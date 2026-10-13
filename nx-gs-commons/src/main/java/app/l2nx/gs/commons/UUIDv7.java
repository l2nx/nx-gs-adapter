package app.l2nx.gs.commons;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/**
 * UUIDv7: 48-bit ms timestamp, 4-bit version, 12-bit monotonic counter, 2-bit variant, 62 random bits.
 * Strictly increasing per JVM: counter exhaustion (4096 ids/ms) advances the logical ms, so clock backsteps never regress ids.
 */
public final class UUIDv7 {

    private static final long TS_MASK = 0x0000_FFFF_FFFF_FFFFL;
    private static final long VERSION_BITS = 0x7L << 12; // version = 7 in MSB bits 12..15
    private static final int COUNTER_MAX = 0x0FFF;
    private static final long VARIANT_CLEAR_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final long VARIANT_SET_BIT = 0x8000_0000_0000_0000L;

    private static final Object LOCK = new Object();
    private static long lastTimestampMs = -1L;
    private static int subMsCounter = 0;

    private UUIDv7() {}

    public static UUID generate() {
        long timestampMs;
        int counter;

        synchronized (LOCK) {
            long nowMs = System.currentTimeMillis();
            if (nowMs > lastTimestampMs) {
                lastTimestampMs = nowMs;
                subMsCounter = 0;
            }
            // Stalled or backstepped clock: bump the counter, borrowing the next ms on exhaustion.
            if (subMsCounter > COUNTER_MAX) {
                lastTimestampMs += 1;
                subMsCounter = 0;
            }
            timestampMs = lastTimestampMs;
            counter = subMsCounter++;
        }

        return build(timestampMs, counter);
    }

    /**
     * Id whose timestamp is {@code instant} (millisecond precision); not monotonic, ids for the same instant are unordered.
     *
     * @throws IllegalArgumentException if the instant is before the epoch or beyond the 48-bit millisecond range
     */
    public static UUID generate(Instant instant) {
        long timestampMs = instant.toEpochMilli();
        if (timestampMs < 0 || timestampMs > TS_MASK) {
            throw new IllegalArgumentException("Instant out of UUIDv7 48-bit millisecond range: " + instant);
        }
        return build(timestampMs, ThreadLocalRandom.current().nextInt(COUNTER_MAX + 1));
    }

    private static UUID build(long timestampMs, int counter) {
        long msb = (timestampMs & TS_MASK) << 16;
        msb |= VERSION_BITS;
        msb |= (counter & 0x0FFFL);

        long lsb = ThreadLocalRandom.current().nextLong();
        lsb &= VARIANT_CLEAR_MASK;
        lsb |= VARIANT_SET_BIT;

        return new UUID(msb, lsb);
    }

    /** Null for null input; throws IllegalArgumentException for non-v7 ids. */
    public static @Nullable Instant extractCreatedAt(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Expected UUIDv7 but got version " + uuid.version());
        }
        long ms = uuid.getMostSignificantBits() >>> 16;
        return Instant.ofEpochMilli(ms);
    }

    /** Null for null or blank input; for required ids use UUID.fromString so absence fails loudly. */
    public static @Nullable UUID fromString(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return UUID.fromString(trimmed);
    }
}
