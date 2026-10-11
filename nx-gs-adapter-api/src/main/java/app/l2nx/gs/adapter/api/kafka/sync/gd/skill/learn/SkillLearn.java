package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn;

import app.l2nx.gs.adapter.api.domain.character.CharacterRace;
import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import app.l2nx.gs.adapter.api.domain.clan.SocialClass;
import app.l2nx.gs.adapter.api.domain.skill.SkillTreeKind;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.Skill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One skill-tree entry that teaches a {@link Skill}, inverted from the host's skill trees of every kind.
 * {@code treeKindRaw} and {@code skillLevel} are non-null. Clan trees carry clan requirements and costs instead of
 * character ones.
 */
public final class SkillLearn {

    private final @Nullable SkillTreeKind treeKind;
    private final String treeKindRaw;
    private final @Nullable String treeName;
    private final @Nullable CharacterClass clazz;
    private final int skillLevel;
    private final @Nullable Integer requiredLevel;
    private final @Nullable Integer requiredClanLevel;
    private final @Nullable Long learnSp;
    private final @Nullable Long learnClanReputation;
    private final @Nullable Boolean autoLearn;
    private final @Nullable Boolean learnedByNpc;
    private final @Nullable Boolean learnedByForgottenScroll;
    private final @Nullable List<SkillLearnItemSet> requiredItemSets;
    private final @Nullable List<SkillLevelRef> prerequisites;
    private final @Nullable List<CharacterRace> races;
    private final @Nullable List<Integer> residenceIds;
    private final @Nullable SocialClass socialClass;
    private final @Nullable List<SkillLearnSubClass> subClassRequirements;
    private final @Nullable List<Integer> removedSkillIds;
    private final @Nullable Integer requiredDualClassLevel;
    private final @Nullable Integer abilityTreeId;
    private final @Nullable Integer abilityRow;
    private final @Nullable Integer abilityColumn;
    private final @Nullable Integer abilityPointsRequired;

    public SkillLearn(
            @Nullable SkillTreeKind treeKind,
            String treeKindRaw,
            @Nullable String treeName,
            @Nullable CharacterClass clazz,
            int skillLevel,
            @Nullable Integer requiredLevel,
            @Nullable Integer requiredClanLevel,
            @Nullable Long learnSp,
            @Nullable Long learnClanReputation,
            @Nullable Boolean autoLearn,
            @Nullable Boolean learnedByNpc,
            @Nullable Boolean learnedByForgottenScroll,
            @Nullable List<SkillLearnItemSet> requiredItemSets,
            @Nullable List<SkillLevelRef> prerequisites,
            @Nullable List<CharacterRace> races,
            @Nullable List<Integer> residenceIds,
            @Nullable SocialClass socialClass,
            @Nullable List<SkillLearnSubClass> subClassRequirements,
            @Nullable List<Integer> removedSkillIds,
            @Nullable Integer requiredDualClassLevel,
            @Nullable Integer abilityTreeId,
            @Nullable Integer abilityRow,
            @Nullable Integer abilityColumn,
            @Nullable Integer abilityPointsRequired) {
        this.treeKind = treeKind;
        this.treeKindRaw = Objects.requireNonNull(treeKindRaw, "treeKindRaw");
        this.treeName = treeName;
        this.clazz = clazz;
        this.skillLevel = skillLevel;
        this.requiredLevel = requiredLevel;
        this.requiredClanLevel = requiredClanLevel;
        this.learnSp = learnSp;
        this.learnClanReputation = learnClanReputation;
        this.autoLearn = autoLearn;
        this.learnedByNpc = learnedByNpc;
        this.learnedByForgottenScroll = learnedByForgottenScroll;
        this.requiredItemSets = requiredItemSets == null
                ? null
                : Collections.unmodifiableList(new ArrayList<SkillLearnItemSet>(requiredItemSets));
        this.prerequisites = prerequisites == null
                ? null
                : Collections.unmodifiableList(new ArrayList<SkillLevelRef>(prerequisites));
        this.races = races == null ? null : Collections.unmodifiableList(new ArrayList<CharacterRace>(races));
        this.residenceIds =
                residenceIds == null ? null : Collections.unmodifiableList(new ArrayList<Integer>(residenceIds));
        this.socialClass = socialClass;
        this.subClassRequirements = subClassRequirements == null
                ? null
                : Collections.unmodifiableList(new ArrayList<SkillLearnSubClass>(subClassRequirements));
        this.removedSkillIds =
                removedSkillIds == null ? null : Collections.unmodifiableList(new ArrayList<Integer>(removedSkillIds));
        this.requiredDualClassLevel = requiredDualClassLevel;
        this.abilityTreeId = abilityTreeId;
        this.abilityRow = abilityRow;
        this.abilityColumn = abilityColumn;
        this.abilityPointsRequired = abilityPointsRequired;
    }

    public @Nullable SkillTreeKind getTreeKind() {
        return treeKind;
    }

    public String getTreeKindRaw() {
        return treeKindRaw;
    }

    /** Tells apart several engine trees of one kind; {@code null} when the kind has a single tree. */
    public @Nullable String getTreeName() {
        return treeName;
    }

    /** {@code null} when the tree is not class-keyed (common, clan, hero, ...). */
    public @Nullable CharacterClass getClazz() {
        return clazz;
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    /** Character level; {@code null} on clan trees. */
    public @Nullable Integer getRequiredLevel() {
        return requiredLevel;
    }

    /** Clan level; clan trees only. */
    public @Nullable Integer getRequiredClanLevel() {
        return requiredClanLevel;
    }

    /** {@code null} on clan trees. */
    public @Nullable Long getLearnSp() {
        return learnSp;
    }

    /** Clan reputation cost; clan trees only. */
    public @Nullable Long getLearnClanReputation() {
        return learnClanReputation;
    }

    public @Nullable Boolean getAutoLearn() {
        return autoLearn;
    }

    public @Nullable Boolean getLearnedByNpc() {
        return learnedByNpc;
    }

    public @Nullable Boolean getLearnedByForgottenScroll() {
        return learnedByForgottenScroll;
    }

    /** Any-of list of item sets, each set all-of; a build with one fixed requirement emits one set. */
    public @Nullable List<SkillLearnItemSet> getRequiredItemSets() {
        return requiredItemSets;
    }

    /** Skills that must already be known, all-of. */
    public @Nullable List<SkillLevelRef> getPrerequisites() {
        return prerequisites;
    }

    /** {@code null} = any race. */
    public @Nullable List<CharacterRace> getRaces() {
        return races;
    }

    /** Residences whose ownership grants the skill. */
    public @Nullable List<Integer> getResidenceIds() {
        return residenceIds;
    }

    public @Nullable SocialClass getSocialClass() {
        return socialClass;
    }

    /** Sub-class certification requirements, all-of. */
    public @Nullable List<SkillLearnSubClass> getSubClassRequirements() {
        return subClassRequirements;
    }

    public @Nullable List<Integer> getRemovedSkillIds() {
        return removedSkillIds;
    }

    public @Nullable Integer getRequiredDualClassLevel() {
        return requiredDualClassLevel;
    }

    public @Nullable Integer getAbilityTreeId() {
        return abilityTreeId;
    }

    public @Nullable Integer getAbilityRow() {
        return abilityRow;
    }

    public @Nullable Integer getAbilityColumn() {
        return abilityColumn;
    }

    /** Ability points spent in the tree before this entry opens. */
    public @Nullable Integer getAbilityPointsRequired() {
        return abilityPointsRequired;
    }

    public Builder toBuilder() {
        return new Builder()
                .treeKind(treeKind)
                .treeKindRaw(treeKindRaw)
                .treeName(treeName)
                .clazz(clazz)
                .skillLevel(skillLevel)
                .requiredLevel(requiredLevel)
                .requiredClanLevel(requiredClanLevel)
                .learnSp(learnSp)
                .learnClanReputation(learnClanReputation)
                .autoLearn(autoLearn)
                .learnedByNpc(learnedByNpc)
                .learnedByForgottenScroll(learnedByForgottenScroll)
                .requiredItemSets(requiredItemSets)
                .prerequisites(prerequisites)
                .races(races)
                .residenceIds(residenceIds)
                .socialClass(socialClass)
                .subClassRequirements(subClassRequirements)
                .removedSkillIds(removedSkillIds)
                .requiredDualClassLevel(requiredDualClassLevel)
                .abilityTreeId(abilityTreeId)
                .abilityRow(abilityRow)
                .abilityColumn(abilityColumn)
                .abilityPointsRequired(abilityPointsRequired);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillLearn)) return false;
        SkillLearn that = (SkillLearn) o;
        return Objects.equals(treeKind, that.treeKind)
                && Objects.equals(treeKindRaw, that.treeKindRaw)
                && Objects.equals(treeName, that.treeName)
                && Objects.equals(clazz, that.clazz)
                && skillLevel == that.skillLevel
                && Objects.equals(requiredLevel, that.requiredLevel)
                && Objects.equals(requiredClanLevel, that.requiredClanLevel)
                && Objects.equals(learnSp, that.learnSp)
                && Objects.equals(learnClanReputation, that.learnClanReputation)
                && Objects.equals(autoLearn, that.autoLearn)
                && Objects.equals(learnedByNpc, that.learnedByNpc)
                && Objects.equals(learnedByForgottenScroll, that.learnedByForgottenScroll)
                && Objects.equals(requiredItemSets, that.requiredItemSets)
                && Objects.equals(prerequisites, that.prerequisites)
                && Objects.equals(races, that.races)
                && Objects.equals(residenceIds, that.residenceIds)
                && Objects.equals(socialClass, that.socialClass)
                && Objects.equals(subClassRequirements, that.subClassRequirements)
                && Objects.equals(removedSkillIds, that.removedSkillIds)
                && Objects.equals(requiredDualClassLevel, that.requiredDualClassLevel)
                && Objects.equals(abilityTreeId, that.abilityTreeId)
                && Objects.equals(abilityRow, that.abilityRow)
                && Objects.equals(abilityColumn, that.abilityColumn)
                && Objects.equals(abilityPointsRequired, that.abilityPointsRequired);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                treeKind,
                treeKindRaw,
                treeName,
                clazz,
                skillLevel,
                requiredLevel,
                requiredClanLevel,
                learnSp,
                learnClanReputation,
                autoLearn,
                learnedByNpc,
                learnedByForgottenScroll,
                requiredItemSets,
                prerequisites,
                races,
                residenceIds,
                socialClass,
                subClassRequirements,
                removedSkillIds,
                requiredDualClassLevel,
                abilityTreeId,
                abilityRow,
                abilityColumn,
                abilityPointsRequired);
    }

    @Override
    public String toString() {
        return "SkillLearn[" + "treeKind=" + treeKind + ", treeKindRaw=" + treeKindRaw + ", clazz=" + clazz
                + ", skillLevel=" + skillLevel + "]";
    }

    public static final class Builder {
        private @Nullable SkillTreeKind treeKind;
        private String treeKindRaw;
        private @Nullable String treeName;
        private @Nullable CharacterClass clazz;
        private int skillLevel;
        private @Nullable Integer requiredLevel;
        private @Nullable Integer requiredClanLevel;
        private @Nullable Long learnSp;
        private @Nullable Long learnClanReputation;
        private @Nullable Boolean autoLearn;
        private @Nullable Boolean learnedByNpc;
        private @Nullable Boolean learnedByForgottenScroll;
        private @Nullable List<SkillLearnItemSet> requiredItemSets;
        private @Nullable List<SkillLevelRef> prerequisites;
        private @Nullable List<CharacterRace> races;
        private @Nullable List<Integer> residenceIds;
        private @Nullable SocialClass socialClass;
        private @Nullable List<SkillLearnSubClass> subClassRequirements;
        private @Nullable List<Integer> removedSkillIds;
        private @Nullable Integer requiredDualClassLevel;
        private @Nullable Integer abilityTreeId;
        private @Nullable Integer abilityRow;
        private @Nullable Integer abilityColumn;
        private @Nullable Integer abilityPointsRequired;

        public Builder treeKind(@Nullable SkillTreeKind treeKind) {
            this.treeKind = treeKind;
            return this;
        }

        public Builder treeKindRaw(String treeKindRaw) {
            this.treeKindRaw = treeKindRaw;
            return this;
        }

        public Builder treeName(@Nullable String treeName) {
            this.treeName = treeName;
            return this;
        }

        public Builder clazz(@Nullable CharacterClass clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder skillLevel(int skillLevel) {
            this.skillLevel = skillLevel;
            return this;
        }

        public Builder requiredLevel(@Nullable Integer requiredLevel) {
            this.requiredLevel = requiredLevel;
            return this;
        }

        public Builder requiredClanLevel(@Nullable Integer requiredClanLevel) {
            this.requiredClanLevel = requiredClanLevel;
            return this;
        }

        public Builder learnSp(@Nullable Long learnSp) {
            this.learnSp = learnSp;
            return this;
        }

        public Builder learnClanReputation(@Nullable Long learnClanReputation) {
            this.learnClanReputation = learnClanReputation;
            return this;
        }

        public Builder autoLearn(@Nullable Boolean autoLearn) {
            this.autoLearn = autoLearn;
            return this;
        }

        public Builder learnedByNpc(@Nullable Boolean learnedByNpc) {
            this.learnedByNpc = learnedByNpc;
            return this;
        }

        public Builder learnedByForgottenScroll(@Nullable Boolean learnedByForgottenScroll) {
            this.learnedByForgottenScroll = learnedByForgottenScroll;
            return this;
        }

        public Builder requiredItemSets(@Nullable List<SkillLearnItemSet> requiredItemSets) {
            this.requiredItemSets = requiredItemSets;
            return this;
        }

        public Builder prerequisites(@Nullable List<SkillLevelRef> prerequisites) {
            this.prerequisites = prerequisites;
            return this;
        }

        public Builder races(@Nullable List<CharacterRace> races) {
            this.races = races;
            return this;
        }

        public Builder residenceIds(@Nullable List<Integer> residenceIds) {
            this.residenceIds = residenceIds;
            return this;
        }

        public Builder socialClass(@Nullable SocialClass socialClass) {
            this.socialClass = socialClass;
            return this;
        }

        public Builder subClassRequirements(@Nullable List<SkillLearnSubClass> subClassRequirements) {
            this.subClassRequirements = subClassRequirements;
            return this;
        }

        public Builder removedSkillIds(@Nullable List<Integer> removedSkillIds) {
            this.removedSkillIds = removedSkillIds;
            return this;
        }

        public Builder requiredDualClassLevel(@Nullable Integer requiredDualClassLevel) {
            this.requiredDualClassLevel = requiredDualClassLevel;
            return this;
        }

        public Builder abilityTreeId(@Nullable Integer abilityTreeId) {
            this.abilityTreeId = abilityTreeId;
            return this;
        }

        public Builder abilityRow(@Nullable Integer abilityRow) {
            this.abilityRow = abilityRow;
            return this;
        }

        public Builder abilityColumn(@Nullable Integer abilityColumn) {
            this.abilityColumn = abilityColumn;
            return this;
        }

        public Builder abilityPointsRequired(@Nullable Integer abilityPointsRequired) {
            this.abilityPointsRequired = abilityPointsRequired;
            return this;
        }

        public SkillLearn build() {
            return new SkillLearn(
                    treeKind,
                    treeKindRaw,
                    treeName,
                    clazz,
                    skillLevel,
                    requiredLevel,
                    requiredClanLevel,
                    learnSp,
                    learnClanReputation,
                    autoLearn,
                    learnedByNpc,
                    learnedByForgottenScroll,
                    requiredItemSets,
                    prerequisites,
                    races,
                    residenceIds,
                    socialClass,
                    subClassRequirements,
                    removedSkillIds,
                    requiredDualClassLevel,
                    abilityTreeId,
                    abilityRow,
                    abilityColumn,
                    abilityPointsRequired);
        }
    }
}
