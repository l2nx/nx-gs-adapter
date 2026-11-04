package app.l2nx.gs.adapter.api.kafka.events.privatetrade;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Objects;

/**
 * Transferred item line in a {@link PrivateTradeFinishedEvent}; adena is a regular line (no separate price/currency
 * field). {@code item.itemId} is the giver-side inventory object id before the exchange.
 */
public final class TradeItemMovement {

    private final ItemSnapshot item;
    private final long newItemId;
    private final long count;

    public TradeItemMovement(ItemSnapshot item, long newItemId, long count) {
        this.item = Objects.requireNonNull(item, "item");
        this.newItemId = newItemId;
        this.count = count;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    /** Receiver-side inventory object id after the exchange; on a stack merge, the stack that absorbed the item. */
    public long getNewItemId() {
        return newItemId;
    }

    /** Quantity this line moved, not the stack size of {@code item}. */
    public long getCount() {
        return count;
    }

    public Builder toBuilder() {
        return new Builder().item(item).newItemId(newItemId).count(count);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeItemMovement)) return false;
        TradeItemMovement that = (TradeItemMovement) o;
        return newItemId == that.newItemId && count == that.count && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, newItemId, count);
    }

    @Override
    public String toString() {
        return "TradeItemMovement[item=" + item + ", newItemId=" + newItemId + ", count=" + count + "]";
    }

    public static final class Builder {
        private ItemSnapshot item;
        private long newItemId;
        private long count;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder newItemId(long newItemId) {
            this.newItemId = newItemId;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public TradeItemMovement build() {
            return new TradeItemMovement(item, newItemId, count);
        }
    }
}
