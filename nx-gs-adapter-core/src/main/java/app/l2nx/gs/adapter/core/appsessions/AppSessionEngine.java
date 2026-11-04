package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResult;
import app.l2nx.gs.adapter.api.spi.capability.NxAppRules;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionCheckOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.session.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSessionPlacement;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import app.l2nx.gs.adapter.core.appsessions.OfflineSessionStore.EndCursor;
import app.l2nx.gs.adapter.core.appsessions.OfflineSessionStore.UnpublishedEnds;
import app.l2nx.gs.adapter.core.events.SessionResultJson;
import app.l2nx.gs.adapter.core.events.UndeliverableEventException;
import app.l2nx.gs.commons.UUIDv7;
import app.l2nx.gs.commons.concurrent.DaemonThreadFactory;
import app.l2nx.gs.commons.concurrent.SafeRunnable;
import app.l2nx.gs.commons.identifiers.ClientIdentifiers;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A failed close or rollback write stays in {@link #pendingWrites}, the character busy, until a retry lands. If the
 * JVM stops first the row stays open and is restored: the lesser harm next to a character lost from the world.
 */
final class AppSessionEngine {

    private static final NxLog log = NxLogFactory.getLogger(AppSessionEngine.class);

    static final long FLUSH_INTERVAL_MS = 60_000L;
    static final long LOGIN_CLOSE_TIMEOUT_MS = 15_000L;
    static final long SHUTDOWN_FLUSH_TIMEOUT_MS = 5_000L;
    static final int RESTORE_ATTEMPTS = 5;
    static final int LAST_STARTED_CANDIDATES = 4;
    static final int LAST_STARTED_ROUNDS = 3;
    static final int REPUBLISH_BATCH = 500;
    static final int MARK_BATCH = 500;

    static final Set<LogoutReason> HOST_END_REASONS = Collections.unmodifiableSet(EnumSet.of(
            LogoutReason.APP_OUT_OF_BAIT,
            LogoutReason.APP_DAILY_LIMIT,
            LogoutReason.APP_INVENTORY_FULL,
            LogoutReason.APP_OVERWEIGHT,
            LogoutReason.APP_SOLD_OUT,
            LogoutReason.APP_INTERRUPTED,
            LogoutReason.BANNED,
            LogoutReason.KICKED));

    /** Marks an answer the host failed to give, apart from the {@code null} of a host with nothing to give. */
    private static final SessionResult HOST_FAILED = SessionResult.builder().build();

    enum Phase {
        STARTING,
        PENDING_RESTORE,
        RUNNING,
        CLOSED
    }

    private final OfflineSessionStore store;
    private final OfflineSessionHost host;
    private final NxAppRules rules;
    private final BooleanSupplier rulesFromFile;
    private final BoundedExecutor work;
    private final ScheduledExecutorService scheduler;
    private final AppSessionClock clock;
    private final PresencePublisher presence = new PresencePublisher();
    private final AppSessionsSnapshotPublisher snapshots;
    private final long loginCloseTimeoutMs;

    private final ConcurrentMap<UUID, Slot> byId = new ConcurrentHashMap<UUID, Slot>();
    private final ConcurrentMap<Integer, Slot> byCharacter = new ConcurrentHashMap<Integer, Slot>();
    private final ConcurrentMap<Integer, PendingWrite> pendingWrites = new ConcurrentHashMap<Integer, PendingWrite>();
    private final AtomicBoolean retrying = new AtomicBoolean();
    /**
     * Ends claimed by one sender: sent and not yet answered by Kafka, or acknowledged and not yet released by
     * {@link #releasePublishedClaims}. A claimed end is never sent again.
     */
    private final Set<UUID> endsInFlight = ConcurrentHashMap.newKeySet();

    private final ConcurrentLinkedQueue<UUID> acknowledgedEnds = new ConcurrentLinkedQueue<UUID>();

    private final AtomicBoolean markScheduled = new AtomicBoolean();
    /** Ends whose acknowledgement the table holds; their claims are released before the next read of the table. */
    private final ConcurrentLinkedQueue<UUID> publishedEnds = new ConcurrentLinkedQueue<UUID>();

    private final AtomicBoolean republishing = new AtomicBoolean();
    /** Closed rows whose end event cannot be built; logged once and skipped from then on. */
    private final Set<UUID> unbuildableEnds = ConcurrentHashMap.newKeySet();
    /** Closed rows whose stored result this version cannot read; logged once, their ends go out without it. */
    private final Set<UUID> unreadableResults = ConcurrentHashMap.newKeySet();
    /** Reads and writes of the {@code result} column that failed; none of them ever ends a session. */
    private final AtomicLong resultColumnFailures = new AtomicLong();

    private final ConcurrentLinkedQueue<Slot> restoreQueue = new ConcurrentLinkedQueue<Slot>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean restoreBatchScheduled = new AtomicBoolean();
    private final AtomicBoolean hostReady = new AtomicBoolean();
    private final AtomicBoolean restoreBegun = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    /** Moves before a start leaves STARTING or its rolled-back row leaves the table, see {@link #lastReallyStarted}. */
    private final AtomicLong settledStarts = new AtomicLong();

    AppSessionEngine(
            OfflineSessionStore store,
            OfflineSessionHost host,
            NxAppRules rules,
            BooleanSupplier rulesFromFile,
            Executor io,
            int ioLimit,
            ScheduledExecutorService scheduler,
            AppSessionClock clock,
            long loginCloseTimeoutMs) {
        this.store = store;
        this.host = host;
        this.rules = rules;
        this.rulesFromFile = rulesFromFile;
        this.work = new BoundedExecutor(io, ioLimit);
        this.scheduler = scheduler;
        this.clock = clock;
        this.loginCloseTimeoutMs = loginCloseTimeoutMs;
        this.snapshots = new AppSessionsSnapshotPublisher(this::openSessionIds, clock);
    }

    /**
     * Throws when the table cannot be read. History cleanup runs only with the file's retention: defaults standing in
     * for a rejected file may be far shorter than intended.
     */
    void boot() {
        if (rulesFromFile.getAsBoolean()) {
            int retentionDays = rules.current().historyRetentionDays();
            int deleted = store.deleteClosedBefore(clock.now().minus(retentionDays, ChronoUnit.DAYS));
            log.info("App sessions: deleted {} closed session(s) older than {} day(s)", deleted, retentionDays);
        } else {
            log.warn("App sessions: no closed session deleted at this start, the app settings file was not applied and"
                    + " its app.history.retention-days is unknown");
        }
        List<OfflineSession> open = store.loadOpen();
        for (OfflineSession row : open) {
            Slot slot = new Slot(row, Phase.PENDING_RESTORE, new SerialExecutor(work));
            byId.put(row.getId(), slot);
            byCharacter.put(row.getCharacterId(), slot);
        }
        log.info("App sessions: {} open session(s) wait for their restore", open.size());
    }

    void bindEvents(NxEvents events) {
        presence.bind(events);
        snapshots.bind(events);
    }

    void unbindEvents() {
        presence.unbind();
        snapshots.unbind();
    }

    void startScheduling() {
        if (stopped.get() || !scheduled.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(
                SafeRunnable.wrap(this::flushTick, log), FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        snapshots.start(scheduler);
        beginRestoreIfReady();
    }

    void hostReady() {
        if (hostReady.compareAndSet(false, true)) {
            log.info("App sessions: the host reported its world loaded");
            beginRestoreIfReady();
        }
    }

    /**
     * Both {@link #startScheduling} and {@link #hostReady} set their flag before calling this, so whichever comes last
     * sees both; {@link #restoreBegun} keeps a race between them to one restore.
     */
    private void beginRestoreIfReady() {
        if (stopped.get() || !scheduled.get() || !hostReady.get() || !restoreBegun.compareAndSet(false, true)) {
            return;
        }
        List<Slot> pending = new ArrayList<Slot>();
        for (Slot slot : byId.values()) {
            if (slot.phase == Phase.PENDING_RESTORE) {
                pending.add(slot);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        pending.sort(Comparator.comparing((Slot s) -> s.row.getStartedAt()).thenComparing(s -> s.id));
        restoreQueue.addAll(pending);
        AppRules current = rules.current();
        log.info(
                "App sessions: restoring {} session(s), {} every {} ms",
                pending.size(),
                Math.min(current.restoreBatchSize(), work.limit()),
                current.restoreBatchPauseMillis());
        scheduleRestoreBatch(0L);
    }

    private void scheduleRestoreBatch(long delayMs) {
        if (restoreBatchScheduled.compareAndSet(false, true) && schedule(this::restoreBatch, delayMs) == null) {
            restoreBatchScheduled.set(false);
        }
    }

    void shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
        Map<UUID, Long> snapshot = activeSnapshot();
        CompletableFuture<Void> flushed = flushAtShutdown(snapshot);
        Map<UUID, String> blocks = new ConcurrentHashMap<UUID, String>();
        AtomicBoolean collecting = new AtomicBoolean(true);
        List<CompletableFuture<Void>> taken = new ArrayList<CompletableFuture<Void>>();
        for (Slot slot : byId.values()) {
            slot.cancelEndTimer();
            CompletableFuture<Void> done = new CompletableFuture<Void>();
            taken.add(done);
            slot.ops.execute(() -> {
                try {
                    if (collecting.get()) {
                        collectRunningResult(slot, blocks);
                    }
                } finally {
                    done.complete(null);
                }
            });
        }
        long budgetMs = SHUTDOWN_FLUSH_TIMEOUT_MS
                + (long) taken.size() * rules.current().shutdownResultMillisPerSession();
        await(CompletableFuture.allOf(taken.toArray(new CompletableFuture<?>[0])), budgetMs, "taking running results");
        collecting.set(false);
        writeRunningResults(blocks, budgetMs);
        await(flushed, SHUTDOWN_FLUSH_TIMEOUT_MS, "storing active_ms");
    }

    private CompletableFuture<Void> flushAtShutdown(Map<UUID, Long> snapshot) {
        CompletableFuture<Void> flushed = new CompletableFuture<Void>();
        if (snapshot.isEmpty() && pendingWrites.isEmpty() && acknowledgedEnds.isEmpty()) {
            flushed.complete(null);
            return flushed;
        }
        work.execute(() -> {
            try {
                markAcknowledgedEnds();
                if (!snapshot.isEmpty()) {
                    store.flushActiveMs(snapshot);
                    log.info("App sessions: active_ms of {} running session(s) stored at shutdown", snapshot.size());
                }
                flushed.complete(null);
            } catch (Throwable t) {
                flushed.completeExceptionally(t);
            } finally {
                retryPendingWrites();
            }
        });
        return flushed;
    }

    /**
     * Queued on every open session, not only the running ones: a restore in flight settles first and may leave one
     * running. No block in the column reads as a crash at the next restore, which is what a block the host failed to
     * give is; a host with nothing to give leaves an empty block, because nothing was lost.
     */
    private void collectRunningResult(Slot slot, Map<UUID, String> blocks) {
        if (slot.phase != Phase.RUNNING) {
            return;
        }
        OfflineSession row = slot.view(clock.monotonicMs());
        SessionResult answer = askHost(row);
        slot.resultKept = true;
        if (answer == HOST_FAILED) {
            return;
        }
        SessionResult block = answer != null ? answer : SessionResult.builder().build();
        String json = blockJson(slot, block, row.getId());
        slot.keptResultJson = answer != null ? json : null;
        if (json != null) {
            blocks.put(slot.id, json);
        }
    }

    /**
     * One batch, on a thread of its own: the pool may be held by slots stuck in the host, and those must not cost the
     * other sessions their blocks.
     */
    private void writeRunningResults(Map<UUID, String> blocks, long budgetMs) {
        if (blocks.isEmpty()) {
            return;
        }
        Map<UUID, String> batch = new LinkedHashMap<UUID, String>(blocks);
        CompletableFuture<Void> written = new CompletableFuture<Void>();
        DaemonThreadFactory.named("nx-app-sessions-stop", log)
                .newThread(() -> {
                    try {
                        store.writeOpenResults(batch);
                        written.complete(null);
                    } catch (Throwable t) {
                        resultColumnFailures.incrementAndGet();
                        written.completeExceptionally(t);
                    }
                })
                .start();
        if (await(written, budgetMs, "storing running results")) {
            log.info("App sessions: result of {} running session(s) stored at shutdown", batch.size());
        }
    }

    /** {@code false} when the wait ran out or the work failed: what missed its write reads as a crash at the restore. */
    private static boolean await(CompletableFuture<Void> work, long timeoutMs, String what) {
        try {
            work.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.error("App sessions: {} at shutdown failed: {}", what, String.valueOf(e.getMessage()), e);
        }
        return false;
    }

    CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request) {
        if (stopped.get()) {
            return CompletableFuture.completedFuture(AppSessionStartOutcome.unavailable("app sessions are stopping"));
        }
        Slot existing = byCharacter.get(request.getCharacterId());
        if (existing != null) {
            return answerExisting(existing, request);
        }
        PendingWrite pending = pendingWrites.get(request.getCharacterId());
        if (pending != null) {
            return CompletableFuture.completedFuture(answerPending(pending, request));
        }
        Instant now = nowSeconds();
        // fixed at the start, so a later change of the file never moves the return rule of a running session
        long returnThresholdMs = TimeUnit.MINUTES.toMillis(rules.current().returnThresholdMinutes());
        OfflineSession row = OfflineSession.builder()
                .id(UUIDv7.generate(now))
                .idempotencyKey(request.getIdempotencyKey())
                .characterId(request.getCharacterId())
                .activity(request.getActivity())
                .accountName(request.getAccountName())
                .masterAccountId(request.getMasterAccountId())
                .hwid(ClientIdentifiers.hwid(request.getHwid()))
                .initiatorIp(ClientIdentifiers.ip(request.getInitiatorIp()))
                .spotPool(request.getSpotPool())
                .spotId(request.getSpotId())
                .originX(request.getOriginX())
                .originY(request.getOriginY())
                .originZ(request.getOriginZ())
                .originHeading(request.getOriginHeading())
                .startedAt(now)
                .plannedMs(request.getPlannedMs())
                .activeMs(0L)
                .returnThresholdMs(returnThresholdMs)
                .typeState(request.getTypeState())
                .build();
        Slot slot = new Slot(row, Phase.STARTING, new SerialExecutor(work));
        CompletableFuture<AppSessionStartOutcome> result = new CompletableFuture<AppSessionStartOutcome>();
        slot.startOutcome = result;
        Slot raced = byCharacter.putIfAbsent(row.getCharacterId(), slot);
        if (raced != null) {
            return answerExisting(raced, request);
        }
        // a close failing in between registers its pending write before it frees the character
        PendingWrite closedMeanwhile = pendingWrites.get(row.getCharacterId());
        if (closedMeanwhile != null) {
            byCharacter.remove(row.getCharacterId(), slot);
            return CompletableFuture.completedFuture(answerPending(closedMeanwhile, request));
        }
        byId.put(row.getId(), slot);
        slot.ops.execute(() -> result.complete(doStart(slot)));
        return result;
    }

    /**
     * Mirrors {@link #start} up to its first write. Without an idempotency key every open session of the character is busy, never {@code EXISTING}.
     */
    CompletableFuture<AppSessionCheckOutcome> check(int characterId, String spotPool, int spotId) {
        if (stopped.get()) {
            return CompletableFuture.completedFuture(AppSessionCheckOutcome.unavailable("app sessions are stopping"));
        }
        Slot existing = byCharacter.get(characterId);
        if (existing != null) {
            return CompletableFuture.completedFuture(
                    AppSessionCheckOutcome.characterBusy(existing.view(clock.monotonicMs())));
        }
        PendingWrite pending = pendingWrites.get(characterId);
        if (pending != null) {
            return CompletableFuture.completedFuture(AppSessionCheckOutcome.characterBusy(pending.session));
        }
        CompletableFuture<AppSessionCheckOutcome> result = new CompletableFuture<AppSessionCheckOutcome>();
        work.execute(() -> result.complete(probeTable(characterId, spotPool, spotId)));
        return result;
    }

    private AppSessionCheckOutcome probeTable(int characterId, String spotPool, int spotId) {
        try {
            switch (store.probeInsert(characterId, spotPool, spotId)) {
                case CHARACTER_ACTIVE:
                    return characterActive(
                            characterId, AppSessionCheckOutcome::characterBusy, AppSessionCheckOutcome::unavailable);
                case SPOT_TAKEN:
                    return AppSessionCheckOutcome.spotTaken();
                default:
                    return AppSessionCheckOutcome.allowed();
            }
        } catch (RuntimeException e) {
            log.error("App sessions: check of character {} failed in the database: {}", characterId, e.getMessage(), e);
            return AppSessionCheckOutcome.unavailable("database: " + e.getMessage());
        }
    }

    CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity) {
        Slot slot = byCharacter.get(characterId);
        if (slot == null || slot.row.getActivity() != activity) {
            return CompletableFuture.completedFuture(Optional.<OfflineSession>empty());
        }
        CompletableFuture<Optional<OfflineSession>> result = new CompletableFuture<Optional<OfflineSession>>();
        slot.ops.execute(() -> result.complete(close(
                slot,
                slot.phase == Phase.PENDING_RESTORE
                        ? LogoutReason.APP_STOPPED_BEFORE_RESTORE
                        : LogoutReason.APP_STOPPED)));
        return result;
    }

    void onActionEnded(UUID sessionId, LogoutReason reason) {
        if (!HOST_END_REASONS.contains(reason)) {
            log.error(
                    "App sessions: onActionEnded({}, {}) ignored, a host reports only {}",
                    sessionId,
                    reason,
                    HOST_END_REASONS);
            return;
        }
        Slot slot = byId.get(sessionId);
        if (slot == null) {
            log.warn("App sessions: onActionEnded({}, {}) for a session that is not open", sessionId, reason);
            return;
        }
        slot.ops.execute(() -> {
            if (slot.phase == Phase.RUNNING) {
                close(slot, reason);
            } else {
                log.warn(
                        "App sessions: onActionEnded({}, {}) ignored, the session is {}",
                        sessionId,
                        reason,
                        slot.phase);
            }
        });
    }

    ClientLoginOutcome onClientLogin(int characterId) {
        Slot slot = byCharacter.get(characterId);
        if (slot == null) {
            // a session whose close is only pending in the table has already left the world
            return ClientLoginOutcome.none();
        }
        CompletableFuture<Optional<OfflineSession>> closed = new CompletableFuture<Optional<OfflineSession>>();
        slot.ops.execute(() -> {
            try {
                closed.complete(close(slot, LogoutReason.APP_LOGIN));
            } catch (Throwable t) {
                closed.completeExceptionally(t);
            }
        });
        try {
            Optional<OfflineSession> session = closed.get(loginCloseTimeoutMs, TimeUnit.MILLISECONDS);
            return session.isPresent() ? ClientLoginOutcome.closed(session.get()) : ClientLoginOutcome.none();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                    "App sessions: login of character {} interrupted while session {} closes; the host refuses this"
                            + " login and the close completes in the background",
                    characterId,
                    slot.id);
        } catch (ExecutionException | TimeoutException e) {
            log.error(
                    "App sessions: closing session {} of character {} on login did not finish within {} ms;"
                            + " the host refuses this login and the close completes in the background",
                    slot.id,
                    characterId,
                    loginCloseTimeoutMs,
                    e);
        }
        return ClientLoginOutcome.pending();
    }

    /** The character is played with a client by now, which the host's {@code removeFromWorld} leaves untouched. */
    void closeAfterLateLogin(int characterId) {
        Slot slot = byCharacter.get(characterId);
        if (slot != null) {
            slot.ops.execute(() -> close(slot, LogoutReason.APP_LOGIN));
        }
    }

    Optional<OfflineSession> findActive(int characterId) {
        Slot slot = byCharacter.get(characterId);
        if (slot != null && slot.phase != Phase.CLOSED) {
            return Optional.of(slot.view(clock.monotonicMs()));
        }
        PendingWrite pending = pendingWrites.get(characterId);
        return pending == null ? Optional.<OfflineSession>empty() : Optional.of(pending.session);
    }

    List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity) {
        return findOpen(row -> matches(row, masterAccountId, activity));
    }

    List<OfflineSession> findOpen(ActivityType activity) {
        return findOpen(row -> row.getActivity() == activity);
    }

    private List<OfflineSession> findOpen(Predicate<OfflineSession> subject) {
        long now = clock.monotonicMs();
        Map<UUID, OfflineSession> found = new LinkedHashMap<UUID, OfflineSession>();
        for (Slot slot : byId.values()) {
            if (slot.phase != Phase.CLOSED && subject.test(slot.row)) {
                found.put(slot.id, slot.view(now));
            }
        }
        // an end or a rolled-back start the table has not taken yet keeps the character busy until its retry lands
        for (PendingWrite pending : pendingWrites.values()) {
            if (subject.test(pending.session)) {
                found.putIfAbsent(pending.session.getId(), pending.session);
            }
        }
        List<OfflineSession> result = new ArrayList<OfflineSession>(found.values());
        result.sort(Comparator.comparing(OfflineSession::getStartedAt).thenComparing(OfflineSession::getId));
        return result;
    }

    CompletableFuture<Optional<OfflineSession>> findLastStartedByMasterAccount(
            UUID masterAccountId, ActivityType activity) {
        return readLastStarted(
                limit -> store.findLatestStartedByMasterAccount(masterAccountId, activity, limit),
                row -> matches(row, masterAccountId, activity));
    }

    CompletableFuture<Optional<OfflineSession>> findLastStartedByCharacter(int characterId, ActivityType activity) {
        return readLastStarted(
                limit -> store.findLatestStartedByCharacter(characterId, activity, limit),
                row -> row.getActivity() == activity && row.getCharacterId() == characterId);
    }

    private CompletableFuture<Optional<OfflineSession>> readLastStarted(
            IntFunction<List<OfflineSession>> latest, Predicate<OfflineSession> subject) {
        CompletableFuture<Optional<OfflineSession>> result = new CompletableFuture<Optional<OfflineSession>>();
        work.execute(() -> {
            try {
                result.complete(lastReallyStarted(latest, subject));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    /**
     * In-flight and delete-refused rolled-back starts have rows but never really started; they are filtered after the
     * query, safe because a start registers its slot before writing its row. A start settling mid-query repeats the
     * read, at most {@value #LAST_STARTED_ROUNDS} times so an IO worker is never held forever; the last read answers.
     */
    private Optional<OfflineSession> lastReallyStarted(
            IntFunction<List<OfflineSession>> latest, Predicate<OfflineSession> subject) {
        int limit = LAST_STARTED_CANDIDATES;
        for (int round = 1; ; round++) {
            long settledBefore = settledStarts.get();
            List<OfflineSession> candidates = latest.apply(limit);
            Set<UUID> notStarted = notReallyStarted(subject);
            boolean settled = settledStarts.get() != settledBefore;
            Optional<OfflineSession> first = firstReallyStarted(candidates, notStarted);
            boolean complete = first.isPresent() || candidates.size() < limit;
            if ((complete && !settled) || round == LAST_STARTED_ROUNDS) {
                return first;
            }
            if (!complete) {
                // a page one row longer than every start not really started holds a real one, if the table has any
                limit = Math.max(limit, notStarted.size() + 1);
            }
        }
    }

    private static Optional<OfflineSession> firstReallyStarted(List<OfflineSession> candidates, Set<UUID> notStarted) {
        for (OfflineSession row : candidates) {
            if (!notStarted.contains(row.getId())) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    private Set<UUID> notReallyStarted(Predicate<OfflineSession> subject) {
        Set<UUID> notStarted = new HashSet<UUID>();
        for (Slot slot : byId.values()) {
            if (slot.phase == Phase.STARTING && subject.test(slot.row)) {
                notStarted.add(slot.id);
            }
        }
        for (PendingWrite pending : pendingWrites.values()) {
            if (pending.delete && subject.test(pending.session)) {
                notStarted.add(pending.session.getId());
            }
        }
        return notStarted;
    }

    private static boolean matches(OfflineSession row, UUID masterAccountId, ActivityType activity) {
        return row.getActivity() == activity && masterAccountId.equals(row.getMasterAccountId());
    }

    CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeState) {
        Slot slot = byId.get(sessionId);
        if (slot == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        String value = typeState == null || typeState.trim().isEmpty() ? null : typeState;
        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
        Runnable write = () -> {
            if (slot.phase == Phase.CLOSED) {
                result.complete(Boolean.FALSE);
                return;
            }
            try {
                boolean stored = store.updateTypeState(slot.id, value);
                if (stored) {
                    slot.row = slot.row.toBuilder().typeState(value).build();
                }
                result.complete(stored);
            } catch (RuntimeException e) {
                log.error("App sessions: storing type_state of {} failed: {}", slot.id, e.getMessage(), e);
                result.complete(Boolean.FALSE);
            }
        };
        if (slot.ops.runsOnCurrentThread()) {
            write.run();
        } else {
            slot.ops.execute(write);
        }
        return result;
    }

    private CompletableFuture<AppSessionStartOutcome> answerExisting(Slot slot, AppSessionStartRequest request) {
        if (slot.row.getIdempotencyKey().equals(request.getIdempotencyKey())) {
            CompletableFuture<AppSessionStartOutcome> first = slot.startOutcome;
            if (first != null) {
                return first.thenApply(outcome -> outcome.getStatus() == AppSessionStartOutcome.Status.STARTED
                        ? AppSessionStartOutcome.existing(outcome.getSession())
                        : outcome);
            }
            return CompletableFuture.completedFuture(AppSessionStartOutcome.existing(slot.view(clock.monotonicMs())));
        }
        return CompletableFuture.completedFuture(AppSessionStartOutcome.characterBusy(slot.view(clock.monotonicMs())));
    }

    private AppSessionStartOutcome answerPending(PendingWrite pending, AppSessionStartRequest request) {
        if (!pending.session.getIdempotencyKey().equals(request.getIdempotencyKey())) {
            return AppSessionStartOutcome.characterBusy(pending.session);
        }
        return pending.delete
                ? AppSessionStartOutcome.unavailable(
                        "the earlier start with this idempotency key is still rolling back")
                : AppSessionStartOutcome.existing(pending.session);
    }

    private AppSessionStartOutcome doStart(Slot slot) {
        OfflineSession row = slot.row;
        try {
            Optional<OfflineSession> earlier = store.findByIdempotencyKey(row.getIdempotencyKey());
            if (earlier.isPresent()) {
                forget(slot);
                return AppSessionStartOutcome.existing(earlier.get());
            }
            OfflineSessionStore.InsertResult inserted = store.insert(row);
            if (inserted == OfflineSessionStore.InsertResult.SPOT_TAKEN) {
                // classified after the fact: the holder of the spot may have closed in between
                inserted = store.insert(row);
            }
            switch (inserted) {
                case INSERTED:
                    slot.stored = true;
                    break;
                case DUPLICATE_IDEMPOTENCY_KEY:
                    forget(slot);
                    Optional<OfflineSession> raced = store.findByIdempotencyKey(row.getIdempotencyKey());
                    return raced.isPresent()
                            ? AppSessionStartOutcome.existing(raced.get())
                            : AppSessionStartOutcome.unavailable("idempotency key conflict without a stored session");
                case CHARACTER_ACTIVE:
                    forget(slot);
                    return characterActive(
                            row.getCharacterId(),
                            AppSessionStartOutcome::characterBusy,
                            AppSessionStartOutcome::unavailable);
                case SPOT_TAKEN:
                default:
                    forget(slot);
                    return AppSessionStartOutcome.spotTaken();
            }
        } catch (RuntimeException e) {
            forget(slot);
            log.error(
                    "App sessions: start of character {} failed in the database: {}",
                    row.getCharacterId(),
                    e.getMessage(),
                    e);
            return AppSessionStartOutcome.unavailable("database: " + e.getMessage());
        }

        OfflineSessionPlacement placement = placeQuietly(row);
        if (placement == null || !placement.isPlaced()) {
            rollBack(slot, row, false);
            return placement == null
                    ? AppSessionStartOutcome.unavailable("placeInWorld threw")
                    : AppSessionStartOutcome.placementRefused(placement.getRefusal(), placement.getDetail());
        }
        slot.placed = true;
        slot.carriedRead = true;
        // re-read: an inline updateTypeState from inside placeInWorld changed it
        row = slot.row;
        Instant startedAt = nowSeconds();
        OfflineSession placed = row.toBuilder()
                .spotId(placement.getSpotId())
                .startedAt(startedAt)
                .typeState(placement.getTypeState() != null ? placement.getTypeState() : row.getTypeState())
                .build();
        OfflineSessionStore.PlaceResult stored;
        try {
            stored = store.place(
                    placed.getId(), placed.getSpotPool(), placed.getSpotId(), startedAt, placement.getTypeState());
        } catch (RuntimeException e) {
            rollBack(slot, placed, true);
            return AppSessionStartOutcome.unavailable("database: " + e.getMessage());
        }
        if (stored != OfflineSessionStore.PlaceResult.PLACED) {
            rollBack(slot, placed, true);
            return stored == OfflineSessionStore.PlaceResult.SPOT_TAKEN
                    ? AppSessionStartOutcome.spotTaken()
                    : AppSessionStartOutcome.unavailable("the session row is no longer open");
        }
        slot.row = placed;
        try {
            host.startAction(placed);
        } catch (Throwable t) {
            log.error(
                    "App sessions: startAction of session {} threw {}",
                    placed.getId(),
                    t.getClass().getName(),
                    t);
            rollBack(slot, placed, true);
            return AppSessionStartOutcome.unavailable(
                    "startAction threw " + t.getClass().getName());
        }
        run(slot);
        OfflineSession view = slot.view(clock.monotonicMs());
        presence.online(view, false);
        log.info("App sessions: started {}", view);
        return AppSessionStartOutcome.started(view);
    }

    private <T> T characterActive(int characterId, Function<OfflineSession, T> busy, Function<String, T> unavailable) {
        Optional<OfflineSession> holder = store.findOpenByCharacterId(characterId);
        return holder.isPresent()
                ? busy.apply(holder.get())
                : unavailable.apply("character session conflict without a stored session");
    }

    private void rollBack(Slot slot, OfflineSession row, boolean placed) {
        if (placed) {
            removeQuietly(row, true);
            // a start that rolls back has no end to carry it, but the host still parked a recorder
            askHost(row);
        }
        try {
            store.delete(row.getId());
        } catch (RuntimeException e) {
            log.error(
                    "App sessions: rolling back the start of session {} failed, retried until the table takes it: {}",
                    row.getId(),
                    e.getMessage(),
                    e);
            pendingWrites.put(row.getCharacterId(), PendingWrite.delete(row));
        }
        forget(slot);
    }

    private void restoreBatch() {
        restoreBatchScheduled.set(false);
        if (stopped.get()) {
            return;
        }
        if (!pendingWrites.isEmpty()) {
            work.execute(this::retryPendingWrites);
        }
        AppRules current = rules.current();
        int batchSize = Math.min(current.restoreBatchSize(), work.limit());
        int submitted = 0;
        // a restore refused for now queues itself again and must wait for the next batch, not run in this one
        int queued = restoreQueue.size();
        Slot next;
        while (submitted < batchSize && queued-- > 0 && (next = restoreQueue.poll()) != null) {
            Slot slot = next;
            if (slot.phase != Phase.PENDING_RESTORE) {
                continue;
            }
            slot.ops.execute(() -> restore(slot));
            submitted++;
        }
        if (!restoreQueue.isEmpty()) {
            scheduleRestoreBatch(current.restoreBatchPauseMillis());
        }
    }

    private void restore(Slot slot) {
        if (slot.phase != Phase.PENDING_RESTORE || stopped.get()) {
            return;
        }
        if (pendingWrites.containsKey(slot.row.getCharacterId())) {
            log.error(
                    "App sessions: restore of session {} skipped, character {} has an end not yet in the table",
                    slot.id,
                    slot.row.getCharacterId());
            return;
        }
        OfflineSession row = slot.row;
        Long plannedMs = row.getPlannedMs();
        if (plannedMs != null && slot.activeMs(clock.monotonicMs()) >= plannedMs) {
            close(slot, LogoutReason.APP_TIME_UP);
            return;
        }
        if (!slot.carriedRead) {
            readCarriedResult(slot);
        }
        OfflineSessionPlacement placement = placeQuietly(carrying(row, slot));
        if (placement != null
                && !placement.isPlaced()
                && placement.isTransientRefusal()
                && ++slot.restoreAttempts < RESTORE_ATTEMPTS) {
            log.info(
                    "App sessions: restore of session {} refused for now ({}), attempt {} of {}, tried again later",
                    row.getId(),
                    placement,
                    slot.restoreAttempts,
                    RESTORE_ATTEMPTS);
            restoreLater(slot);
            return;
        }
        if (placement == null || !placement.isPlaced()) {
            log.warn(
                    "App sessions: restore of session {} refused by the host: {}",
                    row.getId(),
                    placement == null ? "placeInWorld threw" : placement);
            close(slot, LogoutReason.APP_RESTORE_FAILED);
            return;
        }
        slot.placed = true;
        row = slot.row;
        String typeState = placement.getTypeState();
        boolean typeStateChanged = typeState != null && !typeState.equals(row.getTypeState());
        if (placement.getSpotId() != row.getSpotId() || typeStateChanged) {
            OfflineSession moved = row.toBuilder()
                    .spotId(placement.getSpotId())
                    .typeState(typeStateChanged ? typeState : row.getTypeState())
                    .build();
            slot.row = moved;
            OfflineSessionStore.PlaceResult stored;
            try {
                stored = store.place(
                        moved.getId(),
                        moved.getSpotPool(),
                        moved.getSpotId(),
                        null,
                        typeStateChanged ? typeState : null);
            } catch (RuntimeException e) {
                log.error(
                        "App sessions: storing the placement of session {} failed: {}",
                        moved.getId(),
                        e.getMessage(),
                        e);
                stored = OfflineSessionStore.PlaceResult.NOT_OPEN;
            }
            if (stored != OfflineSessionStore.PlaceResult.PLACED) {
                close(slot, LogoutReason.APP_RESTORE_FAILED);
                return;
            }
            row = moved;
        }
        clearCarriedColumn(slot);
        try {
            host.startAction(carrying(row, slot));
        } catch (Throwable t) {
            log.error(
                    "App sessions: startAction on restore of {} threw {}",
                    row.getId(),
                    t.getClass().getName(),
                    t);
            close(slot, LogoutReason.APP_RESTORE_FAILED);
            return;
        }
        slot.carried = null;
        slot.carriedJson = null;
        run(slot);
        OfflineSession view = slot.view(clock.monotonicMs());
        presence.online(view, true);
        log.info("App sessions: restored {}", view);
    }

    private void restoreLater(Slot slot) {
        restoreQueue.add(slot);
        scheduleRestoreBatch(rules.current().restoreBatchPauseMillis());
    }

    /**
     * Read once per session; the block stays in the column until the character is placed, so a stop between two
     * attempts loses nothing. A failed read costs the block, never the session: the end says {@code partial}.
     */
    private void readCarriedResult(Slot slot) {
        slot.carriedRead = true;
        String json;
        try {
            json = store.readResult(slot.id);
        } catch (RuntimeException e) {
            log.error(
                    "App sessions: reading the carried result of {} failed, read as lost ({} column failure(s)): {}",
                    slot.id,
                    resultColumnFailures.incrementAndGet(),
                    e.getMessage(),
                    e);
            slot.carriedInColumn = true;
            slot.partial = true;
            return;
        }
        slot.carriedInColumn = json != null;
        slot.carriedJson = json;
        if (json != null) {
            try {
                slot.carried = SessionResultJson.parse(json);
            } catch (RuntimeException e) {
                if (unreadableResults.add(slot.id)) {
                    log.error("App sessions: the carried result of {} cannot be read: {}", slot.id, e.getMessage(), e);
                }
            }
        }
        SessionResult carried = slot.carried;
        slot.partial = carried == null || carried.isPartial();
    }

    /**
     * A block left in an open row makes a crash after this restore look like a graceful stop. That stale block is all a
     * failed clear risks, so it never costs the session.
     */
    private void clearCarriedColumn(Slot slot) {
        if (!slot.carriedInColumn) {
            return;
        }
        try {
            store.writeOpenResults(Collections.<UUID, String>singletonMap(slot.id, null));
            slot.carriedInColumn = false;
        } catch (RuntimeException e) {
            log.error(
                    "App sessions: clearing the carried result of {} failed ({} column failure(s)): {}",
                    slot.id,
                    resultColumnFailures.incrementAndGet(),
                    e.getMessage(),
                    e);
        }
    }

    private static OfflineSession carrying(OfflineSession row, Slot slot) {
        return slot.carried == null
                ? row
                : row.toBuilder().carriedResult(slot.carried).build();
    }

    /** Runs on the session's executor; empty when the session was not open. */
    private Optional<OfflineSession> close(Slot slot, LogoutReason reason) {
        Phase phase = slot.phase;
        if (phase == Phase.CLOSED || phase == Phase.STARTING) {
            return Optional.empty();
        }
        slot.cancelEndTimer();
        long activeMs = slot.stopCounting(clock.monotonicMs());
        OfflineSession row = slot.row.toBuilder().activeMs(activeMs).build();
        if (phase == Phase.RUNNING) {
            try {
                host.stopAction(row);
            } catch (Throwable t) {
                log.error(
                        "App sessions: stopAction of {} threw {}",
                        row.getId(),
                        t.getClass().getName(),
                        t);
            }
        }
        removeQuietly(row, activeMs < returnThresholdMs(row));
        String resultJson = slot.resultKept ? slot.keptResultJson : endResultJson(slot, row);
        Instant endedAt = nowSeconds();
        OfflineSession closed =
                row.toBuilder().endedAt(endedAt).endReason(reason).build();
        // before the row commits as closed, so a flush tick reading it finds the end claimed and does not send it too
        boolean claimed = endsInFlight.add(closed.getId());
        try {
            store.close(row.getId(), activeMs, endedAt, reason, resultJson);
        } catch (RuntimeException e) {
            log.error(
                    "App sessions: closing session {} with {} failed in the database, retried until the table takes"
                            + " it: {}",
                    row.getId(),
                    reason,
                    e.getMessage(),
                    e);
            // before forget() frees the character, so a start in between sees it busy
            pendingWrites.put(row.getCharacterId(), PendingWrite.close(closed, resultJson));
        }
        slot.row = closed;
        forget(slot);
        if (claimed) {
            sendEnd(closed, resultJson);
        } else {
            log.warn("App sessions: the end of session {} is already being sent, not sent again", closed.getId());
        }
        log.info("App sessions: ended {}", closed);
        return Optional.of(closed);
    }

    /**
     * The text the row and every send of the end share. The host is asked after every {@code removeFromWorld}, placed or
     * not, because it parks the recorder there. Without a placement in this JVM its answer is dropped and the block the
     * last stop carried stands; the phase cannot tell, a restore that failed after its placement closes from the same
     * path. The same block stands in for a host that has nothing because it seeds in a {@code startAction} that threw.
     */
    private @Nullable String endResultJson(Slot slot, OfflineSession row) {
        SessionResult answer = askHost(row);
        if (!slot.placed) {
            return carriedJson(slot);
        }
        if (answer == HOST_FAILED || answer == null || !hasRows(answer)) {
            String carried = carriedJson(slot);
            if (carried != null) {
                return carried;
            }
        }
        return answer == HOST_FAILED || answer == null ? null : blockJson(slot, answer, row.getId());
    }

    /** {@link #HOST_FAILED} when the call threw. */
    private @Nullable SessionResult askHost(OfflineSession row) {
        try {
            return host.takeSessionResult(row);
        } catch (Throwable t) {
            log.error(
                    "App sessions: takeSessionResult of {} threw {}",
                    row.getId(),
                    t.getClass().getName(),
                    t);
            return HOST_FAILED;
        }
    }

    private static @Nullable String blockJson(Slot slot, SessionResult block, UUID sessionId) {
        return SessionResultJson.toJson(
                slot.partial ? block.toBuilder().partial(true).build() : block, sessionId);
    }

    /** The stored text, unreadable ones included so the column keeps them; {@code null} for an empty block. */
    private @Nullable String carriedJson(Slot slot) {
        if (!slot.carriedRead) {
            readCarriedResult(slot);
        }
        SessionResult carried = slot.carried;
        return carried != null && !hasRows(carried) ? null : slot.carriedJson;
    }

    private static boolean hasRows(SessionResult block) {
        return block.getOverflow() > 0L
                || !block.getItems().isEmpty()
                || !block.getUniques().isEmpty()
                || !block.getExperience().isEmpty()
                || !block.getKills().isEmpty()
                || !block.getDeaths().isEmpty()
                || !block.getUntracked().isEmpty();
    }

    private void run(Slot slot) {
        long now = clock.monotonicMs();
        if (slot.phase == Phase.STARTING) {
            settledStarts.incrementAndGet();
        }
        slot.phase = Phase.RUNNING;
        slot.startCounting(now);
        armEndTimer(slot, now);
    }

    private void armEndTimer(Slot slot, long now) {
        Long plannedMs = slot.row.getPlannedMs();
        if (plannedMs == null || stopped.get()) {
            return;
        }
        long remaining = Math.max(0L, plannedMs - slot.activeMs(now));
        slot.setEndTimer(schedule(() -> slot.ops.execute(() -> checkTimeUp(slot)), remaining));
    }

    private void checkTimeUp(Slot slot) {
        if (slot.phase != Phase.RUNNING) {
            return;
        }
        long now = clock.monotonicMs();
        Long plannedMs = slot.row.getPlannedMs();
        if (plannedMs != null && slot.activeMs(now) >= plannedMs) {
            close(slot, LogoutReason.APP_TIME_UP);
        } else {
            armEndTimer(slot, now);
        }
    }

    private void flushTick() {
        if (!pendingWrites.isEmpty()) {
            work.execute(this::retryPendingWrites);
        }
        work.execute(this::republishUnacknowledgedEnds);
        Map<UUID, Long> snapshot = activeSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        work.execute(() -> {
            try {
                store.flushActiveMs(snapshot);
            } catch (RuntimeException e) {
                log.warn("App sessions: periodic active_ms flush failed, retried next minute: {}", e.getMessage());
            }
        });
    }

    /**
     * An end whose close is still pending is sent again once its retry lands; the platform dedups by {@code sessionId}
     * and {@code eventId}. Pages by the {@code (ended_at, id)} cursor so unreadable rows never fill the window.
     */
    private void republishUnacknowledgedEnds() {
        if (!republishing.compareAndSet(false, true)) {
            return;
        }
        try {
            releasePublishedClaims();
            int sent = 0;
            EndCursor after = null;
            while (sent < REPUBLISH_BATCH) {
                UnpublishedEnds page;
                try {
                    page = store.findUnpublishedEnds(after, REPUBLISH_BATCH);
                } catch (RuntimeException e) {
                    log.warn(
                            "App sessions: reading unacknowledged session ends failed, retried next minute: {}",
                            e.getMessage());
                    break;
                }
                for (OfflineSession closed : page.rows) {
                    if (sent >= REPUBLISH_BATCH) {
                        break;
                    }
                    UUID id = closed.getId();
                    if (!unbuildableEnds.contains(id) && endsInFlight.add(id) && resendEnd(closed)) {
                        sent++;
                    }
                }
                if (page.exhausted || page.last == null) {
                    break;
                }
                after = page.last;
            }
            if (sent > 0) {
                log.info("App sessions: sent {} unacknowledged session end(s) again", sent);
            }
        } finally {
            republishing.set(false);
        }
    }

    /**
     * Reads the column here, not with the page, so a page of ends holds one block at a time. An end sent without the
     * table's answer would differ from its first send, so a failed read waits for the next tick.
     */
    private boolean resendEnd(OfflineSession closed) {
        String resultJson;
        try {
            resultJson = store.readResult(closed.getId());
        } catch (RuntimeException e) {
            endsInFlight.remove(closed.getId());
            resultColumnFailures.incrementAndGet();
            log.warn(
                    "App sessions: reading the result of session {} failed, its end is sent next minute: {}",
                    closed.getId(),
                    e.getMessage());
            return false;
        }
        return sendEnd(closed, resultJson);
    }

    /** A stored text a later version cannot parse gives {@code null} on every send alike. */
    private @Nullable SessionResult storedResult(UUID sessionId, @Nullable String resultJson) {
        if (resultJson == null) {
            return null;
        }
        try {
            return SessionResultJson.parse(resultJson);
        } catch (RuntimeException e) {
            if (unreadableResults.add(sessionId)) {
                log.error(
                        "App sessions: the stored result of session {} cannot be read, its end goes out without it: {}",
                        sessionId,
                        e.getMessage(),
                        e);
            }
            return null;
        }
    }

    /**
     * Sends an end its caller claimed; {@code false} releases the claim.
     */
    private boolean sendEnd(OfflineSession closed, @Nullable String resultJson) {
        UUID id = closed.getId();
        CharacterPresenceEvent end;
        try {
            end = PresencePublisher.endEvent(closed, storedResult(id, resultJson));
        } catch (RuntimeException e) {
            endsInFlight.remove(id);
            if (unbuildableEnds.add(id)) {
                log.error(
                        "App sessions: the end event of session {} cannot be built, it is never sent: {}",
                        id,
                        e.getMessage(),
                        e);
            }
            return false;
        }
        try {
            presence.sendEnd(end, failure -> onEndAnswered(id, failure));
            return true;
        } catch (RuntimeException e) {
            endsInFlight.remove(id);
            log.error("App sessions: sending the end of session {} threw, sent again later: {}", id, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Called on the Kafka producer thread among others, so the table write hops to the engine's pool. An end the bound
     * facade can never deliver is recorded as published: sending it again would loop forever.
     */
    private void onEndAnswered(UUID sessionId, @Nullable Exception failure) {
        if (failure instanceof UndeliverableEventException) {
            log.warn(
                    "App sessions: the end of session {} cannot be delivered ({}); recorded as published without"
                            + " reaching the platform",
                    sessionId,
                    failure.getMessage());
        } else if (failure != null) {
            endsInFlight.remove(sessionId);
            log.warn(
                    "App sessions: the end of session {} was not acknowledged, sent again by the flush tick: {}",
                    sessionId,
                    failure.getMessage());
            return;
        }
        acknowledgedEnds.add(sessionId);
        if (markScheduled.compareAndSet(false, true)) {
            work.execute(this::markAcknowledgedEnds);
        }
    }

    /** One write per {@value #MARK_BATCH} acknowledged ends; acknowledgements arriving meanwhile join the batch. */
    private void markAcknowledgedEnds() {
        markScheduled.set(false);
        List<UUID> batch = new ArrayList<UUID>();
        while (true) {
            UUID next;
            while (batch.size() < MARK_BATCH && (next = acknowledgedEnds.poll()) != null) {
                batch.add(next);
            }
            if (batch.isEmpty()) {
                return;
            }
            try {
                store.markEndsPublished(batch, nowSeconds());
                publishedEnds.addAll(batch);
            } catch (RuntimeException e) {
                endsInFlight.removeAll(batch);
                log.warn(
                        "App sessions: recording {} acknowledged session end(s) failed, they are sent again: {}",
                        batch.size(),
                        e.getMessage());
            }
            batch.clear();
        }
    }

    /**
     * A claim released here belongs to a row the table already holds as published; one acknowledged during the read
     * of the unpublished ends stays claimed until the next.
     */
    private void releasePublishedClaims() {
        UUID id;
        while ((id = publishedEnds.poll()) != null) {
            endsInFlight.remove(id);
        }
    }

    /**
     * Running, waiting for the restore, or starting with its row written: a start takes its {@code startedAt} after the
     * insert, so a snapshot taken after that start time lists it.
     */
    private List<UUID> openSessionIds() {
        List<UUID> ids = new ArrayList<UUID>();
        for (Slot slot : byId.values()) {
            Phase phase = slot.phase;
            if (phase == Phase.RUNNING || phase == Phase.PENDING_RESTORE || (phase == Phase.STARTING && slot.stored)) {
                ids.add(slot.id);
            }
        }
        return ids;
    }

    void retryPendingWrites() {
        if (pendingWrites.isEmpty() || !retrying.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Map.Entry<Integer, PendingWrite> entry : pendingWrites.entrySet()) {
                PendingWrite pending = entry.getValue();
                OfflineSession session = pending.session;
                String what = pending.delete ? "rollback" : "close";
                try {
                    if (pending.delete) {
                        store.delete(session.getId());
                        settledStarts.incrementAndGet();
                    } else {
                        store.close(
                                session.getId(),
                                session.getActiveMs(),
                                session.getEndedAt(),
                                session.getEndReason(),
                                pending.resultJson);
                    }
                    pendingWrites.remove(entry.getKey(), pending);
                    log.info("App sessions: {} of session {} reached the table on retry", what, session.getId());
                } catch (RuntimeException e) {
                    log.warn(
                            "App sessions: {} of session {} failed again, retried later: {}",
                            what,
                            session.getId(),
                            e.getMessage());
                }
            }
        } finally {
            retrying.set(false);
        }
        if (stopped.get() && !pendingWrites.isEmpty()) {
            log.error(
                    "App sessions: {} session end(s) did not reach the table before shutdown; those rows stay open and"
                            + " are restored at the next start",
                    pendingWrites.size());
        }
    }

    long resultColumnFailures() {
        return resultColumnFailures.get();
    }

    int pendingWriteCount() {
        return pendingWrites.size();
    }

    private Map<UUID, Long> activeSnapshot() {
        long now = clock.monotonicMs();
        Map<UUID, Long> snapshot = new LinkedHashMap<UUID, Long>();
        for (Slot slot : byId.values()) {
            if (slot.phase == Phase.RUNNING) {
                snapshot.put(slot.id, slot.activeMs(now));
            }
        }
        return snapshot;
    }

    private @Nullable OfflineSessionPlacement placeQuietly(OfflineSession row) {
        try {
            OfflineSessionPlacement placement = host.placeInWorld(row);
            if (placement == null) {
                log.error("App sessions: placeInWorld returned null for {}", row.getId());
            }
            return placement;
        } catch (Throwable t) {
            log.error(
                    "App sessions: placeInWorld of {} threw {}",
                    row.getId(),
                    t.getClass().getName(),
                    t);
            return null;
        }
    }

    private void removeQuietly(OfflineSession row, boolean returnToOrigin) {
        try {
            host.removeFromWorld(row, returnToOrigin);
        } catch (Throwable t) {
            log.error(
                    "App sessions: removeFromWorld of {} threw {}",
                    row.getId(),
                    t.getClass().getName(),
                    t);
        }
    }

    /** A row stored before the adapter kept the threshold uses the current one. */
    private long returnThresholdMs(OfflineSession row) {
        Long fixed = row.getReturnThresholdMs();
        return fixed != null ? fixed : TimeUnit.MINUTES.toMillis(rules.current().returnThresholdMinutes());
    }

    private void forget(Slot slot) {
        if (slot.phase == Phase.STARTING) {
            settledStarts.incrementAndGet();
        }
        slot.phase = Phase.CLOSED;
        byId.remove(slot.id, slot);
        byCharacter.remove(slot.row.getCharacterId(), slot);
    }

    private @Nullable ScheduledFuture<?> schedule(Runnable task, long delayMs) {
        try {
            return scheduler.schedule(SafeRunnable.wrap(task, log), delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shuttingDown) {
            return null;
        }
    }

    /** MySQL and MariaDB {@code DATETIME} keep no fraction. */
    private Instant nowSeconds() {
        return clock.now().truncatedTo(ChronoUnit.SECONDS);
    }

    static final class PendingWrite {
        final OfflineSession session;
        final boolean delete;
        /** The text the end already carried, so the retry stores what went out. */
        final @Nullable String resultJson;

        private PendingWrite(OfflineSession session, boolean delete, @Nullable String resultJson) {
            this.session = session;
            this.delete = delete;
            this.resultJson = resultJson;
        }

        static PendingWrite close(OfflineSession closed, @Nullable String resultJson) {
            return new PendingWrite(closed, false, resultJson);
        }

        static PendingWrite delete(OfflineSession row) {
            return new PendingWrite(row, true, null);
        }
    }

    /** {@link #row}, {@link #phase}, {@link #restoreAttempts} and the result state change only on {@link #ops}. */
    static final class Slot {
        final UUID id;
        final SerialExecutor ops;
        volatile OfflineSession row;
        volatile Phase phase;
        volatile @Nullable CompletableFuture<AppSessionStartOutcome> startOutcome;
        volatile boolean stored;

        int restoreAttempts;

        /** {@code placeInWorld} put the character in the world of this JVM, so an end has a result to ask for. */
        boolean placed;
        /** The block of this session lost something to a crash; a later restore finds that in the block it carries. */
        boolean partial;

        boolean carriedRead;
        boolean carriedInColumn;

        @Nullable
        String carriedJson;
        /** Held only until the action started: from there the host's recorder owns it. */
        @Nullable
        SessionResult carried;

        /** A graceful stop took the block already; an end racing it uses this text, the host has nothing left. */
        boolean resultKept;

        @Nullable
        String keptResultJson;

        private long activeBaseMs;
        private long countingSinceMs = -1L;
        private @Nullable ScheduledFuture<?> endTimer;

        Slot(OfflineSession row, Phase phase, SerialExecutor ops) {
            this.id = row.getId();
            this.row = row;
            this.phase = phase;
            this.ops = ops;
            this.activeBaseMs = row.getActiveMs();
        }

        synchronized long activeMs(long nowMs) {
            return countingSinceMs < 0 ? activeBaseMs : activeBaseMs + Math.max(0L, nowMs - countingSinceMs);
        }

        synchronized void startCounting(long nowMs) {
            if (countingSinceMs < 0) {
                countingSinceMs = nowMs;
            }
        }

        synchronized long stopCounting(long nowMs) {
            activeBaseMs = activeMs(nowMs);
            countingSinceMs = -1L;
            return activeBaseMs;
        }

        synchronized void setEndTimer(@Nullable ScheduledFuture<?> timer) {
            endTimer = timer;
        }

        synchronized void cancelEndTimer() {
            if (endTimer != null) {
                endTimer.cancel(false);
                endTimer = null;
            }
        }

        OfflineSession view(long nowMs) {
            return row.toBuilder().activeMs(activeMs(nowMs)).build();
        }
    }
}
