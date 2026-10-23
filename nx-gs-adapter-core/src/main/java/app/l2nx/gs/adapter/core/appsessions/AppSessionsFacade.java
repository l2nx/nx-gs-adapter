package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.capability.NxAppSessions;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * Handed out from JVM start, before the engine exists. A login waits for the booting engine so a restored session
 * never belongs to a character already played with a client. Nothing throws into the caller.
 */
final class AppSessionsFacade implements NxAppSessions {

    private static final NxLog log = NxLogFactory.getLogger(AppSessionsFacade.class);

    enum State {
        IDLE,
        BOOTING,
        READY,
        DISABLED
    }

    private final long bootWaitMs;
    private volatile State state = State.IDLE;
    private volatile @Nullable AppSessionEngine engine;
    private volatile CountDownLatch booted = new CountDownLatch(1);
    private final Set<Integer> lateLogins = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean hostReady = new AtomicBoolean();

    AppSessionsFacade(long bootWaitMs) {
        this.bootWaitMs = bootWaitMs;
    }

    State state() {
        return state;
    }

    @Nullable
    AppSessionEngine engine() {
        return engine;
    }

    void booting() {
        booted = new CountDownLatch(1);
        state = State.BOOTING;
    }

    void ready(AppSessionEngine ready) {
        engine = ready;
        state = State.READY;
        booted.countDown();
        drainLateLogins(ready);
        if (hostReady.get()) {
            ready.hostReady();
        }
    }

    void disable() {
        engine = null;
        state = State.DISABLED;
        booted.countDown();
    }

    void reset() {
        engine = null;
        state = State.IDLE;
        lateLogins.clear();
        hostReady.set(false);
        booted.countDown();
        booted = new CountDownLatch(1);
    }

    @Override
    public CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request) {
        AppSessionEngine e = engine;
        if (e == null) {
            return CompletableFuture.completedFuture(
                    AppSessionStartOutcome.unavailable("app sessions do not run on this server (" + state + ")"));
        }
        try {
            return e.start(request);
        } catch (Throwable t) {
            log.error("App sessions: start threw {}", t.getClass().getName(), t);
            return CompletableFuture.completedFuture(AppSessionStartOutcome.unavailable(
                    "start threw " + t.getClass().getName()));
        }
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity) {
        AppSessionEngine e = engine;
        if (e == null) {
            return CompletableFuture.completedFuture(Optional.<OfflineSession>empty());
        }
        try {
            return e.stop(characterId, activity);
        } catch (Throwable t) {
            log.error("App sessions: stop threw {}", t.getClass().getName(), t);
            return CompletableFuture.completedFuture(Optional.<OfflineSession>empty());
        }
    }

    @Override
    public void onActionEnded(UUID sessionId, LogoutReason reason) {
        AppSessionEngine e = engine;
        if (e == null) {
            log.warn("App sessions: onActionEnded({}, {}) while no engine runs", sessionId, reason);
            return;
        }
        try {
            e.onActionEnded(sessionId, reason);
        } catch (Throwable t) {
            log.error("App sessions: onActionEnded threw {}", t.getClass().getName(), t);
        }
    }

    @Override
    public void hostReady() {
        if (!hostReady.compareAndSet(false, true)) {
            return;
        }
        // an engine still booting gets it from ready()
        AppSessionEngine e = engine;
        if (e == null) {
            return;
        }
        try {
            e.hostReady();
        } catch (Throwable t) {
            log.error("App sessions: hostReady threw {}", t.getClass().getName(), t);
        }
    }

    @Override
    public ClientLoginOutcome onClientLogin(int characterId) {
        try {
            if (state == State.BOOTING && !awaitBoot()) {
                closeWhenReady(characterId);
                return ClientLoginOutcome.pending();
            }
            AppSessionEngine e = engine;
            return e == null ? ClientLoginOutcome.none() : e.onClientLogin(characterId);
        } catch (Throwable t) {
            log.error("App sessions: onClientLogin threw {}", t.getClass().getName(), t);
            return ClientLoginOutcome.none();
        }
    }

    @Override
    public Optional<OfflineSession> findActive(int characterId) {
        AppSessionEngine e = engine;
        if (e == null) {
            return Optional.empty();
        }
        try {
            return e.findActive(characterId);
        } catch (Throwable t) {
            log.error("App sessions: findActive threw {}", t.getClass().getName(), t);
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson) {
        AppSessionEngine e = engine;
        if (e == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        try {
            return e.updateTypeState(sessionId, typeStateJson);
        } catch (Throwable t) {
            log.error("App sessions: updateTypeState threw {}", t.getClass().getName(), t);
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    }

    @Override
    public List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity) {
        AppSessionEngine e = engine;
        if (e == null || masterAccountId == null || activity == null) {
            return Collections.emptyList();
        }
        try {
            return e.findOpenByMasterAccount(masterAccountId, activity);
        } catch (Throwable t) {
            log.error(
                    "App sessions: findOpenByMasterAccount threw {}",
                    t.getClass().getName(),
                    t);
            return Collections.emptyList();
        }
    }

    @Override
    public List<OfflineSession> findOpen(ActivityType activity) {
        AppSessionEngine e = engine;
        if (e == null || activity == null) {
            return Collections.emptyList();
        }
        try {
            return e.findOpen(activity);
        } catch (Throwable t) {
            log.error("App sessions: findOpen threw {}", t.getClass().getName(), t);
            return Collections.emptyList();
        }
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> findLastStartedByMasterAccount(
            UUID masterAccountId, ActivityType activity) {
        AppSessionEngine e = engine;
        if (e == null) {
            return notReady();
        }
        if (masterAccountId == null) {
            return CompletableFuture.completedFuture(Optional.<OfflineSession>empty());
        }
        try {
            return e.findLastStartedByMasterAccount(masterAccountId, activity);
        } catch (Throwable t) {
            log.error(
                    "App sessions: findLastStartedByMasterAccount threw {}",
                    t.getClass().getName(),
                    t);
            return failed(t);
        }
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> findLastStartedByCharacter(
            int characterId, ActivityType activity) {
        AppSessionEngine e = engine;
        if (e == null) {
            return notReady();
        }
        try {
            return e.findLastStartedByCharacter(characterId, activity);
        } catch (Throwable t) {
            log.error(
                    "App sessions: findLastStartedByCharacter threw {}",
                    t.getClass().getName(),
                    t);
            return failed(t);
        }
    }

    /** The host must not read "no engine yet" as "no previous session" and skip its cooldown. */
    private CompletableFuture<Optional<OfflineSession>> notReady() {
        return failed(new IllegalStateException("app sessions not ready (" + state + ")"));
    }

    private static <T> CompletableFuture<T> failed(Throwable t) {
        CompletableFuture<T> failed = new CompletableFuture<T>();
        failed.completeExceptionally(t);
        return failed;
    }

    private boolean awaitBoot() {
        try {
            if (booted.await(bootWaitMs, TimeUnit.MILLISECONDS)) {
                return true;
            }
            log.error(
                    "App sessions: the engine did not finish booting within {} ms; the host refuses this login and the"
                            + " character's session is closed with APP_LOGIN once the engine is ready",
                    bootWaitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("App sessions: a login was interrupted while waiting for the engine to boot; the host refuses it"
                    + " and the character's session is closed with APP_LOGIN once the engine is ready");
        }
        return false;
    }

    private void closeWhenReady(int characterId) {
        lateLogins.add(characterId);
        AppSessionEngine late = engine;
        if (late != null) {
            drainLateLogins(late);
        }
    }

    private void drainLateLogins(AppSessionEngine ready) {
        for (Integer characterId : lateLogins) {
            if (lateLogins.remove(characterId)) {
                ready.closeAfterLateLogin(characterId);
            }
        }
    }
}
