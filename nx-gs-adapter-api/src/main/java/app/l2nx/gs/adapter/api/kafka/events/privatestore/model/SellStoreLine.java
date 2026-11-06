package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Objects;

/** A line of a SELL / PACKAGE_SELL store: a concrete instance on offer. */
public final class SellStoreLine {

    private final ItemSnapshot item;
    private final long count;
    private final long unitPrice;
    private final long currencyItemTemplateId;

    public SellStoreLine(ItemSnapshot item, long count, long unitPrice, long currencyItemTemplateId) {
        this.item = Objects.requireNonNull(item, "item");
        this.count = count;
        this.unitPrice = unitPrice;
        this.currencyItemTemplateId = currencyItemTemplateId;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    /** Units still on offer; always {@code > 0}, exhausted lines are not sent. */
    public long getCount() {
        return count;
    }

    /** Price per unit; in a PACKAGE_SELL store it is nominal, the real price is the bundle total. */
    public long getUnitPrice() {
        return unitPrice;
    }

    /** Currency item template id; Adena is {@code 57}. */
    public long getCurrencyItemTemplateId() {
        return currencyItemTemplateId;
    }

    public Builder toBuilder() {
        return new Builder()
                .item(item)
                .count(count)
                .unitPrice(unitPrice)
                .currencyItemTemplateId(currencyItemTemplateId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellStoreLine)) return false;
        SellStoreLine that = (SellStoreLine) o;
        return count == that.count
                && unitPrice == that.unitPrice
                && currencyItemTemplateId == that.currencyItemTemplateId
                && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, count, unitPrice, currencyItemTemplateId);
    }

    @Override
    public String toString() {
        return "SellStoreLine[item=" + item
                + ", count=" + count
                + ", unitPrice=" + unitPrice
                + ", currencyItemTemplateId=" + currencyItemTemplateId + "]";
    }

    public static final class Builder {
        private ItemSnapshot item;
        private long count;
        private long unitPrice;
        private long currencyItemTemplateId;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder unitPrice(long unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder currencyItemTemplateId(long currencyItemTemplateId) {
            this.currencyItemTemplateId = currencyItemTemplateId;
            return this;
        }

        public SellStoreLine build() {
            return new SellStoreLine(item, count, unitPrice, currencyItemTemplateId);
        }
    }
}
