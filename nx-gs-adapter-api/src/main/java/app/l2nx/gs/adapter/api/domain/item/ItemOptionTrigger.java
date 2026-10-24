package app.l2nx.gs.adapter.api.domain.item;

/**
 * Combat event on which an item option casts its skill ({@code ItemOptionSkill.trigger}). Derived from the engine's
 * option structure, so it has no raw companion.
 */
public enum ItemOptionTrigger {
    /** A non-critical normal-attack hit and every physical skill the owner casts, once per affected target. */
    ATTACK,
    /** Every magic skill the owner casts, once per affected target. */
    MAGIC,
    /** A critical normal-attack hit; a critical hit of a physical skill does not fire it. */
    CRITICAL
}
