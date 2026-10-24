package app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone;

import app.l2nx.gs.adapter.api.domain.item.LifeStoneGrade;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * {@code grade} is {@code null} when the engine grade has no canonical meaning or the stone has none; {@code gradeRaw}
 * is {@code null} only when the engine has no grade, so a non-null {@code grade} implies a non-null {@code gradeRaw}.
 * {@code level} and {@code minCharacterLevel} are {@code null} when the build has no such requirement.
 *
 * <p>A listed group may have no outcomes: the engine then applies an empty augmentation. Every outcome references a
 * listed group, {@code (group, slot, itemOptionId)} is unique, and chances per {@code (group, slot)} sum to at most 100
 * (within {@link #CHANCE_SUM_TOLERANCE_PERCENT}).</p>
 */
public final class LifeStone {

    public static final double CHANCE_SUM_TOLERANCE_PERCENT = 1e-6;

    private final int itemTemplateId;
    private final @Nullable LifeStoneGrade grade;
    private final @Nullable String gradeRaw;
    private final @Nullable Integer level;
    private final @Nullable Integer minCharacterLevel;
    private final List<Integer> augmentationTargetGroupIds;
    private final List<AugmentationOutcome> outcomes;

    public LifeStone(
            int itemTemplateId,
            @Nullable LifeStoneGrade grade,
            @Nullable String gradeRaw,
            @Nullable Integer level,
            @Nullable Integer minCharacterLevel,
            List<Integer> augmentationTargetGroupIds,
            List<AugmentationOutcome> outcomes) {
        if (grade != null && gradeRaw == null) {
            throw new IllegalArgumentException("gradeRaw is required when grade is set, grade=" + grade);
        }
        this.itemTemplateId = itemTemplateId;
        this.grade = grade;
        this.gradeRaw = gradeRaw;
        this.level = level;
        this.minCharacterLevel = minCharacterLevel;
        this.augmentationTargetGroupIds = Collections.unmodifiableList(new ArrayList<Integer>(
                Objects.requireNonNull(augmentationTargetGroupIds, "augmentationTargetGroupIds")));
        this.outcomes = Collections.unmodifiableList(
                new ArrayList<AugmentationOutcome>(Objects.requireNonNull(outcomes, "outcomes")));
        validate(this.augmentationTargetGroupIds, this.outcomes);
    }

    private static void validate(List<Integer> groupIds, List<AugmentationOutcome> outcomes) {
        Set<Integer> groups = new HashSet<Integer>();
        for (Integer groupId : groupIds) {
            if (!groups.add(Objects.requireNonNull(groupId, "augmentationTargetGroupIds element"))) {
                throw new IllegalArgumentException("augmentationTargetGroupIds repeats group " + groupId);
            }
        }
        // (group, slot, option) needs 65 bits, so the slot (1 or 2) picks the set and the key packs (group, option).
        Set<Long> seenInSlot1 = new HashSet<Long>();
        Set<Long> seenInSlot2 = new HashSet<Long>();
        Map<Long, double[]> sums = new HashMap<Long, double[]>();
        for (AugmentationOutcome outcome : outcomes) {
            Objects.requireNonNull(outcome, "outcomes element");
            int groupId = outcome.getAugmentationTargetGroupId();
            int slot = outcome.getSlot();
            if (!groups.contains(groupId)) {
                throw new IllegalArgumentException(
                        "outcome references group " + groupId + " missing from augmentationTargetGroupIds");
            }
            Set<Long> seen = slot == 1 ? seenInSlot1 : seenInSlot2;
            if (!seen.add(pack(groupId, outcome.getItemOptionId()))) {
                throw new IllegalArgumentException("outcome repeats option " + outcome.getItemOptionId()
                        + " in group/slot " + groupId + "/" + slot);
            }
            long slotKey = pack(groupId, slot);
            double[] sum = sums.get(slotKey);
            if (sum == null) {
                sum = new double[1];
                sums.put(slotKey, sum);
            }
            sum[0] += outcome.getChancePercent();
            if (sum[0] > 100d + CHANCE_SUM_TOLERANCE_PERCENT) {
                throw new IllegalArgumentException(
                        "chances of group/slot " + groupId + "/" + slot + " exceed 100: " + sum[0]);
            }
        }
    }

    private static long pack(int high, int low) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    public int getItemTemplateId() {
        return itemTemplateId;
    }

    public @Nullable LifeStoneGrade getGrade() {
        return grade;
    }

    public @Nullable String getGradeRaw() {
        return gradeRaw;
    }

    /** Level of the stone itself, as the engine defines it; not a character level. */
    public @Nullable Integer getLevel() {
        return level;
    }

    public @Nullable Integer getMinCharacterLevel() {
        return minCharacterLevel;
    }

    public List<Integer> getAugmentationTargetGroupIds() {
        return augmentationTargetGroupIds;
    }

    public List<AugmentationOutcome> getOutcomes() {
        return outcomes;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemTemplateId(itemTemplateId)
                .grade(grade)
                .gradeRaw(gradeRaw)
                .level(level)
                .minCharacterLevel(minCharacterLevel)
                .augmentationTargetGroupIds(augmentationTargetGroupIds)
                .outcomes(outcomes);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LifeStone)) return false;
        LifeStone that = (LifeStone) o;
        return itemTemplateId == that.itemTemplateId
                && grade == that.grade
                && Objects.equals(gradeRaw, that.gradeRaw)
                && Objects.equals(level, that.level)
                && Objects.equals(minCharacterLevel, that.minCharacterLevel)
                && augmentationTargetGroupIds.equals(that.augmentationTargetGroupIds)
                && outcomes.equals(that.outcomes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                itemTemplateId, grade, gradeRaw, level, minCharacterLevel, augmentationTargetGroupIds, outcomes);
    }

    @Override
    public String toString() {
        return "LifeStone[itemTemplateId=" + itemTemplateId + ", gradeRaw=" + gradeRaw + ", level=" + level
                + ", augmentationTargetGroupIds=" + augmentationTargetGroupIds + ", outcomes=" + outcomes.size() + "]";
    }

    public static final class Builder {
        private int itemTemplateId;
        private @Nullable LifeStoneGrade grade;
        private @Nullable String gradeRaw;
        private @Nullable Integer level;
        private @Nullable Integer minCharacterLevel;
        private List<Integer> augmentationTargetGroupIds = Collections.emptyList();
        private List<AugmentationOutcome> outcomes = Collections.emptyList();

        public Builder itemTemplateId(int itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder grade(@Nullable LifeStoneGrade grade) {
            this.grade = grade;
            return this;
        }

        public Builder gradeRaw(@Nullable String gradeRaw) {
            this.gradeRaw = gradeRaw;
            return this;
        }

        public Builder level(@Nullable Integer level) {
            this.level = level;
            return this;
        }

        public Builder minCharacterLevel(@Nullable Integer minCharacterLevel) {
            this.minCharacterLevel = minCharacterLevel;
            return this;
        }

        public Builder augmentationTargetGroupIds(List<Integer> augmentationTargetGroupIds) {
            this.augmentationTargetGroupIds = augmentationTargetGroupIds;
            return this;
        }

        public Builder outcomes(List<AugmentationOutcome> outcomes) {
            this.outcomes = outcomes;
            return this;
        }

        public LifeStone build() {
            return new LifeStone(
                    itemTemplateId, grade, gradeRaw, level, minCharacterLevel, augmentationTargetGroupIds, outcomes);
        }
    }
}
