package app.l2nx.gs.adapter.api.kafka.commands.item;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Moves a stack between characters by item-instance object-id; non-stackables require {@code count == 1}. Always refused
 * with {@code INVALID_STATE}: {@link app.l2nx.gs.adapter.api.kafka.commands.item.model.WellKnownItemCommandReasons#ITEM_IN_STORE ITEM_IN_STORE}, {@link app.l2nx.gs.adapter.api.kafka.commands.item.model.WellKnownItemCommandReasons#ITEM_IN_USE ITEM_IN_USE}. Gson bypasses
 * the constructor, so the handler checks {@code VALIDATION_FAILED}.
 *
 * <p>Routed by {@code charIdFrom}, which does NOT serialize against commands keyed by {@code charIdTo}; delivery is
 * at-most-once, so after a reply timeout the caller decides whether the transfer landed.</p>
 */
public final class TransferItemToCharacterCommand implements NxCommand<TransferItemToCharacterResult> {

    private final Long charIdFrom;
    private final Long charIdTo;
    private final Long itemId;
    private final Long count;

    public TransferItemToCharacterCommand(Long charIdFrom, Long charIdTo, Long itemId, Long count) {
        if (charIdFrom == null) {
            throw new IllegalArgumentException("charIdFrom is required");
        }
        if (charIdTo == null) {
            throw new IllegalArgumentException("charIdTo is required");
        }
        if (itemId == null) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (count == null) {
            throw new IllegalArgumentException("count is required");
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive (got " + count + ")");
        }
        this.charIdFrom = charIdFrom;
        this.charIdTo = charIdTo;
        this.itemId = itemId;
        this.count = count;
    }

    public Long getCharIdFrom() {
        return charIdFrom;
    }

    public Long getCharIdTo() {
        return charIdTo;
    }

    /** Per-instance object-id, NOT the catalog item-template id. */
    public Long getItemId() {
        return itemId;
    }

    public Long getCount() {
        return count;
    }

    public Builder toBuilder() {
        return new Builder()
                .charIdFrom(charIdFrom)
                .charIdTo(charIdTo)
                .itemId(itemId)
                .count(count);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferItemToCharacterCommand)) return false;
        TransferItemToCharacterCommand that = (TransferItemToCharacterCommand) o;
        return Objects.equals(charIdFrom, that.charIdFrom)
                && Objects.equals(charIdTo, that.charIdTo)
                && Objects.equals(itemId, that.itemId)
                && Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(charIdFrom, charIdTo, itemId, count);
    }

    @Override
    public String toString() {
        return "TransferItemToCharacterCommand[charIdFrom=" + charIdFrom
                + ", charIdTo=" + charIdTo
                + ", itemId=" + itemId
                + ", count=" + count + "]";
    }

    public static final class Builder {
        private Long charIdFrom;
        private Long charIdTo;
        private Long itemId;
        private Long count = 1L;

        public Builder charIdFrom(Long charIdFrom) {
            this.charIdFrom = charIdFrom;
            return this;
        }

        public Builder charIdTo(Long charIdTo) {
            this.charIdTo = charIdTo;
            return this;
        }

        public Builder itemId(Long itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder count(Long count) {
            this.count = count;
            return this;
        }

        public TransferItemToCharacterCommand build() {
            return new TransferItemToCharacterCommand(charIdFrom, charIdTo, itemId, count);
        }
    }
}
