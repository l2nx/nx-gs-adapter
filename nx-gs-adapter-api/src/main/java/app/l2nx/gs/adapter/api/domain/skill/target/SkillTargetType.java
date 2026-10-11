package app.l2nx.gs.adapter.api.domain.skill.target;

/**
 * Canonical target type of a skill ({@code Skill.targetType}, and the {@code TARGET} skill characteristic); the
 * engine token travels in {@code targetTypeRaw}. Some hosts use single-axis tokens that embed the area shape
 * ({@code AOE_FRONT}); others describe only the initial selection ({@code ENEMY}). The affected set is always read
 * from {@link SkillAffectScope} and {@link SkillAffectObject}. {@code AOE_*} = area around the target,
 * {@code AURA_*} = around the caster, {@code SIEGE_*} = siege objects.
 */
public enum SkillTargetType {
    ONE,
    SELF,
    NONE,
    ENEMY_ONLY,

    AOE,
    AOE_FRONT,
    AOE_BEHIND,
    AOE_FRIENDLY,
    AOE_SUMMON,
    AOE_CORPSE_MOB,
    AOE_MOB,

    AURA,
    AURA_FRONT,
    AURA_BEHIND,
    AURA_FRIENDLY,
    AURA_CORPSE_MOB,
    AURA_UNDEAD_ENEMY,

    PARTY,
    PARTY_MEMBER,
    PARTY_OTHER,
    AOE_PARTY_NOT_ME,
    PARTY_CLAN,
    CLAN,
    CLAN_MEMBER,
    ALLY,
    COMMAND_CHANNEL,

    SUMMON,
    SERVITOR,
    PET,
    PET_OWNER,
    SUMMON_ENEMY,

    CORPSE,
    CORPSE_PLAYER,
    CORPSE_MOB,
    CORPSE_PET,
    CORPSE_PARTY,
    CORPSE_CLAN,
    CORPSE_ALLY,

    SIEGE_HOLY,
    SIEGE_FLAGPOLE,
    UNLOCKABLE,
    GROUND,

    /** An auto-attackable creature. */
    ENEMY,
    /** A non-hostile creature or self. */
    NOT_ENEMY,
    /** The selected creature, otherwise self. */
    TARGET_OR_SELF,
    /** Any creature except self. */
    NOT_SELF,
    /** An item on the ground or in the inventory. */
    ITEM,
    /** An enemy siege door or artillery object. */
    SIEGE_ARTILLERY,
    /** The advanced siege base NPC. */
    SIEGE_ADVANCE_BASE
}
