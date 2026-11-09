package app.l2nx.gs.adapter.api.kafka.events.raid.respawn;

import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * Full snapshot of every tracked raid / epic boss, not a delta: the consumer drops bosses absent from a newer one.
 * Respawn times are absolute, so cadence can be slow. Bosses outside the standard spawn managers are not reported.
 *
 * <p>{@code eventId} MUST be a UUIDv7; consumers dedupe and order on it. {@code metadata} keys may be added by hosts
 * without an API release; consumers ignore unknown ones.</p>
 */
public final class BossRespawnSnapshotEvent {

    private final UUID eventId;
    private final List<BossRespawnEntry> bosses;
    private final @Nullable Map<String, String> metadata;

    public BossRespawnSnapshotEvent(
            UUID eventId, @Nullable List<BossRespawnEntry> bosses, @Nullable Map<String, String> metadata) {
        this.eventId = Objects.requireNonNull(eventId, "BossRespawnSnapshotEvent.eventId is required");
        this.bosses = freezeList(bosses);
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    public UUID getEventId() {
        return eventId;
    }

    /** Never null; a {@code null} constructor argument becomes an empty list. Unmodifiable. */
    public List<BossRespawnEntry> getBosses() {
        return bosses;
    }

    /** Unmodifiable when non-null. */
    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public Builder toBuilder() {
        return new Builder().eventId(eventId).bosses(bosses).metadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<BossRespawnEntry> freezeList(@Nullable List<BossRespawnEntry> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<BossRespawnEntry>(src));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BossRespawnSnapshotEvent)) return false;
        BossRespawnSnapshotEvent that = (BossRespawnSnapshotEvent) o;
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(bosses, that.bosses)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, bosses, metadata);
    }

    @Override
    public String toString() {
        return "BossRespawnSnapshotEvent[eventId=" + eventId + ", bosses=" + bosses + ", metadata=" + metadata + "]";
    }

    public static final class Builder {
        private UUID eventId;
        private @Nullable List<BossRespawnEntry> bosses;
        private @Nullable Map<String, String> metadata;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder bosses(@Nullable List<BossRespawnEntry> bosses) {
            this.bosses = bosses;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public BossRespawnSnapshotEvent build() {
            return new BossRespawnSnapshotEvent(eventId, bosses, metadata);
        }
    }
}
