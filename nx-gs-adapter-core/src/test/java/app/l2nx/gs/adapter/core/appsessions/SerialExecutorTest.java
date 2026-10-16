package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SerialExecutorTest {

    @Test
    void runsOnCurrentThread_shouldBeTrueOnlyInsideATaskOfThisExecutor() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            List<Boolean> inside = new ArrayList<Boolean>();
            Future<Boolean> fromOtherThread =
                    Executors.newSingleThreadExecutor().submit(serial::runsOnCurrentThread);

            serial.execute(() -> inside.add(serial.runsOnCurrentThread()));
            pool.submit(() -> {}).get(5, TimeUnit.SECONDS);

            assertEquals(1, inside.size());
            assertTrue(inside.get(0));
            assertFalse(fromOtherThread.get(5, TimeUnit.SECONDS));
            assertFalse(serial.runsOnCurrentThread());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void runsOnCurrentThread_shouldBeTrueOnTheCaller_whenThePoolRejectsAndTheCallerDrains() {
        SerialExecutor serial = new SerialExecutor(task -> {
            throw new RejectedExecutionException("pool gone");
        });
        List<Boolean> inside = new ArrayList<Boolean>();

        serial.execute(() -> inside.add(serial.runsOnCurrentThread()));

        assertEquals(1, inside.size());
        assertTrue(inside.get(0));
        assertFalse(serial.runsOnCurrentThread());
    }

    @Test
    void runsOnCurrentThread_shouldHoldForEveryExecutorDrainingOnTheThread_whenTheyNest() {
        SerialExecutor outer = new SerialExecutor(Runnable::run);
        SerialExecutor inner = new SerialExecutor(Runnable::run);
        List<Boolean> seen = new ArrayList<Boolean>();

        outer.execute(() -> inner.execute(() -> {
            seen.add(inner.runsOnCurrentThread());
            seen.add(outer.runsOnCurrentThread());
        }));

        assertEquals(2, seen.size());
        assertTrue(seen.get(0));
        assertTrue(seen.get(1));
    }
}
