package app.l2nx.gs.adapter.api.kafka.events.mail;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.privatetrade.TradeItemMovement;
import java.util.Objects;

/**
 * One item-movement line of a mail-lifecycle event; mirrors
 * {@link TradeItemMovement}.
 *
 * <p>{@code item.itemId} / {@code newItemId} are the object-id transition. SENT and RETURNED have none (both equal the
 * attachment row id); ACCEPTED goes mail row to receiver inventory, CANCELLED to sender inventory. A stack merge
 * collapses {@code newItemId} onto the existing stack id.</p>
 */
public final class MailItemMovement {

    private final ItemSnapshot item;
    private final long newItemId;
    private final long count;

    public MailItemMovement(ItemSnapshot item, long newItemId, long count) {
        this.item = Objects.requireNonNull(item, "item");
        this.newItemId = newItemId;
        this.count = count;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    public long getNewItemId() {
        return newItemId;
    }

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
        if (!(o instanceof MailItemMovement)) return false;
        MailItemMovement that = (MailItemMovement) o;
        return newItemId == that.newItemId && count == that.count && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, newItemId, count);
    }

    @Override
    public String toString() {
        return "MailItemMovement[item=" + item + ", newItemId=" + newItemId + ", count=" + count + "]";
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

        public MailItemMovement build() {
            return new MailItemMovement(item, newItemId, count);
        }
    }
}
