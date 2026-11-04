package app.l2nx.gs.adapter.api.kafka.events.character.result;

import app.l2nx.gs.adapter.api.domain.item.ItemSource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Stackable items of one key of a session. The same item both gained and lost under one key nets out: {@code delta} is
 * the net and {@code events} the operations, so a net of {@code 0} is a valid row. A row folded by a host loses
 * {@code sourceRefId} and {@code sourceCharacterId} and joins the row of the remaining key.
 */
public final class SessionResultItem {

    private final long itemTemplateId;
    private final ItemSource source;
    private final @Nullable Long sourceNpcId;
    private final @Nullable Long sourceCharacterId;
    private final @Nullable String sourceRefId;
    private final long delta;
    private final long events;

    public SessionResultItem(
            long itemTemplateId,
            ItemSource source,
            @Nullable Long sourceNpcId,
            @Nullable Long sourceCharacterId,
            @Nullable String sourceRefId,
            long delta,
            long events) {
        if (itemTemplateId <= 0L) {
            throw new IllegalArgumentException(
                    "SessionResultItem.itemTemplateId must be positive, got " + itemTemplateId);
        }
        if (events <= 0L) {
            throw new IllegalArgumentException("SessionResultItem.events must be positive, got " + events);
        }
        this.itemTemplateId = itemTemplateId;
        this.source = Objects.requireNonNull(source, "SessionResultItem.source is required");
        this.sourceNpcId = sourceNpcId;
        this.sourceCharacterId = sourceCharacterId;
        this.sourceRefId = SessionResultText.blankToNull(sourceRefId);
        this.delta = delta;
        this.events = events;
    }

    public long getItemTemplateId() {
        return itemTemplateId;
    }

    public ItemSource getSource() {
        return source;
    }

    /** NPC template id. */
    public @Nullable Long getSourceNpcId() {
        return sourceNpcId;
    }

    /** The other character of the operation. */
    public @Nullable Long getSourceCharacterId() {
        return sourceCharacterId;
    }

    /**
     * Opaque link to the event already describing the operation, part of the row's key. A consumer joins on it, never
     * parses it, and tolerates one it cannot join yet; {@code null} when no such event exists, never empty.
     */
    public @Nullable String getSourceRefId() {
        return sourceRefId;
    }

    /** Signed net as the character sees it: positive gained, negative lost. */
    public long getDelta() {
        return delta;
    }

    public long getEvents() {
        return events;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemTemplateId(itemTemplateId)
                .source(source)
                .sourceNpcId(sourceNpcId)
                .sourceCharacterId(sourceCharacterId)
                .sourceRefId(sourceRefId)
                .delta(delta)
                .events(events);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResultItem)) return false;
        SessionResultItem that = (SessionResultItem) o;
        return itemTemplateId == that.itemTemplateId
                && delta == that.delta
                && events == that.events
                && source == that.source
                && Objects.equals(sourceNpcId, that.sourceNpcId)
                && Objects.equals(sourceCharacterId, that.sourceCharacterId)
                && Objects.equals(sourceRefId, that.sourceRefId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, source, sourceNpcId, sourceCharacterId, sourceRefId, delta, events);
    }

    @Override
    public String toString() {
        return "SessionResultItem[itemTemplateId=" + itemTemplateId
                + ", source=" + source
                + ", sourceNpcId=" + sourceNpcId
                + ", sourceCharacterId=" + sourceCharacterId
                + ", sourceRefId=" + sourceRefId
                + ", delta=" + delta
                + ", events=" + events + "]";
    }

    public static final class Builder {
        private long itemTemplateId;
        private @Nullable ItemSource source;
        private @Nullable Long sourceNpcId;
        private @Nullable Long sourceCharacterId;
        private @Nullable String sourceRefId;
        private long delta;
        private long events;

        public Builder itemTemplateId(long itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder source(ItemSource source) {
            this.source = source;
            return this;
        }

        public Builder sourceNpcId(@Nullable Long sourceNpcId) {
            this.sourceNpcId = sourceNpcId;
            return this;
        }

        public Builder sourceCharacterId(@Nullable Long sourceCharacterId) {
            this.sourceCharacterId = sourceCharacterId;
            return this;
        }

        public Builder sourceRefId(@Nullable String sourceRefId) {
            this.sourceRefId = sourceRefId;
            return this;
        }

        public Builder delta(long delta) {
            this.delta = delta;
            return this;
        }

        public Builder events(long events) {
            this.events = events;
            return this;
        }

        public SessionResultItem build() {
            return new SessionResultItem(
                    itemTemplateId, source, sourceNpcId, sourceCharacterId, sourceRefId, delta, events);
        }
    }
}
