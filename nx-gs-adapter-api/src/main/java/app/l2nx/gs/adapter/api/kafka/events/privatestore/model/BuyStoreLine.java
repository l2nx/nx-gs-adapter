package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.domain.Attribute;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A line of a BUY store: a wanted template, no instance. */
public final class BuyStoreLine {

    private final long itemTemplateId;
    private final @Nullable Integer enchantLevel;
    private final @Nullable Map<Attribute, Integer> attributes;
    private final long count;
    private final long unitPrice;
    private final long currencyItemTemplateId;

    public BuyStoreLine(
            long itemTemplateId,
            @Nullable Integer enchantLevel,
            @Nullable Map<Attribute, Integer> attributes,
            long count,
            long unitPrice,
            long currencyItemTemplateId) {
        this.itemTemplateId = itemTemplateId;
        this.enchantLevel = enchantLevel;
        this.attributes = freezeMap(attributes);
        this.count = count;
        this.unitPrice = unitPrice;
        this.currencyItemTemplateId = currencyItemTemplateId;
    }

    public long getItemTemplateId() {
        return itemTemplateId;
    }

    /** {@code null} = no enchant concept; {@code 0} = unenchanted; {@code > 0} = enchanted. */
    public @Nullable Integer getEnchantLevel() {
        return enchantLevel;
    }

    public Map<Attribute, Integer> getAttributes() {
        return attributes == null ? Collections.emptyMap() : attributes;
    }

    /** Units still wanted; always {@code > 0}, exhausted lines are not sent. */
    public long getCount() {
        return count;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    /** Currency item template id; Adena is {@code 57}. */
    public long getCurrencyItemTemplateId() {
        return currencyItemTemplateId;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemTemplateId(itemTemplateId)
                .enchantLevel(enchantLevel)
                .attributes(attributes)
                .count(count)
                .unitPrice(unitPrice)
                .currencyItemTemplateId(currencyItemTemplateId);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static @Nullable Map<Attribute, Integer> freezeMap(@Nullable Map<Attribute, Integer> src) {
        if (src == null || src.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new EnumMap<Attribute, Integer>(src));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyStoreLine)) return false;
        BuyStoreLine that = (BuyStoreLine) o;
        return itemTemplateId == that.itemTemplateId
                && count == that.count
                && unitPrice == that.unitPrice
                && currencyItemTemplateId == that.currencyItemTemplateId
                && Objects.equals(enchantLevel, that.enchantLevel)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, enchantLevel, attributes, count, unitPrice, currencyItemTemplateId);
    }

    @Override
    public String toString() {
        return "BuyStoreLine[itemTemplateId=" + itemTemplateId
                + ", enchantLevel=" + enchantLevel
                + ", attributes=" + attributes
                + ", count=" + count
                + ", unitPrice=" + unitPrice
                + ", currencyItemTemplateId=" + currencyItemTemplateId + "]";
    }

    public static final class Builder {
        private long itemTemplateId;
        private @Nullable Integer enchantLevel;
        private @Nullable Map<Attribute, Integer> attributes;
        private long count;
        private long unitPrice;
        private long currencyItemTemplateId;

        public Builder itemTemplateId(long itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder enchantLevel(@Nullable Integer enchantLevel) {
            this.enchantLevel = enchantLevel;
            return this;
        }

        public Builder attributes(@Nullable Map<Attribute, Integer> attributes) {
            this.attributes = attributes;
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

        public BuyStoreLine build() {
            return new BuyStoreLine(itemTemplateId, enchantLevel, attributes, count, unitPrice, currencyItemTemplateId);
        }
    }
}
