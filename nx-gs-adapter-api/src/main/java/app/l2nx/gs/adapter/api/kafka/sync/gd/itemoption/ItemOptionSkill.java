package app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption;

import app.l2nx.gs.adapter.api.domain.item.ItemSkillType;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A skill an item option brings. {@code type} is {@code null} when the engine token has no exact meaning in
 * {@link ItemSkillType}; {@code typeRaw} names it, never empty. {@code chancePercent} ({@code 0 < p <= 100}) is set
 * exactly for chance events and no enchant-gated type is allowed (no threshold field); only {@link Builder#build()}
 * checks that, so a type unknown to the consumer reads as {@code null} without rejecting the option.
 */
public final class ItemOptionSkill {

    private final int skillId;
    private final int skillLevel;
    private final @Nullable ItemSkillType type;
    private final String typeRaw;
    private final @Nullable Double chancePercent;

    public ItemOptionSkill(
            int skillId, int skillLevel, @Nullable ItemSkillType type, String typeRaw, @Nullable Double chancePercent) {
        if (typeRaw == null || typeRaw.trim().isEmpty()) {
            throw new IllegalArgumentException("typeRaw is required, got [" + typeRaw + "]");
        }
        if (chancePercent != null && !(chancePercent > 0d && chancePercent <= 100d)) {
            throw new IllegalArgumentException("chancePercent must be within (0, 100], got " + chancePercent);
        }
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        this.type = type;
        this.typeRaw = typeRaw;
        this.chancePercent = chancePercent;
    }

    public int getSkillId() {
        return skillId;
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    public @Nullable ItemSkillType getType() {
        return type;
    }

    public String getTypeRaw() {
        return typeRaw;
    }

    public @Nullable Double getChancePercent() {
        return chancePercent;
    }

    public Builder toBuilder() {
        return new Builder()
                .skillId(skillId)
                .skillLevel(skillLevel)
                .type(type)
                .typeRaw(typeRaw)
                .chancePercent(chancePercent);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemOptionSkill)) return false;
        ItemOptionSkill that = (ItemOptionSkill) o;
        return skillId == that.skillId
                && skillLevel == that.skillLevel
                && type == that.type
                && Objects.equals(typeRaw, that.typeRaw)
                && Objects.equals(chancePercent, that.chancePercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId, skillLevel, type, typeRaw, chancePercent);
    }

    @Override
    public String toString() {
        return "ItemOptionSkill[skillId=" + skillId + ", skillLevel=" + skillLevel + ", type=" + type + ", typeRaw="
                + typeRaw + ", chancePercent=" + chancePercent + "]";
    }

    public static final class Builder {
        private int skillId;
        private int skillLevel;
        private @Nullable ItemSkillType type;
        private @Nullable String typeRaw;
        private @Nullable Double chancePercent;

        public Builder skillId(int skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder skillLevel(int skillLevel) {
            this.skillLevel = skillLevel;
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

        public Builder chancePercent(@Nullable Double chancePercent) {
            this.chancePercent = chancePercent;
            return this;
        }

        public ItemOptionSkill build() {
            if (type != null) {
                if (type.enchantGated()) {
                    throw new IllegalArgumentException(
                            "an item option cannot carry an enchant threshold, type=" + type);
                }
                if (type.chanceEvent() != (chancePercent != null)) {
                    throw new IllegalArgumentException("chancePercent is required exactly for a chance event, type="
                            + type + ", chancePercent=" + chancePercent);
                }
            }
            return new ItemOptionSkill(skillId, skillLevel, type, typeRaw, chancePercent);
        }
    }
}
