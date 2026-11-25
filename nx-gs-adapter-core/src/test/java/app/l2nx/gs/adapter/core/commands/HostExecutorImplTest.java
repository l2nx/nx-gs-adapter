package app.l2nx.gs.adapter.core.commands;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.spi.HostExecutorTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HostExecutorImplTest {

    private static final long TEST_SYNC_TIMEOUT_MS = 200L;
    private static final Executor IMMEDIATE_EXECUTOR = Runnable::run;

    @Test
    void sync_runnable_shouldRunOnExecutor() {
        AtomicBoolean ran = new AtomicBoolean(false);
        HostExecutorImpl host = new HostExecutorImpl(IMMEDIATE_EXECUTOR, TEST_SYNC_TIMEOUT_MS);

        host.sync(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void sync_supplier_shouldReturnResult() {
        HostExecutorImpl host = new HostExecutorImpl(IMMEDIATE_EXECUTOR, TEST_SYNC_TIMEOUT_MS);

        String result = host.sync(() -> "hello");

        assertEquals("hello", result);
    }

    @Test
    void sync_shouldWaitForTheTask_whenTheExecutorRunsItOnAnotherThread() {
        Thread caller = Thread.currentThread();
        CountDownLatch returned = new CountDownLatch(1);
        Executor otherThread = task -> {
            Thread worker = new Thread(task);
            worker.setDaemon(true);
            worker.start();
        };
        HostExecutorImpl host = new HostExecutorImpl(otherThread, TimeUnit.MINUTES.toMillis(1));

        String result;
        try {
            result = host.sync(() -> {
                // The task finishes only once the caller is parked inside sync.
                while (returned.getCount() > 0 && !isParked(caller)) {
                    Thread.yield();
                }
                return "hello";
            });
        } finally {
            returned.countDown();
        }

        assertEquals("hello", result);
    }

    @Test
    void sync_runnable_shouldBeNoOpOnNullTask() {
        HostExecutorImpl host = new HostExecutorImpl(IMMEDIATE_EXECUTOR, TEST_SYNC_TIMEOUT_MS);

        host.sync((Runnable) null);
    }

    @Test
    void sync_supplier_shouldReturnNullOnNullTask() {
        HostExecutorImpl host = new HostExecutorImpl(IMMEDIATE_EXECUTOR, TEST_SYNC_TIMEOUT_MS);

        assertNull(host.sync((java.util.function.Supplier<String>) null));
    }

    @Test
    void syncAndAsync_unregistered_shouldThrowIllegalStateException() {
        HostExecutorImpl host = new HostExecutorImpl(null, TEST_SYNC_TIMEOUT_MS);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> host.sync(() -> {}));
        assertTrue(ex.getMessage().contains("HostExecutor not registered"));
        assertThrows(IllegalStateException.class, () -> host.async(() -> {}));
    }

    @Test
    void sync_taskThrowingRuntimeException_shouldPropagate() {
        HostExecutorImpl host = new HostExecutorImpl(IMMEDIATE_EXECUTOR, TEST_SYNC_TIMEOUT_MS);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> host.sync(() -> {
                    throw new IllegalStateException("boom");
                }));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void sync_executorRejectingTask_shouldPropagate() {
        Executor rejecting = task -> {
            throw new java.util.concurrent.RejectedExecutionException("nope");
        };
        HostExecutorImpl host = new HostExecutorImpl(rejecting, TEST_SYNC_TIMEOUT_MS);

        assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> host.sync(() -> {}));
    }

    @Test
    void sync_executorNeverCompletes_shouldThrowTimeoutException() {
        Executor blackHole = task -> {};
        HostExecutorImpl host = new HostExecutorImpl(blackHole, TEST_SYNC_TIMEOUT_MS);

        HostExecutorTimeoutException ex = assertThrows(HostExecutorTimeoutException.class, () -> host.sync(() -> {}));

        assertEquals(TEST_SYNC_TIMEOUT_MS, ex.getTimeoutMs());
    }

    @Test
    void async_shouldDelegateToExecutor() {
        AtomicReference<Runnable> captured = new AtomicReference<>();
        Executor capturing = captured::set;
        HostExecutorImpl host = new HostExecutorImpl(capturing, TEST_SYNC_TIMEOUT_MS);

        host.async(() -> {});

        assertNotNull(captured.get());
    }

    @Test
    void async_shouldWrapTaskInSafeRunnable_swallowingThrowables() {
        AtomicReference<Throwable> caughtByExecutor = new AtomicReference<>();
        Executor immediateCatching = task -> {
            try {
                task.run();
            } catch (Throwable t) {
                caughtByExecutor.set(t);
            }
        };
        HostExecutorImpl host = new HostExecutorImpl(immediateCatching, TEST_SYNC_TIMEOUT_MS);

        host.async(() -> {
            throw new RuntimeException("boom");
        });

        assertNull(caughtByExecutor.get(), "SafeRunnable.wrap should swallow task exceptions");
    }

    @Test
    void async_shouldBeNoOpOnNullTask() {
        AtomicBoolean executorInvoked = new AtomicBoolean(false);
        Executor recording = task -> executorInvoked.set(true);
        HostExecutorImpl host = new HostExecutorImpl(recording, TEST_SYNC_TIMEOUT_MS);

        host.async(null);

        assertFalse(executorInvoked.get());
    }

    private static boolean isParked(Thread thread) {
        Thread.State state = thread.getState();
        return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
    }
}
