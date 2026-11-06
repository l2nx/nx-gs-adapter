package app.l2nx.gs.adapter.api.kafka.commands.item;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Objects;

/**
 * Echoes the amount moved and both endpoints; {@code countTransferred} may be less than requested, as in {@link DeleteItemResult}.
 * {@code item} is the item before the operation.
 */
public final class TransferItemToCharacterResult {

    private final ItemSnapshot item;
    private final Long countTransferred;
    private final Long fromCharacterId;
    private final Long toCharacterId;

    public TransferItemToCharacterResult(
            ItemSnapshot item, Long countTransferred, Long fromCharacterId, Long toCharacterId) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
        if (countTransferred == null) {
            throw new IllegalArgumentException("countTransferred is required");
        }
        if (countTransferred <= 0L) {
            throw new IllegalArgumentException("countTransferred must be positive (got " + countTransferred + ")");
        }
        if (fromCharacterId == null) {
            throw new IllegalArgumentException("fromCharacterId is required");
        }
        if (toCharacterId == null) {
            throw new IllegalArgumentException("toCharacterId is required");
        }
        this.item = item;
        this.countTransferred = countTransferred;
        this.fromCharacterId = fromCharacterId;
        this.toCharacterId = toCharacterId;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    public Long getCountTransferred() {
        return countTransferred;
    }

    public Long getFromCharacterId() {
        return fromCharacterId;
    }

    public Long getToCharacterId() {
        return toCharacterId;
    }

    public Builder toBuilder() {
        return new Builder()
                .item(item)
                .countTransferred(countTransferred)
                .fromCharacterId(fromCharacterId)
                .toCharacterId(toCharacterId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferItemToCharacterResult)) return false;
        TransferItemToCharacterResult that = (TransferItemToCharacterResult) o;
        return Objects.equals(item, that.item)
                && Objects.equals(countTransferred, that.countTransferred)
                && Objects.equals(fromCharacterId, that.fromCharacterId)
                && Objects.equals(toCharacterId, that.toCharacterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, countTransferred, fromCharacterId, toCharacterId);
    }

    @Override
    public String toString() {
        return "TransferItemToCharacterResult[item=" + item
                + ", countTransferred=" + countTransferred
                + ", fromCharacterId=" + fromCharacterId
                + ", toCharacterId=" + toCharacterId + "]";
    }

    public static final class Builder {
        private ItemSnapshot item;
        private Long countTransferred;
        private Long fromCharacterId;
        private Long toCharacterId;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder countTransferred(Long countTransferred) {
            this.countTransferred = countTransferred;
            return this;
        }

        public Builder fromCharacterId(Long fromCharacterId) {
            this.fromCharacterId = fromCharacterId;
            return this;
        }

        public Builder toCharacterId(Long toCharacterId) {
            this.toCharacterId = toCharacterId;
            return this;
        }

        public TransferItemToCharacterResult build() {
            return new TransferItemToCharacterResult(item, countTransferred, fromCharacterId, toCharacterId);
        }
    }
}
