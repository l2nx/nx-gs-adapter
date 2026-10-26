package app.l2nx.gs.adapter.api.kafka.commands.item;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Deletes {@code count} items from a stack by item-instance object-id. Always refused with {@code INVALID_STATE}:
 * {@link app.l2nx.gs.adapter.api.kafka.commands.item.model.WellKnownItemCommandReasons#ITEM_IN_STORE ITEM_IN_STORE}, {@link app.l2nx.gs.adapter.api.kafka.commands.item.model.WellKnownItemCommandReasons#ITEM_IN_USE ITEM_IN_USE}. Gson bypasses the constructor, so the
 * handler checks {@code VALIDATION_FAILED}; delivery is at-most-once, so after a reply timeout the caller decides whether the delete landed.
 */
public final class DeleteItemCommand implements NxCommand<DeleteItemResult> {

    private final Long charId;
    private final Long itemId;
    private final Long count;
    private final CommandInitiator initiator;

    public DeleteItemCommand(Long charId, Long itemId, Long count, CommandInitiator initiator) {
        if (charId == null) {
            throw new IllegalArgumentException("charId is required");
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
        this.charId = charId;
        this.itemId = itemId;
        this.count = count;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharId() {
        return charId;
    }

    /** Per-instance object-id, NOT the catalog item-template id. */
    public Long getItemId() {
        return itemId;
    }

    public Long getCount() {
        return count;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder().charId(charId).itemId(itemId).count(count).initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeleteItemCommand)) return false;
        DeleteItemCommand that = (DeleteItemCommand) o;
        return Objects.equals(charId, that.charId)
                && Objects.equals(itemId, that.itemId)
                && Objects.equals(count, that.count)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(charId, itemId, count, initiator);
    }

    @Override
    public String toString() {
        return "DeleteItemCommand[charId=" + charId + ", itemId=" + itemId + ", count=" + count + ", initiator="
                + initiator + "]";
    }

    public static final class Builder {
        private Long charId;
        private Long itemId;
        private Long count = 1L;
        private CommandInitiator initiator;

        public Builder charId(Long charId) {
            this.charId = charId;
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

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public DeleteItemCommand build() {
            return new DeleteItemCommand(charId, itemId, count, initiator);
        }
    }
}
