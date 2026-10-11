package app.l2nx.gs.adapter.api.domain.skill.target;

/**
 * Relation filter applied to the creatures a skill affects ({@code Skill.affectObject}); the engine token travels
 * in {@code affectObjectRaw}. Used with {@link SkillAffectScope}.
 */
public enum SkillAffectObject {
    /** Every creature in scope. */
    ALL,
    /** Non-hostile creatures. */
    FRIEND,
    /** Non-hostile players only. */
    FRIEND_PLAYER,
    /** Hostile creatures. */
    NOT_FRIEND,
    /** Hostile players only. */
    NOT_FRIEND_PLAYER,
    /** Members of the caster's clan. */
    CLAN,
    /** Monsters only. */
    MONSTER,
    /** NPC corpses. */
    CORPSE_NPC,
    /** Hostile undead. */
    UNDEAD_ENEMY,
    /** Hidden objects / places (detection). */
    HIDDEN_PLACE,
    /** Invisible creatures. */
    INVISIBLE
}
