package app.l2nx.gs.adapter.api.kafka.events.privatestore;

import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.StoreState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Full state, not a delta, of one trader's private store: the consumer replaces what it holds for {@code charId};
 * {@code store == null} is a tombstone (no market store any more). Open stores are re-sent at least every 10 minutes
 * (at-least-once, so be idempotent); a trader silent for 25 minutes may be treated as closed.
 */
public final class PrivateStoreStateEvent {

    private final UUID eventId;
    private final long charId;
    private final @Nullable StoreState store;
    private final @Nullable Map<String, String> metadata;

    public PrivateStoreStateEvent(
            UUID eventId, long charId, @Nullable StoreState store, @Nullable Map<String, String> metadata) {
        this.eventId = eventId;
        this.charId = charId;
        this.store = store;
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    /** UUIDv7: the occurrence time derives from it and consumers dedupe on it. */
    public UUID getEventId() {
        return eventId;
    }

    /** Trader character id; also the Kafka partition key (8-byte BE), keeping one trader's updates ordered. */
    public long getCharId() {
        return charId;
    }

    public @Nullable StoreState getStore() {
        return store;
    }

    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public Builder toBuilder() {
        return new Builder().eventId(eventId).charId(charId).store(store).metadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrivateStoreStateEvent)) return false;
        PrivateStoreStateEvent that = (PrivateStoreStateEvent) o;
        return charId == that.charId
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(store, that.store)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, charId, store, metadata);
    }

    @Override
    public String toString() {
        return "PrivateStoreStateEvent[eventId=" + eventId
                + ", charId=" + charId
                + ", store=" + store
                + ", metadata=" + metadata + "]";
    }

    public static final class Builder {
        private UUID eventId;
        private long charId;
        private @Nullable StoreState store;
        private @Nullable Map<String, String> metadata;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder charId(long charId) {
            this.charId = charId;
            return this;
        }

        public Builder store(@Nullable StoreState store) {
            this.store = store;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public PrivateStoreStateEvent build() {
            return new PrivateStoreStateEvent(eventId, charId, store, metadata);
        }
    }
}
