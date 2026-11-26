package app.l2nx.gs.adapter.api.kafka.sync.gd.itemtemplate;

import app.l2nx.gs.adapter.api.domain.item.ItemSkillType;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A skill an item template brings. {@code type} is {@code null} when the engine token has no exact meaning in
 * {@link ItemSkillType}; {@code typeRaw} is that token, never empty. {@code minEnchantLevel} is set exactly for
 * {@link ItemSkillType#enchantGated()}, {@code chancePercent} ({@code [0, 100]}) exactly for
 * {@link ItemSkillType#chanceEvent()}; only {@link Builder#build()} checks that, so an unknown type reads as
 * {@code null} without rejecting the item.
 */
public final class ItemSkillRef {

    private final int id;
    private final int level;
    private final @Nullable ItemSkillType type;
    private final String typeRaw;
    private final @Nullable Integer minEnchantLevel;
    private final @Nullable Integer chancePercent;

    public ItemSkillRef(
            int id,
            int level,
            @Nullable ItemSkillType type,
            String typeRaw,
            @Nullable Integer minEnchantLevel,
            @Nullable Integer chancePercent) {
        if (typeRaw == null || typeRaw.trim().isEmpty()) {
            throw new IllegalArgumentException("typeRaw is required, got [" + typeRaw + "]");
        }
        if (chancePercent != null && (chancePercent < 0 || chancePercent > 100)) {
            throw new IllegalArgumentException("chancePercent must be within [0, 100], got " + chancePercent);
        }
        this.id = id;
        this.level = level;
        this.type = type;
        this.typeRaw = typeRaw;
        this.minEnchantLevel = minEnchantLevel;
        this.chancePercent = chancePercent;
    }

    public int getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    public @Nullable ItemSkillType getType() {
        return type;
    }

    public String getTypeRaw() {
        return typeRaw;
    }

    public @Nullable Integer getMinEnchantLevel() {
        return minEnchantLevel;
    }

    public @Nullable Integer getChancePercent() {
        return chancePercent;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .level(level)
                .type(type)
                .typeRaw(typeRaw)
                .minEnchantLevel(minEnchantLevel)
                .chancePercent(chancePercent);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemSkillRef)) return false;
        ItemSkillRef that = (ItemSkillRef) o;
        return id == that.id
                && level == that.level
                && type == that.type
                && Objects.equals(typeRaw, that.typeRaw)
                && Objects.equals(minEnchantLevel, that.minEnchantLevel)
                && Objects.equals(chancePercent, that.chancePercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, type, typeRaw, minEnchantLevel, chancePercent);
    }

    @Override
    public String toString() {
        return "ItemSkillRef[id=" + id + ", level=" + level + ", type=" + type + ", typeRaw=" + typeRaw
                + ", minEnchantLevel=" + minEnchantLevel + ", chancePercent=" + chancePercent + "]";
    }

    public static final class Builder {
        private int id;
        private int level;
        private @Nullable ItemSkillType type;
        private @Nullable String typeRaw;
        private @Nullable Integer minEnchantLevel;
        private @Nullable Integer chancePercent;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder type(@Nullable ItemSkillType type) {
            this.type = type;
            return this;
        }

        public Builder typeRaw(String typeRaw) {
            this.typeRaw = typeRaw;
            return this;
        }

        public Builder minEnchantLevel(@Nullable Integer minEnchantLevel) {
            this.minEnchantLevel = minEnchantLevel;
            return this;
        }

        public Builder chancePercent(@Nullable Integer chancePercent) {
            this.chancePercent = chancePercent;
            return this;
        }

        public ItemSkillRef build() {
            if (type != null) {
                if (type.chanceEvent() != (chancePercent != null)) {
                    throw new IllegalArgumentException("chancePercent is required exactly for a chance event, type="
                            + type + ", chancePercent=" + chancePercent);
                }
                if (type.enchantGated() != (minEnchantLevel != null)) {
                    throw new IllegalArgumentException("minEnchantLevel is required exactly for an enchant-gated type,"
                            + " type=" + type + ", minEnchantLevel=" + minEnchantLevel);
                }
            }
            return new ItemSkillRef(id, level, type, typeRaw, minEnchantLevel, chancePercent);
        }
    }
}
