package app.l2nx.gs.adapter.api.domain.skill.effect;

/**
 * When and to whom a skill effect applies ({@code SkillEffect.scope}); the engine scope token travels in {@code
 * scopeRaw}. Effects of a passive skill always get {@link #PASSIVE}.
 */
public enum SkillEffectScope {
    /** Applied to each affected target on cast. */
    TARGET,
    /** Applied to the caster on cast. */
    SELF,
    /** Applied to the owner while the skill is known. */
    PASSIVE,
    /** Applied to the target when casting begins. */
    CAST_START,
    /** Applied to channel targets on every channel tick. */
    CHANNELING,
    /** Applied only when caster and target are playable. */
    PVP,
    /** Applied only when caster is playable and target is an NPC. */
    PVE,
    /** Applied when the skill's abnormal ends. */
    ABNORMAL_END
}
