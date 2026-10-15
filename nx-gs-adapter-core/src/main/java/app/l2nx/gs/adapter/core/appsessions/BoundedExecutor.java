package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** At most {@code limit} tasks in flight, the rest wait in order: engine work blocks, the IO pool is shared. */
final class BoundedExecutor implements Executor {

    private static final NxLog log = NxLogFactory.getLogger(BoundedExecutor.class);

    private final Executor delegate;
    private final int limit;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<Runnable>();
    private int running;

    BoundedExecutor(Executor delegate, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        this.delegate = delegate;
        this.limit = limit;
    }

    static int limitFor(int ioWorkers) {
        return Math.max(1, ioWorkers / 2);
    }

    int limit() {
        return limit;
    }

    @Override
    public void execute(Runnable task) {
        synchronized (queue) {
            queue.add(task);
            if (running >= limit) {
                return;
            }
            running++;
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
                    running--;
                    return;
                }
            }
            try {
                next.run();
            } catch (Throwable t) {
                log.error("App-session task threw {}", t.getClass().getName(), t);
            }
        }
    }
}
