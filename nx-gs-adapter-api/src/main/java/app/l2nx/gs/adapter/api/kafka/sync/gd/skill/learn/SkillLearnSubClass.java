package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn;

import java.util.Objects;

/** Sub-class certification requirement of a {@link SkillLearn} entry. */
public final class SkillLearnSubClass {

    private final int slot;
    private final int level;

    public SkillLearnSubClass(int slot, int level) {
        this.slot = slot;
        this.level = level;
    }

    /** Sub-class slot index, 1-based. */
    public int getSlot() {
        return slot;
    }

    public int getLevel() {
        return level;
    }

    public Builder toBuilder() {
        return new Builder().slot(slot).level(level);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillLearnSubClass)) return false;
        SkillLearnSubClass that = (SkillLearnSubClass) o;
        return slot == that.slot && level == that.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, level);
    }

    @Override
    public String toString() {
        return "SkillLearnSubClass[" + "slot=" + slot + ", level=" + level + "]";
    }

    public static final class Builder {
        private int slot;
        private int level;

        public Builder slot(int slot) {
            this.slot = slot;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public SkillLearnSubClass build() {
            return new SkillLearnSubClass(slot, level);
        }
    }
}
