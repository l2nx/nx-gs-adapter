package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn;

import java.util.Objects;

/** A skill at a given level, required by a {@link SkillLearn} entry. */
public final class SkillLevelRef {

    private final int skillId;
    private final int skillLevel;

    public SkillLevelRef(int skillId, int skillLevel) {
        this.skillId = skillId;
        this.skillLevel = skillLevel;
    }

    public int getSkillId() {
        return skillId;
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    public Builder toBuilder() {
        return new Builder().skillId(skillId).skillLevel(skillLevel);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillLevelRef)) return false;
        SkillLevelRef that = (SkillLevelRef) o;
        return skillId == that.skillId && skillLevel == that.skillLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId, skillLevel);
    }

    @Override
    public String toString() {
        return "SkillLevelRef[" + "skillId=" + skillId + ", skillLevel=" + skillLevel + "]";
    }

    public static final class Builder {
        private int skillId;
        private int skillLevel;

        public Builder skillId(int skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder skillLevel(int skillLevel) {
            this.skillLevel = skillLevel;
            return this;
        }

        public SkillLevelRef build() {
            return new SkillLevelRef(skillId, skillLevel);
        }
    }
}
