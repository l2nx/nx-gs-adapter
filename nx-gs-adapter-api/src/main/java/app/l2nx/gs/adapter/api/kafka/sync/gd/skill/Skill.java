package app.l2nx.gs.adapter.api.kafka.sync.gd.skill;

import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectCategory;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillBasicProperty;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillMagicType;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillOperateType;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillTrait;
import app.l2nx.gs.adapter.api.domain.skill.target.SkillAffectObject;
import app.l2nx.gs.adapter.api.domain.skill.target.SkillAffectScope;
import app.l2nx.gs.adapter.api.domain.skill.target.SkillTargetType;
import app.l2nx.gs.adapter.api.domain.stat.Stat;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect.SkillEffect;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn.SkillLearn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Build-agnostic skill wire DTO ({@code skill} topic of the {@code gd} sync stream); one instance is the whole
 * aggregate for a skillId. Only {@link #getId()} is non-null: elsewhere {@code null} means the build did not supply
 * the value.
 *
 * <p>Vocabulary values travel as a canonical / raw pair, here and on every type nested in this aggregate. The plain
 * field ({@code operateType}, {@code params}) carries the canonical value, an API enum or a canonical map, and is
 * {@code null} when the engine token has no exact canonical meaning: the host never guesses a mapping. The
 * {@code <name>Raw} companion carries the engine token exactly as the engine has it and is sent whenever the engine
 * has one. A raw field without a canonical companion ({@code skillTypeRaw}, {@code abnormalTypeRaw}) is per-build
 * engine data that has no API vocabulary.</p>
 *
 * <p>Header values (operate, target, affect and magic type, basic property, trait) are read from the skill's
 * canonical level.</p>
 */
public final class Skill {

    private final int id;
    private final @Nullable SkillOperateType operateType;
    private final @Nullable String operateTypeRaw;
    private final @Nullable String skillTypeRaw;
    private final @Nullable SkillTargetType targetType;
    private final @Nullable String targetTypeRaw;
    private final @Nullable SkillAffectScope affectScope;
    private final @Nullable String affectScopeRaw;
    private final @Nullable SkillAffectObject affectObject;
    private final @Nullable String affectObjectRaw;
    private final @Nullable SkillTrait trait;
    private final @Nullable String traitRaw;
    private final @Nullable SkillMagicType magicType;
    private final @Nullable String magicTypeRaw;
    private final @Nullable SkillBasicProperty basicProperty;
    private final @Nullable String basicPropertyRaw;
    private final @Nullable String abnormalTypeRaw;
    private final @Nullable List<String> abnormalVisualEffectsRaw;
    private final @Nullable Stat saveVs;
    private final @Nullable String saveVsRaw;
    private final @Nullable SkillEffectCategory effectCategory;
    private final @Nullable Integer sharedReuseGroup;
    private final @Nullable String icon;
    private final @Nullable Integer maxLevel;
    private final @Nullable SkillFlags flags;
    private final @Nullable List<SkillLevel> levels;
    private final @Nullable List<SkillEnchantRoute> enchantRoutes;
    private final @Nullable List<SkillLearn> learns;
    private final @Nullable List<GearScoreContribution> gearScoreContributions;

    public Skill(
            int id,
            @Nullable SkillOperateType operateType,
            @Nullable String operateTypeRaw,
            @Nullable String skillTypeRaw,
            @Nullable SkillTargetType targetType,
            @Nullable String targetTypeRaw,
            @Nullable SkillAffectScope affectScope,
            @Nullable String affectScopeRaw,
            @Nullable SkillAffectObject affectObject,
            @Nullable String affectObjectRaw,
            @Nullable SkillTrait trait,
            @Nullable String traitRaw,
            @Nullable SkillMagicType magicType,
            @Nullable String magicTypeRaw,
            @Nullable SkillBasicProperty basicProperty,
            @Nullable String basicPropertyRaw,
            @Nullable String abnormalTypeRaw,
            @Nullable List<String> abnormalVisualEffectsRaw,
            @Nullable Stat saveVs,
            @Nullable String saveVsRaw,
            @Nullable SkillEffectCategory effectCategory,
            @Nullable Integer sharedReuseGroup,
            @Nullable String icon,
            @Nullable Integer maxLevel,
            @Nullable SkillFlags flags,
            @Nullable List<SkillLevel> levels,
            @Nullable List<SkillEnchantRoute> enchantRoutes,
            @Nullable List<SkillLearn> learns,
            @Nullable List<GearScoreContribution> gearScoreContributions) {
        this.id = id;
        this.operateType = operateType;
        this.operateTypeRaw = operateTypeRaw;
        this.skillTypeRaw = skillTypeRaw;
        this.targetType = targetType;
        this.targetTypeRaw = targetTypeRaw;
        this.affectScope = affectScope;
        this.affectScopeRaw = affectScopeRaw;
        this.affectObject = affectObject;
        this.affectObjectRaw = affectObjectRaw;
        this.trait = trait;
        this.traitRaw = traitRaw;
        this.magicType = magicType;
        this.magicTypeRaw = magicTypeRaw;
        this.basicProperty = basicProperty;
        this.basicPropertyRaw = basicPropertyRaw;
        this.abnormalTypeRaw = abnormalTypeRaw;
        this.abnormalVisualEffectsRaw = abnormalVisualEffectsRaw == null
                ? null
                : Collections.unmodifiableList(new ArrayList<String>(abnormalVisualEffectsRaw));
        this.saveVs = saveVs;
        this.saveVsRaw = saveVsRaw;
        this.effectCategory = effectCategory;
        this.sharedReuseGroup = sharedReuseGroup;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.flags = flags;
        this.levels = levels == null ? null : Collections.unmodifiableList(new ArrayList<SkillLevel>(levels));
        this.enchantRoutes = enchantRoutes == null
                ? null
                : Collections.unmodifiableList(new ArrayList<SkillEnchantRoute>(enchantRoutes));
        this.learns = learns == null ? null : Collections.unmodifiableList(new ArrayList<SkillLearn>(learns));
        this.gearScoreContributions = gearScoreContributions == null
                ? null
                : Collections.unmodifiableList(new ArrayList<GearScoreContribution>(gearScoreContributions));
    }

    public int getId() {
        return id;
    }

    public @Nullable SkillOperateType getOperateType() {
        return operateType;
    }

    public @Nullable String getOperateTypeRaw() {
        return operateTypeRaw;
    }

    /**
     * Single skill-type token of builds that classify a skill by one type and derive its effects from it
     * (see {@link SkillEffect#getDerived()}); no API vocabulary exists for it. {@code null} on builds without the concept.
     */
    public @Nullable String getSkillTypeRaw() {
        return skillTypeRaw;
    }

    /**
     * Single-axis token with the area shape embedded on builds that have one target axis, the selection token
     * otherwise. Read the shape from {@link #getAffectScope()} and {@link #getAffectObject()}, which every host fills.
     */
    public @Nullable SkillTargetType getTargetType() {
        return targetType;
    }

    public @Nullable String getTargetTypeRaw() {
        return targetTypeRaw;
    }

    /**
     * Which creatures around the selected target are affected. Builds with a single target axis derive it
     * from the target type.
     */
    public @Nullable SkillAffectScope getAffectScope() {
        return affectScope;
    }

    /** {@code null} when the build has no own token and the scope is derived. */
    public @Nullable String getAffectScopeRaw() {
        return affectScopeRaw;
    }

    /**
     * Relation filter on the affected creatures. Builds with a single target axis derive it from the
     * target type.
     */
    public @Nullable SkillAffectObject getAffectObject() {
        return affectObject;
    }

    /** {@code null} when the build has no own token and the filter is derived. */
    public @Nullable String getAffectObjectRaw() {
        return affectObjectRaw;
    }

    public @Nullable SkillTrait getTrait() {
        return trait;
    }

    public @Nullable String getTraitRaw() {
        return traitRaw;
    }

    public @Nullable SkillMagicType getMagicType() {
        return magicType;
    }

    /** The engine's magic-type number as a decimal string. */
    public @Nullable String getMagicTypeRaw() {
        return magicTypeRaw;
    }

    /**
     * Which abnormal-resistance roll the skill uses; {@code null} on builds that express the roll through
     * {@link #getSaveVs()}.
     */
    public @Nullable SkillBasicProperty getBasicProperty() {
        return basicProperty;
    }

    public @Nullable String getBasicPropertyRaw() {
        return basicPropertyRaw;
    }

    /**
     * Buff-slot stacking type of the primary effect; same-type abnormals overwrite by abnormal level. Abnormal
     * types are client data per chronicle, so they never become an API vocabulary. {@code null} when the skill occupies
     * no buff slot.
     */
    public @Nullable String getAbnormalTypeRaw() {
        return abnormalTypeRaw;
    }

    /** Abnormal visual-effect tokens; client data per chronicle, like abnormal types. */
    public @Nullable List<String> getAbnormalVisualEffectsRaw() {
        return abnormalVisualEffectsRaw;
    }

    /**
     * Base stat of the save roll, one of {@code STR DEX CON INT WIT MEN}; {@code null} when the skill makes
     * no save roll or the build uses {@link #getBasicProperty()}.
     */
    public @Nullable Stat getSaveVs() {
        return saveVs;
    }

    public @Nullable String getSaveVsRaw() {
        return saveVsRaw;
    }

    /** Computed by the same host function as the category of a runtime character effect. */
    public @Nullable SkillEffectCategory getEffectCategory() {
        return effectCategory;
    }

    /** Skills in the same group share their reuse delay; {@code null} when independent. */
    public @Nullable Integer getSharedReuseGroup() {
        return sharedReuseGroup;
    }

    public @Nullable String getIcon() {
        return icon;
    }

    public @Nullable Integer getMaxLevel() {
        return maxLevel;
    }

    public @Nullable SkillFlags getFlags() {
        return flags;
    }

    public @Nullable List<SkillLevel> getLevels() {
        return levels;
    }

    public @Nullable List<SkillEnchantRoute> getEnchantRoutes() {
        return enchantRoutes;
    }

    /**
     * Every skill-tree entry that teaches this skill, across all tree kinds; {@code null} when no tree
     * teaches it (NPC-only or item-granted skills).
     */
    public @Nullable List<SkillLearn> getLearns() {
        return learns;
    }

    /** Owning / per-level / per-enchant bonuses; {@code null} when the build computes no gear score. */
    public @Nullable List<GearScoreContribution> getGearScoreContributions() {
        return gearScoreContributions;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .operateType(operateType)
                .operateTypeRaw(operateTypeRaw)
                .skillTypeRaw(skillTypeRaw)
                .targetType(targetType)
                .targetTypeRaw(targetTypeRaw)
                .affectScope(affectScope)
                .affectScopeRaw(affectScopeRaw)
                .affectObject(affectObject)
                .affectObjectRaw(affectObjectRaw)
                .trait(trait)
                .traitRaw(traitRaw)
                .magicType(magicType)
                .magicTypeRaw(magicTypeRaw)
                .basicProperty(basicProperty)
                .basicPropertyRaw(basicPropertyRaw)
                .abnormalTypeRaw(abnormalTypeRaw)
                .abnormalVisualEffectsRaw(abnormalVisualEffectsRaw)
                .saveVs(saveVs)
                .saveVsRaw(saveVsRaw)
                .effectCategory(effectCategory)
                .sharedReuseGroup(sharedReuseGroup)
                .icon(icon)
                .maxLevel(maxLevel)
                .flags(flags)
                .levels(levels)
                .enchantRoutes(enchantRoutes)
                .learns(learns)
                .gearScoreContributions(gearScoreContributions);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Skill)) return false;
        Skill that = (Skill) o;
        return id == that.id
                && Objects.equals(operateType, that.operateType)
                && Objects.equals(operateTypeRaw, that.operateTypeRaw)
                && Objects.equals(skillTypeRaw, that.skillTypeRaw)
                && Objects.equals(targetType, that.targetType)
                && Objects.equals(targetTypeRaw, that.targetTypeRaw)
                && Objects.equals(affectScope, that.affectScope)
                && Objects.equals(affectScopeRaw, that.affectScopeRaw)
                && Objects.equals(affectObject, that.affectObject)
                && Objects.equals(affectObjectRaw, that.affectObjectRaw)
                && Objects.equals(trait, that.trait)
                && Objects.equals(traitRaw, that.traitRaw)
                && Objects.equals(magicType, that.magicType)
                && Objects.equals(magicTypeRaw, that.magicTypeRaw)
                && Objects.equals(basicProperty, that.basicProperty)
                && Objects.equals(basicPropertyRaw, that.basicPropertyRaw)
                && Objects.equals(abnormalTypeRaw, that.abnormalTypeRaw)
                && Objects.equals(abnormalVisualEffectsRaw, that.abnormalVisualEffectsRaw)
                && Objects.equals(saveVs, that.saveVs)
                && Objects.equals(saveVsRaw, that.saveVsRaw)
                && Objects.equals(effectCategory, that.effectCategory)
                && Objects.equals(sharedReuseGroup, that.sharedReuseGroup)
                && Objects.equals(icon, that.icon)
                && Objects.equals(maxLevel, that.maxLevel)
                && Objects.equals(flags, that.flags)
                && Objects.equals(levels, that.levels)
                && Objects.equals(enchantRoutes, that.enchantRoutes)
                && Objects.equals(learns, that.learns)
                && Objects.equals(gearScoreContributions, that.gearScoreContributions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                operateType,
                operateTypeRaw,
                skillTypeRaw,
                targetType,
                targetTypeRaw,
                affectScope,
                affectScopeRaw,
                affectObject,
                affectObjectRaw,
                trait,
                traitRaw,
                magicType,
                magicTypeRaw,
                basicProperty,
                basicPropertyRaw,
                abnormalTypeRaw,
                abnormalVisualEffectsRaw,
                saveVs,
                saveVsRaw,
                effectCategory,
                sharedReuseGroup,
                icon,
                maxLevel,
                flags,
                levels,
                enchantRoutes,
                learns,
                gearScoreContributions);
    }

    @Override
    public String toString() {
        return "Skill[" + "id=" + id + ", operateType=" + operateType + ", maxLevel=" + maxLevel + "]";
    }

    public static final class Builder {
        private int id;
        private @Nullable SkillOperateType operateType;
        private @Nullable String operateTypeRaw;
        private @Nullable String skillTypeRaw;
        private @Nullable SkillTargetType targetType;
        private @Nullable String targetTypeRaw;
        private @Nullable SkillAffectScope affectScope;
        private @Nullable String affectScopeRaw;
        private @Nullable SkillAffectObject affectObject;
        private @Nullable String affectObjectRaw;
        private @Nullable SkillTrait trait;
        private @Nullable String traitRaw;
        private @Nullable SkillMagicType magicType;
        private @Nullable String magicTypeRaw;
        private @Nullable SkillBasicProperty basicProperty;
        private @Nullable String basicPropertyRaw;
        private @Nullable String abnormalTypeRaw;
        private @Nullable List<String> abnormalVisualEffectsRaw;
        private @Nullable Stat saveVs;
        private @Nullable String saveVsRaw;
        private @Nullable SkillEffectCategory effectCategory;
        private @Nullable Integer sharedReuseGroup;
        private @Nullable String icon;
        private @Nullable Integer maxLevel;
        private @Nullable SkillFlags flags;
        private @Nullable List<SkillLevel> levels;
        private @Nullable List<SkillEnchantRoute> enchantRoutes;
        private @Nullable List<SkillLearn> learns;
        private @Nullable List<GearScoreContribution> gearScoreContributions;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder operateType(@Nullable SkillOperateType operateType) {
            this.operateType = operateType;
            return this;
        }

        public Builder operateTypeRaw(@Nullable String operateTypeRaw) {
            this.operateTypeRaw = operateTypeRaw;
            return this;
        }

        public Builder skillTypeRaw(@Nullable String skillTypeRaw) {
            this.skillTypeRaw = skillTypeRaw;
            return this;
        }

        public Builder targetType(@Nullable SkillTargetType targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder targetTypeRaw(@Nullable String targetTypeRaw) {
            this.targetTypeRaw = targetTypeRaw;
            return this;
        }

        public Builder affectScope(@Nullable SkillAffectScope affectScope) {
            this.affectScope = affectScope;
            return this;
        }

        public Builder affectScopeRaw(@Nullable String affectScopeRaw) {
            this.affectScopeRaw = affectScopeRaw;
            return this;
        }

        public Builder affectObject(@Nullable SkillAffectObject affectObject) {
            this.affectObject = affectObject;
            return this;
        }

        public Builder affectObjectRaw(@Nullable String affectObjectRaw) {
            this.affectObjectRaw = affectObjectRaw;
            return this;
        }

        public Builder trait(@Nullable SkillTrait trait) {
            this.trait = trait;
            return this;
        }

        public Builder traitRaw(@Nullable String traitRaw) {
            this.traitRaw = traitRaw;
            return this;
        }

        public Builder magicType(@Nullable SkillMagicType magicType) {
            this.magicType = magicType;
            return this;
        }

        public Builder magicTypeRaw(@Nullable String magicTypeRaw) {
            this.magicTypeRaw = magicTypeRaw;
            return this;
        }

        public Builder basicProperty(@Nullable SkillBasicProperty basicProperty) {
            this.basicProperty = basicProperty;
            return this;
        }

        public Builder basicPropertyRaw(@Nullable String basicPropertyRaw) {
            this.basicPropertyRaw = basicPropertyRaw;
            return this;
        }

        public Builder abnormalTypeRaw(@Nullable String abnormalTypeRaw) {
            this.abnormalTypeRaw = abnormalTypeRaw;
            return this;
        }

        public Builder abnormalVisualEffectsRaw(@Nullable List<String> abnormalVisualEffectsRaw) {
            this.abnormalVisualEffectsRaw = abnormalVisualEffectsRaw;
            return this;
        }

        public Builder saveVs(@Nullable Stat saveVs) {
            this.saveVs = saveVs;
            return this;
        }

        public Builder saveVsRaw(@Nullable String saveVsRaw) {
            this.saveVsRaw = saveVsRaw;
            return this;
        }

        public Builder effectCategory(@Nullable SkillEffectCategory effectCategory) {
            this.effectCategory = effectCategory;
            return this;
        }

        public Builder sharedReuseGroup(@Nullable Integer sharedReuseGroup) {
            this.sharedReuseGroup = sharedReuseGroup;
            return this;
        }

        public Builder icon(@Nullable String icon) {
            this.icon = icon;
            return this;
        }

        public Builder maxLevel(@Nullable Integer maxLevel) {
            this.maxLevel = maxLevel;
            return this;
        }

        public Builder flags(@Nullable SkillFlags flags) {
            this.flags = flags;
            return this;
        }

        public Builder levels(@Nullable List<SkillLevel> levels) {
            this.levels = levels;
            return this;
        }

        public Builder enchantRoutes(@Nullable List<SkillEnchantRoute> enchantRoutes) {
            this.enchantRoutes = enchantRoutes;
            return this;
        }

        public Builder learns(@Nullable List<SkillLearn> learns) {
            this.learns = learns;
            return this;
        }

        public Builder gearScoreContributions(@Nullable List<GearScoreContribution> gearScoreContributions) {
            this.gearScoreContributions = gearScoreContributions;
            return this;
        }

        public Skill build() {
            return new Skill(
                    id,
                    operateType,
                    operateTypeRaw,
                    skillTypeRaw,
                    targetType,
                    targetTypeRaw,
                    affectScope,
                    affectScopeRaw,
                    affectObject,
                    affectObjectRaw,
                    trait,
                    traitRaw,
                    magicType,
                    magicTypeRaw,
                    basicProperty,
                    basicPropertyRaw,
                    abnormalTypeRaw,
                    abnormalVisualEffectsRaw,
                    saveVs,
                    saveVsRaw,
                    effectCategory,
                    sharedReuseGroup,
                    icon,
                    maxLevel,
                    flags,
                    levels,
                    enchantRoutes,
                    learns,
                    gearScoreContributions);
        }
    }
}
