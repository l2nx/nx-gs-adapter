package app.l2nx.gs.adapter.api.domain.skill.condition;

/**
 * Action whose engine-side permission gate a {@link SkillConditionType#CASTER_CAN_PERFORM} condition checks. The
 * gate is opaque (the engine evaluates several state checks at once); the constant says which action must be
 * currently permitted. Carried in the {@code skillConditionAction} param.
 */
public enum SkillConditionAction {
    /** Gate permits escaping (no escape block, not in restricted state). */
    ESCAPE,
    /** Gate permits summoning a player to the caster. */
    SUMMON_PLAYER,
    /** Gate permits summoning a servitor. */
    SUMMON_SERVITOR,
    /** Gate permits summoning a pet. */
    SUMMON_PET,
    /** Gate permits summoning another cubic. */
    SUMMON_CUBIC,
    /** Gate permits summoning a siege golem. */
    SUMMON_SIEGE_GOLEM,
    /** Gate permits transforming. */
    TRANSFORM,
    /** Gate permits leaving the transformation. */
    UNTRANSFORM,
    /** Gate permits sweeping a spoiled corpse. */
    SWEEP,
    /** Gate permits resurrecting the target. */
    RESURRECT,
    /** Gate permits refuelling (amount). */
    REFUEL_AIRSHIP,
    /** Gate permits engraving the castle artifact. */
    TAKE_CASTLE,
    /** Gate permits capturing the fortress flag. */
    TAKE_FORTRESS,
    /** Gate permits building a siege HQ / camp / advanced base. */
    CREATE_SIEGE_HEADQUARTERS,
    /** Gate permits building a Territory War outpost. */
    CREATE_TERRITORY_OUTPOST,
    /** Gate permits blinking (geodata path free over range). */
    BLINK,
    /** Gate permits adding teleport bookmark slots (amount). */
    EXPAND_TELEPORT_BOOKMARKS,
    /** Gate permits earning PC cafe points. */
    GAIN_PC_CAFE_POINTS
}
