package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStorePurchaseEvent;
import java.util.Objects;

/** One transferred position in a closed {@link PrivateStorePurchaseEvent}; {@code item} is the item that moved. */
public final class TradeLine {

    private final ItemSnapshot item;
    private final long count;
    private final long unitPrice;
    private final long currencyItemTemplateId;

    public TradeLine(ItemSnapshot item, long count, long unitPrice, long currencyItemTemplateId) {
        this.item = Objects.requireNonNull(item, "item");
        this.count = count;
        this.unitPrice = unitPrice;
        this.currencyItemTemplateId = currencyItemTemplateId;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    public long getCount() {
        return count;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    /** Currency item template id; differs from Adena ({@code 57}) for alt-currency stores. */
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
        if (!(o instanceof TradeLine)) return false;
        TradeLine that = (TradeLine) o;
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
        return "TradeLine[item=" + item
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

        public TradeLine build() {
            return new TradeLine(item, count, unitPrice, currencyItemTemplateId);
        }
    }
}
