package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.BuyFromPrivateStoreCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.BuyFromPrivateStoreResult;
import java.util.Objects;

/**
 * Echo of an executed {@link BuyFromPrivateStoreCommand} lot. Purchases are all-or-nothing, so count and price
 * always equal the request.
 */
public final class BoughtLine {

    private final ItemSnapshot item;
    private final long count;
    private final long unitPriceAdena;

    public BoughtLine(ItemSnapshot item, long count, long unitPriceAdena) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
        this.item = item;
        this.count = count;
        this.unitPriceAdena = unitPriceAdena;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    public long getCount() {
        return count;
    }

    /** Excludes the burned buyer fee, reported once per deal on {@link BuyFromPrivateStoreResult#getBuyerFeeAdena()}. */
    public long getUnitPriceAdena() {
        return unitPriceAdena;
    }

    public Builder toBuilder() {
        return new Builder().item(item).count(count).unitPriceAdena(unitPriceAdena);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoughtLine)) return false;
        BoughtLine that = (BoughtLine) o;
        return count == that.count && unitPriceAdena == that.unitPriceAdena && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, count, unitPriceAdena);
    }

    @Override
    public String toString() {
        return "BoughtLine[item=" + item + ", count=" + count + ", unitPriceAdena=" + unitPriceAdena + "]";
    }

    public static final class Builder {
        private ItemSnapshot item;
        private long count;
        private long unitPriceAdena;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder unitPriceAdena(long unitPriceAdena) {
            this.unitPriceAdena = unitPriceAdena;
            return this;
        }

        public BoughtLine build() {
            return new BoughtLine(item, count, unitPriceAdena);
        }
    }
}
