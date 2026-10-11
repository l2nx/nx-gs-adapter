package app.l2nx.gs.adapter.api.domain.skill.kind;

/**
 * Combat trait a skill belongs to (vulnerability / proficiency category); one per skill.
 * {@link #NONE} means not subject to trait resistances.
 */
public enum SkillTrait {
    NONE,
    BLEED,
    BOSS,
    DEATH,
    DERANGEMENT,
    ETC,
    GUST,
    HOLD,
    PARALYZE,
    PHYSICAL_BLOCKADE,
    POISON,
    SHOCK,
    SLEEP,
    VALAKAS,
    DISARM_WEAPON,
    /** Petrification. */
    TURN_STONE,
    ROOT_PHYSICALLY,
    ROOT_MAGICALLY,
    KNOCK_BACK,
    KNOCK_DOWN,
    PULL,
    AIR_BIND,
    FEAR,
    SILENCE,
    SUPPRESSION,
    INFECTION,
    ANOMALY,
    IMPRISON,
    /** Banish. */
    DEPORT,
    /** Polymorph. */
    CHANGE_BODY,
    ZONE,
    PSYCHIC
}
