package app.l2nx.gs.adapter.core.sync;

import app.l2nx.gs.adapter.api.spi.capability.NxRuntimeSync;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import org.jspecify.annotations.Nullable;

/** JVM-wide, so the host's call survives a reconnect; a callback that threw is logged, never thrown at the host. */
public final class RuntimeSyncGate implements NxRuntimeSync {

    private static final NxLog log = NxLogFactory.getLogger(RuntimeSyncGate.class);

    private boolean started;
    private @Nullable Runnable waiting;

    @Override
    public void hostStarted() {
        Runnable callback;
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
            callback = waiting;
            waiting = null;
        }
        log.info("Runtime sync: the host declared its start");
        if (callback != null) {
            runQuietly(callback);
        }
    }

    @Override
    public void onHostStarted(Runnable callback) {
        synchronized (this) {
            if (!started) {
                waiting = callback;
                return;
            }
        }
        runQuietly(callback);
    }

    public synchronized void resetForTesting() {
        started = false;
        waiting = null;
    }

    private static void runQuietly(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable t) {
            log.error("Runtime sync: the start callback threw {}", t.getClass().getName(), t);
        }
    }
}
