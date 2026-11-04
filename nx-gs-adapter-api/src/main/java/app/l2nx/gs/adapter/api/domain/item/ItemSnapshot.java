package app.l2nx.gs.adapter.api.domain.item;

import app.l2nx.gs.adapter.api.domain.Attribute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A concrete item as it was at the moment of an event. Location, owner and the quantity an event moved stay in the
 * event around it. Gson bypasses the constructor, so a consumer re-checks what it depends on.
 */
public final class ItemSnapshot {

    private final long itemId;
    private final long itemTemplateId;
    private final @Nullable Long count;
    private final @Nullable Integer enchantLevel;
    private final @Nullable Map<Attribute, Integer> attributes;
    private final @Nullable ItemAugmentation augmentation;

    public ItemSnapshot(
            long itemId,
            long itemTemplateId,
            @Nullable Long count,
            @Nullable Integer enchantLevel,
            @Nullable Map<Attribute, Integer> attributes,
            @Nullable ItemAugmentation augmentation) {
        if (itemId <= 0L) {
            throw new IllegalArgumentException("itemId must be positive, got " + itemId);
        }
        if (itemTemplateId <= 0L) {
            throw new IllegalArgumentException("itemTemplateId must be positive, got " + itemTemplateId);
        }
        this.itemId = itemId;
        this.itemTemplateId = itemTemplateId;
        this.count = count;
        this.enchantLevel = enchantLevel;
        this.attributes = attributes == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<Attribute, Integer>(attributes));
        this.augmentation = augmentation;
    }

    /** Instance object id, NOT the catalog template id. */
    public long getItemId() {
        return itemId;
    }

    public long getItemTemplateId() {
        return itemTemplateId;
    }

    /**
     * Stack size at the moment of the snapshot, not the quantity the event moved; {@code null} when the host does not
     * report it. Never backfill it from a later inventory read.
     */
    public @Nullable Long getCount() {
        return count;
    }

    /** {@code null} = no enchant concept; {@code 0} = unenchanted. */
    public @Nullable Integer getEnchantLevel() {
        return enchantLevel;
    }

    /** Element to power; {@code null} = not reported, empty = none. Gson omits {@code null}, so the wire keeps both. */
    public @Nullable Map<Attribute, Integer> getAttributes() {
        return attributes;
    }

    /** {@code null} = not augmented. */
    public @Nullable ItemAugmentation getAugmentation() {
        return augmentation;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemId(itemId)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .enchantLevel(enchantLevel)
                .attributes(attributes)
                .augmentation(augmentation);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemSnapshot)) return false;
        ItemSnapshot that = (ItemSnapshot) o;
        return itemId == that.itemId
                && itemTemplateId == that.itemTemplateId
                && Objects.equals(count, that.count)
                && Objects.equals(enchantLevel, that.enchantLevel)
                && Objects.equals(attributes, that.attributes)
                && Objects.equals(augmentation, that.augmentation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, itemTemplateId, count, enchantLevel, attributes, augmentation);
    }

    @Override
    public String toString() {
        return "ItemSnapshot[itemId=" + itemId
                + ", itemTemplateId=" + itemTemplateId
                + ", count=" + count
                + ", enchantLevel=" + enchantLevel
                + ", attributes=" + attributes
                + ", augmentation=" + augmentation + "]";
    }

    public static final class Builder {
        private long itemId;
        private long itemTemplateId;
        private @Nullable Long count;
        private @Nullable Integer enchantLevel;
        private @Nullable Map<Attribute, Integer> attributes;
        private @Nullable ItemAugmentation augmentation;

        public Builder itemId(long itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder itemTemplateId(long itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder count(@Nullable Long count) {
            this.count = count;
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

        public Builder augmentation(@Nullable ItemAugmentation augmentation) {
            this.augmentation = augmentation;
            return this;
        }

        public ItemSnapshot build() {
            return new ItemSnapshot(itemId, itemTemplateId, count, enchantLevel, attributes, augmentation);
        }
    }
}
