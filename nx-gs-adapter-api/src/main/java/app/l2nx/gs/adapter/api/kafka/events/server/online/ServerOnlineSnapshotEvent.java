package app.l2nx.gs.adapter.api.kafka.events.server.online;

import app.l2nx.gs.adapter.api.kafka.events.server.online.model.ServerOnlineBucket;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * Periodic population breakdown by activity bucket, on the {@code serveronline} family topic.
 *
 * <p>{@code eventId} MUST be a UUIDv7 (upper 48 bits = timestamp); consumers dedupe on it
 * (at-least-once) and order within a server by it.</p>
 *
 * <p>{@code buckets} MUST carry {@link ServerOnlineBucket#TOTAL} and {@link ServerOnlineBucket#UNIQUE}; the rest are
 * optional. Buckets may overlap, so consumers MUST NOT derive a total as {@code sum(buckets)}; read {@code TOTAL}.</p>
 *
 * <p>{@code metadata} is a separate open string map of snapshot attributes (no counts); hosts MAY add
 * keys without an API release, consumers ignore unknown ones.</p>
 */
public final class ServerOnlineSnapshotEvent {

    private final UUID eventId;
    private final Map<ServerOnlineBucket, Long> buckets;
    private final @Nullable Map<String, String> metadata;

    public ServerOnlineSnapshotEvent(
            UUID eventId, @Nullable Map<ServerOnlineBucket, Long> buckets, @Nullable Map<String, String> metadata) {
        this.eventId = eventId;
        this.buckets = freezeMap(buckets);
        requireRequiredBuckets(this.buckets);
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    /** MUST be a UUIDv7. */
    public UUID getEventId() {
        return eventId;
    }

    /** Non-negative counts, unmodifiable. Gson bypasses the constructor, so a consumer re-checks the required keys. */
    public Map<ServerOnlineBucket, Long> getBuckets() {
        return buckets == null ? Collections.<ServerOnlineBucket, Long>emptyMap() : buckets;
    }

    /** Unmodifiable when non-null. */
    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public Builder toBuilder() {
        return new Builder().eventId(eventId).buckets(buckets).metadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Map<ServerOnlineBucket, Long> freezeMap(@Nullable Map<ServerOnlineBucket, Long> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new EnumMap<ServerOnlineBucket, Long>(src));
    }

    private static void requireRequiredBuckets(Map<ServerOnlineBucket, Long> buckets) {
        if (!buckets.containsKey(ServerOnlineBucket.TOTAL) || !buckets.containsKey(ServerOnlineBucket.UNIQUE)) {
            throw new IllegalArgumentException("ServerOnlineSnapshotEvent.buckets must carry TOTAL and UNIQUE");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerOnlineSnapshotEvent)) return false;
        ServerOnlineSnapshotEvent that = (ServerOnlineSnapshotEvent) o;
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(buckets, that.buckets)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, buckets, metadata);
    }

    @Override
    public String toString() {
        return "ServerOnlineSnapshotEvent[eventId=" + eventId + ", buckets=" + buckets + ", metadata=" + metadata + "]";
    }

    public static final class Builder {
        private UUID eventId;
        private @Nullable Map<ServerOnlineBucket, Long> buckets;
        private @Nullable Map<String, String> metadata;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder buckets(@Nullable Map<ServerOnlineBucket, Long> buckets) {
            this.buckets = buckets;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ServerOnlineSnapshotEvent build() {
            return new ServerOnlineSnapshotEvent(eventId, buckets, metadata);
        }
    }
}
