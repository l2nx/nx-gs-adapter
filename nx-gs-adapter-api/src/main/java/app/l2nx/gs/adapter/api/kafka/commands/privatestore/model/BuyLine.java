package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.BuyFromPrivateStoreCommand;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One lot of a {@link BuyFromPrivateStoreCommand}. The host resolves it by {@code item.itemId}, then re-verifies
 * template, enchant, {@link #attributesForMatch()} and {@code unitPriceAdena} as an optimistic lock: any mismatch fails
 * the whole command with {@code OFFER_CHANGED}, no partial fills. {@code null} enchant is part of the match;
 * {@code item.count} is not, the lot size is {@code count}.
 */
public final class BuyLine {

    private final ItemSnapshot item;
    private final long count;
    private final long unitPriceAdena;

    public BuyLine(ItemSnapshot item, long count, long unitPriceAdena) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive (got " + count + ")");
        }
        if (unitPriceAdena < 0L) {
            throw new IllegalArgumentException("unitPriceAdena must be non-negative (got " + unitPriceAdena + ")");
        }
        try {
            Math.multiplyExact(count, unitPriceAdena);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "count * unitPriceAdena overflows a long (count=" + count + ", unitPriceAdena=" + unitPriceAdena
                            + ")",
                    e);
        }
        Integer enchantLevel = item.getEnchantLevel();
        if (enchantLevel != null && (enchantLevel < 0 || enchantLevel > 127)) {
            throw new IllegalArgumentException("enchantLevel must be in 0..127 (got " + enchantLevel + ")");
        }
        this.item = item;
        this.count = count;
        this.unitPriceAdena = unitPriceAdena;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    /**
     * {@code item.attributes} with an empty map as {@code null}: both mean the offer carried none. Not a getter, so a
     * getter-based serializer keeps it off the wire.
     */
    public @Nullable Map<Attribute, Integer> attributesForMatch() {
        Map<Attribute, Integer> attributes = item.getAttributes();
        return attributes == null || attributes.isEmpty() ? null : attributes;
    }

    /** The host buys exactly this many or fails; it never shrinks the count. */
    public long getCount() {
        return count;
    }

    /** Must match the live lot exactly. */
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
        if (!(o instanceof BuyLine)) return false;
        BuyLine that = (BuyLine) o;
        return count == that.count && unitPriceAdena == that.unitPriceAdena && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, count, unitPriceAdena);
    }

    @Override
    public String toString() {
        return "BuyLine[item=" + item + ", count=" + count + ", unitPriceAdena=" + unitPriceAdena + "]";
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

        public BuyLine build() {
            return new BuyLine(item, count, unitPriceAdena);
        }
    }
}
