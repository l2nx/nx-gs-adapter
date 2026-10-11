package app.l2nx.gs.adapter.api.domain.skill;

/**
 * Kind of skill tree that teaches a skill ({@code SkillLearn.treeKind}); the engine tree token travels in {@code
 * treeKindRaw}. A kind may be declared before any data fills it, so a consumer must not assume every constant
 * occurs.
 */
public enum SkillTreeKind {
    /** Class skills; a learn entry without a class belongs to the common tree. */
    CLASS,
    /** Skills that can be transferred to other classes. */
    TRANSFER,
    /** Sub-class certification skills. */
    SUB_CLASS,
    CLAN,
    /** Clan sub-unit (squad) skills. */
    CLAN_SUBUNIT,
    NOBLESSE,
    HERO,
    TRANSFORMATION,
    COLLECTION,
    FISHING,
    GAME_MASTER,
    GAME_MASTER_AURA,
    ALCHEMY,
    /** Server-defined learnable tree; {@code SkillLearn.treeName} tells several of them apart. */
    CUSTOM,
    RACE,
    /** Revelation (chaos) skills. */
    REVELATION,
    /** Ability-point skills; the entry position is in the {@code SkillLearn.ability*} fields. */
    ABILITY,
    DUAL_CLASS,
    /** Skills kept through the awakening class change. */
    AWAKENING_SAVE,
    CERTIFICATION
}
