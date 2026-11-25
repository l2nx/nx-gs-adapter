package app.l2nx.gs.commons;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UUIDv7Test {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "2026-03-04T05:06:07.089Z, 2026-03-04T05:06:07.089Z",
        "2026-03-04T05:06:07.089123456Z, 2026-03-04T05:06:07.089Z"
    })
    void generateWithInstant_shouldEncodeTheMillisecondsAsVersion7Variant2(Instant instant, Instant expected) {
        UUID id = UUIDv7.generate(instant);

        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        assertEquals(expected, UUIDv7.extractCreatedAt(id));
    }

    @Test
    void generateWithInstant_shouldReturnDistinctIds_whenInstantRepeats() {
        Instant instant = Instant.parse("2026-03-04T05:06:07.089Z");
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(UUIDv7.generate(instant));
        }

        assertTrue(ids.size() > 90, "expected near-unique ids, got " + ids.size());
    }

    @Test
    void generateWithInstant_shouldThrow_whenBeforeEpoch() {
        assertThrows(IllegalArgumentException.class, () -> UUIDv7.generate(Instant.ofEpochMilli(-1)));
    }

    @Test
    void withTimestamp_shouldKeepSourceCounterAndRandomBits_underGivenInstant() {
        Instant instant = Instant.parse("2026-03-04T05:06:07.089Z");
        UUID source = UUIDv7.generate(Instant.parse("2020-01-01T00:00:00Z"));

        UUID id = UUIDv7.withTimestamp(instant, source);

        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        assertEquals(instant, UUIDv7.extractCreatedAt(id));
        assertEquals(source.getMostSignificantBits() & 0x0FFFL, id.getMostSignificantBits() & 0x0FFFL);
        assertEquals(source.getLeastSignificantBits(), id.getLeastSignificantBits());
    }

    @Test
    void withTimestamp_shouldThrow_whenBeforeEpoch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UUIDv7.withTimestamp(Instant.ofEpochMilli(-1L), UUIDv7.generate()));
    }

    @Test
    void generate_shouldReturnVersion7Variant2() {
        UUID id = UUIDv7.generate();

        assertEquals(7, id.version());
        assertEquals(2, id.variant());
    }

    @Test
    void generate_shouldEncodeNearCurrentEpochMs() {
        long beforeMs = System.currentTimeMillis();
        UUID id = UUIDv7.generate();
        long afterMs = System.currentTimeMillis();

        Instant extracted = UUIDv7.extractCreatedAt(id);
        assertNotNull(extracted);
        long extractedMs = extracted.toEpochMilli();

        assertTrue(extractedMs >= beforeMs, "extractedMs (" + extractedMs + ") < beforeMs (" + beforeMs + ")");
        // burst tests share the static clock and may have borrowed a few milliseconds ahead
        long upperBoundMs = afterMs + 1_000L;
        assertTrue(extractedMs <= upperBoundMs, "extractedMs (" + extractedMs + ") > " + upperBoundMs);
    }

    @Test
    void generate_shouldBeMonotonic_acrossBurstInSameMillisecond() {
        int n = 5000;
        UUID prev = UUIDv7.generate();
        for (int i = 1; i < n; i++) {
            UUID curr = UUIDv7.generate();
            assertTrue(curr.compareTo(prev) > 0, "non-monotonic at i=" + i + ": prev=" + prev + " curr=" + curr);
            prev = curr;
        }
    }

    @Test
    void generate_shouldProduceUniqueIds_underHighConcurrency() throws InterruptedException {
        int threads = 8;
        int perThread = 2_000;
        Set<UUID> all = java.util.Collections.synchronizedSet(new HashSet<>(threads * perThread));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        all.add(UUIDv7.generate());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent generation timed out");
        pool.shutdownNow();

        assertEquals(threads * perThread, all.size(), "duplicate ids produced under concurrency");
    }

    @Test
    void extractCreatedAt_shouldReturnNullForNull() {
        assertNull(UUIDv7.extractCreatedAt(null));
    }

    @Test
    void extractCreatedAt_shouldThrow_forNonVersion7() {
        UUID v4 = UUID.randomUUID();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> UUIDv7.extractCreatedAt(v4));
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    void fromString_shouldReturnNullForNull() {
        assertNull(UUIDv7.fromString(null));
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"", "   \t\n  "})
    void fromString_shouldReturnNull_whenBlank(String value) {
        assertNull(UUIDv7.fromString(value));
    }

    @Test
    void fromString_shouldTrimSurroundingWhitespace() {
        UUID original = UUID.randomUUID();
        UUID parsed = UUIDv7.fromString("  " + original + "  ");
        assertEquals(original, parsed);
    }
}
