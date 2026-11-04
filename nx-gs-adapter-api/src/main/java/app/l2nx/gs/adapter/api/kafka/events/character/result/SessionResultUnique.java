package app.l2nx.gs.adapter.api.kafka.events.character.result;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.domain.item.ItemSource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One non-stackable item a session moved, with its state at that moment. */
public final class SessionResultUnique {

    private final ItemSnapshot item;
    private final ItemSource source;
    private final long delta;
    private final @Nullable Long sourceNpcId;
    private final @Nullable Long sourceCharacterId;
    private final @Nullable String sourceRefId;

    public SessionResultUnique(
            ItemSnapshot item,
            ItemSource source,
            long delta,
            @Nullable Long sourceNpcId,
            @Nullable Long sourceCharacterId,
            @Nullable String sourceRefId) {
        if (delta == 0L) {
            throw new IllegalArgumentException("SessionResultUnique.delta must not be 0");
        }
        this.item = Objects.requireNonNull(item, "SessionResultUnique.item is required");
        this.source = Objects.requireNonNull(source, "SessionResultUnique.source is required");
        this.delta = delta;
        this.sourceNpcId = sourceNpcId;
        this.sourceCharacterId = sourceCharacterId;
        this.sourceRefId = SessionResultText.blankToNull(sourceRefId);
    }

    public ItemSnapshot getItem() {
        return item;
    }

    public ItemSource getSource() {
        return source;
    }

    /** The moved count with its sign as the character sees it; never {@code 0}. */
    public long getDelta() {
        return delta;
    }

    public @Nullable Long getSourceNpcId() {
        return sourceNpcId;
    }

    public @Nullable Long getSourceCharacterId() {
        return sourceCharacterId;
    }

    /** As {@link SessionResultItem#getSourceRefId()}. */
    public @Nullable String getSourceRefId() {
        return sourceRefId;
    }

    public Builder toBuilder() {
        return new Builder()
                .item(item)
                .source(source)
                .delta(delta)
                .sourceNpcId(sourceNpcId)
                .sourceCharacterId(sourceCharacterId)
                .sourceRefId(sourceRefId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResultUnique)) return false;
        SessionResultUnique that = (SessionResultUnique) o;
        return delta == that.delta
                && Objects.equals(item, that.item)
                && source == that.source
                && Objects.equals(sourceNpcId, that.sourceNpcId)
                && Objects.equals(sourceCharacterId, that.sourceCharacterId)
                && Objects.equals(sourceRefId, that.sourceRefId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, source, delta, sourceNpcId, sourceCharacterId, sourceRefId);
    }

    @Override
    public String toString() {
        return "SessionResultUnique[item=" + item
                + ", source=" + source
                + ", delta=" + delta
                + ", sourceNpcId=" + sourceNpcId
                + ", sourceCharacterId=" + sourceCharacterId
                + ", sourceRefId=" + sourceRefId + "]";
    }

    public static final class Builder {
        private @Nullable ItemSnapshot item;
        private @Nullable ItemSource source;
        private long delta;
        private @Nullable Long sourceNpcId;
        private @Nullable Long sourceCharacterId;
        private @Nullable String sourceRefId;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder source(ItemSource source) {
            this.source = source;
            return this;
        }

        public Builder delta(long delta) {
            this.delta = delta;
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

        public SessionResultUnique build() {
            return new SessionResultUnique(item, source, delta, sourceNpcId, sourceCharacterId, sourceRefId);
        }
    }
}
