package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BoundedExecutorTest {

    @Nested
    class LimitFor {

        @ParameterizedTest(name = "{0} IO workers -> {1}")
        @CsvSource({"1, 1", "2, 1", "3, 1", "4, 2", "9, 4"})
        void limitFor_shouldTakeHalfOfTheIoWorkersAndAtLeastOne(int ioWorkers, int expected) {
            assertEquals(expected, BoundedExecutor.limitFor(ioWorkers));
        }
    }

    @Nested
    class Execute {

        @Test
        void execute_shouldNeverRunMoreThanTheLimitAtOnce_whenThePoolHasMoreThreads() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                BoundedExecutor bounded = new BoundedExecutor(pool, 2);
                AtomicInteger running = new AtomicInteger();
                AtomicInteger peak = new AtomicInteger();
                CountDownLatch done = new CountDownLatch(10);
                for (int i = 0; i < 10; i++) {
                    bounded.execute(() -> {
                        int now = running.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        try {
                            Thread.sleep(20L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        running.decrementAndGet();
                        done.countDown();
                    });
                }

                assertTrue(done.await(10, TimeUnit.SECONDS));
                assertEquals(2, peak.get());
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void execute_shouldRunInOrderOnTheCaller_whenThePoolIsShutDown() {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.shutdown();
            BoundedExecutor bounded = new BoundedExecutor(pool, 1);
            List<Integer> ran = new ArrayList<Integer>();

            bounded.execute(() -> ran.add(1));
            bounded.execute(() -> ran.add(2));

            assertEquals(2, ran.size());
            assertEquals(Integer.valueOf(1), ran.get(0));
        }
    }
}
