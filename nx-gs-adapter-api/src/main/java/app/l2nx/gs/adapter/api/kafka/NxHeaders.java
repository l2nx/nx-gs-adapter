package app.l2nx.gs.adapter.api.kafka;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/** Kafka header names and UUID codecs shared by the adapter and platform consumers. */
public final class NxHeaders {

    /**
     * Game-server id as 16 raw bytes ({@link #encodeUuid(UUID)}); its direction depends on the topic. On the shared
     * commands topic it is the target, and the adapter drops a command whose id differs from its own or is missing. On
     * every other topic (events, settings, gd-sync, command replies) it is the source. Consumers must not derive a
     * source server from a command record.
     */
    public static final String SERVER_ID = "Server-Id";

    /** UTF-8 simple class name of the payload type; consumers pick the deserializer from it without parsing JSON. */
    public static final String MESSAGE_TYPE = "Message-Type";

    /** Platform-issued correlation id as a textual UUID; echoed on the reply so the platform can route it back. */
    public static final String CORRELATION_ID = "Correlation-Id";

    private NxHeaders() {}

    /** Encodes as 16 bytes: most-significant long then least-significant long, big-endian. */
    public static byte[] encodeUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    /** Inverse of {@link #encodeUuid(UUID)}; throws {@link IllegalArgumentException} for null or non-16-byte input. */
    public static UUID decodeUuid(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID header value must not be null");
        }
        if (value.length != 16) {
            throw new IllegalArgumentException("UUID header value must be 16 bytes, got " + value.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(value);
        long msb = buf.getLong();
        long lsb = buf.getLong();
        return new UUID(msb, lsb);
    }
}
