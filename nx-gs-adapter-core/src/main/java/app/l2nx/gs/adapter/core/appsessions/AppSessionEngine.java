package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.OfflineSessionPlacement;
import app.l2nx.gs.adapter.api.spi.model.OfflineSessionSettings;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import app.l2nx.gs.commons.UUIDv7;
import app.l2nx.gs.commons.concurrent.SafeRunnable;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
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
import org.jspecify.annotations.Nullable;

/**
 * A close or rolled-back start whose table write fails stays in {@link #pendingWrites} and is retried until it lands;
 * the character counts as busy meanwhile. If the JVM stops first the row is still open and gets restored, so the
 * platform sees the session resumed: the lesser harm next to a character silently lost from the world.
 */
final class AppSessionEngine {

    private static final NxLog log = NxLogFactory.getLogger(AppSessionEngine.class);

    static final long FLUSH_INTERVAL_MS = 60_000L;
    static final long LOGIN_CLOSE_TIMEOUT_MS = 15_000L;
    static final long SHUTDOWN_FLUSH_TIMEOUT_MS = 5_000L;
    static final int RESTORE_ATTEMPTS = 5;

    static final Set<LogoutReason> HOST_END_REASONS = Collections.unmodifiableSet(EnumSet.of(
            LogoutReason.APP_OUT_OF_BAIT,
            LogoutReason.APP_DAILY_LIMIT,
            LogoutReason.APP_INVENTORY_FULL,
            LogoutReason.APP_SOLD_OUT,
            LogoutReason.BANNED,
            LogoutReason.KICKED));

    enum Phase {
        STARTING,
        PENDING_RESTORE,
        RUNNING,
        CLOSED
    }

    private final OfflineSessionStore store;
    private final OfflineSessionHost host;
    private final BoundedExecutor work;
    private final ScheduledExecutorService scheduler;
    private final AppSessionClock clock;
    private final PresencePublisher presence = new PresencePublisher();
    private final long loginCloseTimeoutMs;

    private final ConcurrentMap<UUID, Slot> byId = new ConcurrentHashMap<UUID, Slot>();
    private final ConcurrentMap<Integer, Slot> byCharacter = new ConcurrentHashMap<Integer, Slot>();
    private final ConcurrentMap<Integer, PendingWrite> pendingWrites = new ConcurrentHashMap<Integer, PendingWrite>();
    private final AtomicBoolean retrying = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Slot> restoreQueue = new ConcurrentLinkedQueue<Slot>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean restoreBatchScheduled = new AtomicBoolean();
    private final AtomicBoolean hostReady = new AtomicBoolean();
    private final AtomicBoolean restoreBegun = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile @Nullable OfflineSessionSettings lastSettings;

    AppSessionEngine(
            OfflineSessionStore store,
            OfflineSessionHost host,
            Executor io,
            int ioLimit,
            ScheduledExecutorService scheduler,
            AppSessionClock clock,
            long loginCloseTimeoutMs) {
        this.store = store;
        this.host = host;
        this.work = new BoundedExecutor(io, ioLimit);
        this.scheduler = scheduler;
        this.clock = clock;
        this.loginCloseTimeoutMs = loginCloseTimeoutMs;
    }

    /** Throws when the host's settings or the table cannot be read, which leaves the engine unusable. */
    void boot() {
        OfflineSessionSettings settings = host.settings();
        lastSettings = settings;
        if (settings.getRetentionDays() > 0) {
            Instant cutoff = clock.now().minus(settings.getRetentionDays(), ChronoUnit.DAYS);
            int deleted = store.deleteClosedBefore(cutoff);
            log.info(
                    "App sessions: deleted {} closed session(s) older than {} day(s)",
                    deleted,
                    settings.getRetentionDays());
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
    }

    void unbindEvents() {
        presence.unbind();
    }

    void startScheduling() {
        if (stopped.get() || !scheduled.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(
                SafeRunnable.wrap(this::flushTick, log), FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
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
        OfflineSessionSettings settings = settings();
        log.info(
                "App sessions: restoring {} session(s), {} every {} ms",
                pending.size(),
                Math.min(settings.getRestoreBatchSize(), work.limit()),
                settings.getRestoreBatchIntervalMs());
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
        for (Slot slot : byId.values()) {
            slot.cancelEndTimer();
        }
        if (snapshot.isEmpty() && pendingWrites.isEmpty()) {
            return;
        }
        CompletableFuture<Void> flushed = new CompletableFuture<Void>();
        work.execute(() -> {
            try {
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
        try {
            flushed.get(SHUTDOWN_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.error("App sessions: storing active_ms at shutdown failed: {}", String.valueOf(e.getMessage()), e);
        }
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
        OfflineSession row = OfflineSession.builder()
                .id(UUIDv7.generate(now))
                .idempotencyKey(request.getIdempotencyKey())
                .characterId(request.getCharacterId())
                .activity(request.getActivity())
                .accountName(request.getAccountName())
                .masterAccountId(request.getMasterAccountId())
                .hwid(request.getHwid())
                .initiatorIp(request.getInitiatorIp())
                .spotPool(request.getSpotPool())
                .spotId(request.getSpotId())
                .originX(request.getOriginX())
                .originY(request.getOriginY())
                .originZ(request.getOriginZ())
                .originHeading(request.getOriginHeading())
                .startedAt(now)
                .plannedMs(request.getPlannedMs())
                .activeMs(0L)
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
                    break;
                case DUPLICATE_IDEMPOTENCY_KEY:
                    forget(slot);
                    Optional<OfflineSession> raced = store.findByIdempotencyKey(row.getIdempotencyKey());
                    return raced.isPresent()
                            ? AppSessionStartOutcome.existing(raced.get())
                            : AppSessionStartOutcome.unavailable("idempotency key conflict without a stored session");
                case CHARACTER_ACTIVE:
                    forget(slot);
                    Optional<OfflineSession> holder = store.findOpenByCharacterId(row.getCharacterId());
                    return holder.isPresent()
                            ? AppSessionStartOutcome.characterBusy(holder.get())
                            : AppSessionStartOutcome.unavailable("character session conflict without a stored session");
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

    private void rollBack(Slot slot, OfflineSession row, boolean placed) {
        if (placed) {
            removeQuietly(row, true);
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
        OfflineSessionSettings settings = settings();
        int batchSize = Math.min(settings.getRestoreBatchSize(), work.limit());
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
            scheduleRestoreBatch(settings.getRestoreBatchIntervalMs());
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
        OfflineSessionPlacement placement = placeQuietly(row);
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
            restoreQueue.add(slot);
            scheduleRestoreBatch(settings().getRestoreBatchIntervalMs());
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
        try {
            host.startAction(row);
        } catch (Throwable t) {
            log.error(
                    "App sessions: startAction on restore of {} threw {}",
                    row.getId(),
                    t.getClass().getName(),
                    t);
            close(slot, LogoutReason.APP_RESTORE_FAILED);
            return;
        }
        run(slot);
        OfflineSession view = slot.view(clock.monotonicMs());
        presence.online(view, true);
        log.info("App sessions: restored {}", view);
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
        removeQuietly(row, activeMs < settings().getReturnThresholdMs());
        Instant endedAt = nowSeconds();
        OfflineSession closed =
                row.toBuilder().endedAt(endedAt).endReason(reason).build();
        try {
            store.close(row.getId(), activeMs, endedAt, reason);
        } catch (RuntimeException e) {
            log.error(
                    "App sessions: closing session {} with {} failed in the database, retried until the table takes"
                            + " it: {}",
                    row.getId(),
                    reason,
                    e.getMessage(),
                    e);
            // before forget() frees the character, so a start in between sees it busy
            pendingWrites.put(row.getCharacterId(), PendingWrite.close(closed));
        }
        slot.row = closed;
        forget(slot);
        presence.offline(closed, reason);
        log.info("App sessions: ended {}", closed);
        return Optional.of(closed);
    }

    private void run(Slot slot) {
        long now = clock.monotonicMs();
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
                    } else {
                        store.close(
                                session.getId(), session.getActiveMs(), session.getEndedAt(), session.getEndReason());
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

    /** Falls back to the last good settings when reading throws, so an end never stalls on it. */
    private OfflineSessionSettings settings() {
        try {
            OfflineSessionSettings current = host.settings();
            if (current != null) {
                lastSettings = current;
                return current;
            }
        } catch (Throwable t) {
            log.error(
                    "App sessions: OfflineSessionHost.settings() threw {}",
                    t.getClass().getName(),
                    t);
        }
        OfflineSessionSettings last = lastSettings;
        return last != null ? last : OfflineSessionSettings.builder().build();
    }

    private void forget(Slot slot) {
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

        private PendingWrite(OfflineSession session, boolean delete) {
            this.session = session;
            this.delete = delete;
        }

        static PendingWrite close(OfflineSession closed) {
            return new PendingWrite(closed, false);
        }

        static PendingWrite delete(OfflineSession row) {
            return new PendingWrite(row, true);
        }
    }

    /** {@link #row}, {@link #phase} and {@link #restoreAttempts} change only on {@link #ops}. */
    static final class Slot {
        final UUID id;
        final SerialExecutor ops;
        volatile OfflineSession row;
        volatile Phase phase;
        volatile @Nullable CompletableFuture<AppSessionStartOutcome> startOutcome;
        int restoreAttempts;

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
