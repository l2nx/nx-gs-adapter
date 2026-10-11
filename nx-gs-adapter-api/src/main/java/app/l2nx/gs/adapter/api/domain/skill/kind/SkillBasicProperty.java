package app.l2nx.gs.adapter.api.domain.skill.kind;

/**
 * Abnormal-resist roll a skill uses ({@code Skill.basicProperty}); the engine token travels in {@code
 * basicPropertyRaw}. Only some game lines express the roll this way; others use {@code Skill.saveVs}, a
 * base-stat-keyed model that is not converted.
 */
public enum SkillBasicProperty {
    /** No abnormal-resist roll. */
    NONE,
    /** Rolled against {@link app.l2nx.gs.adapter.api.domain.stat.Stat#P_ABNORMAL_RES}. */
    PHYSICAL,
    /** Rolled against {@link app.l2nx.gs.adapter.api.domain.stat.Stat#M_ABNORMAL_RES}. */
    MAGIC
}
