package app.l2nx.gs.adapter.api.kafka.events.raid.kill;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One drop entry of a {@link RaidKillEvent}: what the raid actually rolled, not the template drop table. A template
 * reference: no instance is known when the drop is recorded.
 */
public final class RaidDropItem {

    private final long itemTemplateId;
    private final long count;
    private final @Nullable Integer enchantLevel;

    public RaidDropItem(long itemTemplateId, long count, @Nullable Integer enchantLevel) {
        this.itemTemplateId = itemTemplateId;
        this.count = count;
        this.enchantLevel = enchantLevel;
    }

    public long getItemTemplateId() {
        return itemTemplateId;
    }

    /** {@code >= 1}; producers MUST NOT emit a zero-count drop. {@code long} because adena stacks overflow {@code int}. */
    public long getCount() {
        return count;
    }

    /** {@code null} for unenchantable items (adena, materials, recipes). */
    public @Nullable Integer getEnchantLevel() {
        return enchantLevel;
    }

    public Builder toBuilder() {
        return new Builder().itemTemplateId(itemTemplateId).count(count).enchantLevel(enchantLevel);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RaidDropItem)) return false;
        RaidDropItem that = (RaidDropItem) o;
        return itemTemplateId == that.itemTemplateId
                && count == that.count
                && Objects.equals(enchantLevel, that.enchantLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, count, enchantLevel);
    }

    @Override
    public String toString() {
        return "RaidDropItem[itemTemplateId=" + itemTemplateId + ", count=" + count + ", enchantLevel=" + enchantLevel
                + "]";
    }

    public static final class Builder {
        private long itemTemplateId;
        private long count;
        private @Nullable Integer enchantLevel;

        public Builder itemTemplateId(long itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder enchantLevel(@Nullable Integer enchantLevel) {
            this.enchantLevel = enchantLevel;
            return this;
        }

        public RaidDropItem build() {
            return new RaidDropItem(itemTemplateId, count, enchantLevel);
        }
    }
}
