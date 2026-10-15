package app.l2nx.gs.adapter.core.appsessions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Scheduler and clock in one: {@link #advance} runs due tasks in due order on the calling thread. */
final class ManualScheduler implements ScheduledExecutorService, AppSessionClock {

    private long nowMs;
    private Instant wall = Instant.parse("2026-10-15T12:00:00Z");
    private long seq;
    private boolean shutdown;
    private final List<Task> tasks = new ArrayList<Task>();

    @Override
    public long monotonicMs() {
        return nowMs;
    }

    @Override
    public Instant now() {
        return wall;
    }

    void advance(long ms) {
        long target = nowMs + ms;
        while (true) {
            Task next = null;
            for (Task t : tasks) {
                if (!t.cancelled && t.dueMs <= target && (next == null || t.compareTo(next) < 0)) {
                    next = t;
                }
            }
            if (next == null) {
                break;
            }
            wall = wall.plusMillis(next.dueMs - nowMs);
            nowMs = next.dueMs;
            if (next.periodMs > 0) {
                next.dueMs = nowMs + next.periodMs;
                next.seq = seq++;
            } else {
                tasks.remove(next);
            }
            next.command.run();
        }
        wall = wall.plusMillis(target - nowMs);
        nowMs = target;
    }

    int pending() {
        int n = 0;
        for (Task t : tasks) {
            if (!t.cancelled) {
                n++;
            }
        }
        return n;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        Task t = new Task(command, nowMs + unit.toMillis(delay), 0L, seq++);
        tasks.add(t);
        return t;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        Task t = new Task(command, nowMs + unit.toMillis(initialDelay), unit.toMillis(delay), seq++);
        tasks.add(t);
        return t;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        shutdown = true;
        tasks.clear();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    private final class Task implements ScheduledFuture<Object> {
        final Runnable command;
        final long periodMs;
        long dueMs;
        long seq;
        boolean cancelled;

        Task(Runnable command, long dueMs, long periodMs, long seq) {
            this.command = command;
            this.dueMs = dueMs;
            this.periodMs = periodMs;
            this.seq = seq;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueMs - nowMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            Task other = (Task) o;
            int byDue = Long.compare(dueMs, other.dueMs);
            return byDue != 0 ? byDue : Long.compare(seq, other.seq);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() throws ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
