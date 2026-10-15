package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;

/** One session's tasks run one after another, so they never overlap and no lock is held during host or DB work. */
final class SerialExecutor implements Executor {

    private static final NxLog log = NxLogFactory.getLogger(SerialExecutor.class);

    private final Executor delegate;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<Runnable>();
    private boolean draining;

    SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        synchronized (queue) {
            queue.add(task);
            if (draining) {
                return;
            }
            draining = true;
        }
        try {
            delegate.execute(this::drain);
        } catch (RejectedExecutionException shuttingDown) {
            // pool gone on shutdown: run here so no caller waits on a future that never completes
            drain();
        }
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (queue) {
                next = queue.poll();
                if (next == null) {
                    draining = false;
                    return;
                }
            }
            runQuietly(next);
        }
    }

    private static void runQuietly(@Nullable Runnable task) {
        try {
            if (task != null) {
                task.run();
            }
        } catch (Throwable t) {
            log.error("App-session task threw {}", t.getClass().getName(), t);
        }
    }
}
