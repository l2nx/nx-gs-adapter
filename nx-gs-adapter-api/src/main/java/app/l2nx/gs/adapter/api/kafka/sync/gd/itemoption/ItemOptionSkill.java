package app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption;

import app.l2nx.gs.adapter.api.domain.item.ItemOptionTrigger;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A skill an item option brings: without a {@code trigger} it is granted while the item is equipped (active or passive
 * per the skill's {@code operateType}), else cast with {@code chancePercent} (0 &lt; p &lt;= 100) on that event.
 * {@link Builder#build()} requires a chance exactly when a trigger is set; the constructor checks only the range, so a
 * trigger unknown to the consumer reads as {@code null} and leaves a chance without a trigger.
 */
public final class ItemOptionSkill {

    private final int skillId;
    private final int skillLevel;
    private final @Nullable ItemOptionTrigger trigger;
    private final @Nullable Double chancePercent;

    public ItemOptionSkill(
            int skillId, int skillLevel, @Nullable ItemOptionTrigger trigger, @Nullable Double chancePercent) {
        if (chancePercent != null && !(chancePercent > 0d && chancePercent <= 100d)) {
            throw new IllegalArgumentException("chancePercent must be within (0, 100], got " + chancePercent);
        }
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        this.trigger = trigger;
        this.chancePercent = chancePercent;
    }

    public int getSkillId() {
        return skillId;
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    public @Nullable ItemOptionTrigger getTrigger() {
        return trigger;
    }

    public @Nullable Double getChancePercent() {
        return chancePercent;
    }

    public Builder toBuilder() {
        return new Builder()
                .skillId(skillId)
                .skillLevel(skillLevel)
                .trigger(trigger)
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
                && trigger == that.trigger
                && Objects.equals(chancePercent, that.chancePercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId, skillLevel, trigger, chancePercent);
    }

    @Override
    public String toString() {
        return "ItemOptionSkill[skillId=" + skillId + ", skillLevel=" + skillLevel + ", trigger=" + trigger
                + ", chancePercent=" + chancePercent + "]";
    }

    public static final class Builder {
        private int skillId;
        private int skillLevel;
        private @Nullable ItemOptionTrigger trigger;
        private @Nullable Double chancePercent;

        public Builder skillId(int skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder skillLevel(int skillLevel) {
            this.skillLevel = skillLevel;
            return this;
        }

        public Builder trigger(@Nullable ItemOptionTrigger trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder chancePercent(@Nullable Double chancePercent) {
            this.chancePercent = chancePercent;
            return this;
        }

        public ItemOptionSkill build() {
            if ((trigger != null) != (chancePercent != null)) {
                throw new IllegalArgumentException("chancePercent is required with a trigger and only with it, trigger="
                        + trigger + ", chancePercent=" + chancePercent);
            }
            return new ItemOptionSkill(skillId, skillLevel, trigger, chancePercent);
        }
    }
}
