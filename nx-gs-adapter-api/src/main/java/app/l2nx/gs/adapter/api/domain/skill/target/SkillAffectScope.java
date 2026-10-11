package app.l2nx.gs.adapter.api.domain.skill.target;

/**
 * Which creatures around the selected target are affected by a skill ({@code Skill.affectScope}); the engine token
 * travels in {@code affectScopeRaw}. Together with {@link SkillAffectObject} it fixes the affected set; the shape
 * is never read from {@link SkillTargetType}. Hosts whose engine embeds the shape in the target type derive this
 * value and leave the raw field {@code null}.
 */
public enum SkillAffectScope {
    /** No affected creatures (self-contained effect, passives). */
    NONE,
    /** The selected target only. */
    SINGLE,
    /** Circle around the selected target. */
    RANGE,
    /** Circle around the target, lowest HP first up to the limit. */
    RANGE_SORT_BY_HP,
    /** Ring (min/max radius) around the target. */
    RING_RANGE,
    /** Circle around the caster. */
    POINT_BLANK,
    /** Cone from the caster towards the target (angles on the level). */
    FAN,
    /** Cone around the caster in its facing (no target needed). */
    FAN_POINT_BLANK,
    /** Rectangle from the caster towards the target. */
    SQUARE,
    /** Rectangle from the caster in its facing. */
    SQUARE_POINT_BLANK,
    /** Static world objects (doors, siege objects) around the target. */
    STATIC_OBJECT,
    /** Caster's party members in range. */
    PARTY,
    /** Caster's clan members in range. */
    CLAN,
    /** Party and clan members in range. */
    PARTY_CLAN,
    /** Alliance members in range. */
    ALLY,
    /** Command-channel members in range. */
    COMMAND_CHANNEL,
    /** Dead party members in range. */
    DEAD_PARTY,
    /** Dead clan members in range. */
    DEAD_CLAN,
    /** Dead alliance members in range. */
    DEAD_ALLY
}
