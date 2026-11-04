package app.l2nx.gs.adapter.api.kafka.commands.item;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.item.model.WellKnownItemCommandReasons;
import java.util.Objects;

/**
 * Moves a stack between characters by item-instance object-id; non-stackables require {@code count == 1}. Always refused
 * with {@code INVALID_STATE}: {@link WellKnownItemCommandReasons#ITEM_IN_STORE ITEM_IN_STORE}, {@link WellKnownItemCommandReasons#ITEM_IN_USE ITEM_IN_USE}. Gson bypasses
 * the constructor, so the handler checks {@code VALIDATION_FAILED}.
 *
 * <p>Routed by {@code fromCharacterId}, which does NOT serialize against commands keyed by {@code toCharacterId}; delivery is
 * at-most-once, so after a reply timeout the caller decides whether the transfer landed.</p>
 */
public final class TransferItemToCharacterCommand implements NxCommand<TransferItemToCharacterResult> {

    private final Long fromCharacterId;
    private final Long toCharacterId;
    private final Long itemId;
    private final Long count;
    private final CommandInitiator initiator;

    public TransferItemToCharacterCommand(
            Long fromCharacterId, Long toCharacterId, Long itemId, Long count, CommandInitiator initiator) {
        if (fromCharacterId == null) {
            throw new IllegalArgumentException("fromCharacterId is required");
        }
        if (toCharacterId == null) {
            throw new IllegalArgumentException("toCharacterId is required");
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
        this.fromCharacterId = fromCharacterId;
        this.toCharacterId = toCharacterId;
        this.itemId = itemId;
        this.count = count;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getFromCharacterId() {
        return fromCharacterId;
    }

    public Long getToCharacterId() {
        return toCharacterId;
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
        return new Builder()
                .fromCharacterId(fromCharacterId)
                .toCharacterId(toCharacterId)
                .itemId(itemId)
                .count(count)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferItemToCharacterCommand)) return false;
        TransferItemToCharacterCommand that = (TransferItemToCharacterCommand) o;
        return Objects.equals(fromCharacterId, that.fromCharacterId)
                && Objects.equals(toCharacterId, that.toCharacterId)
                && Objects.equals(itemId, that.itemId)
                && Objects.equals(count, that.count)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromCharacterId, toCharacterId, itemId, count, initiator);
    }

    @Override
    public String toString() {
        return "TransferItemToCharacterCommand[fromCharacterId=" + fromCharacterId
                + ", toCharacterId=" + toCharacterId
                + ", itemId=" + itemId
                + ", count=" + count + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long fromCharacterId;
        private Long toCharacterId;
        private Long itemId;
        private Long count = 1L;
        private CommandInitiator initiator;

        public Builder fromCharacterId(Long fromCharacterId) {
            this.fromCharacterId = fromCharacterId;
            return this;
        }

        public Builder toCharacterId(Long toCharacterId) {
            this.toCharacterId = toCharacterId;
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

        public TransferItemToCharacterCommand build() {
            return new TransferItemToCharacterCommand(fromCharacterId, toCharacterId, itemId, count, initiator);
        }
    }
}
