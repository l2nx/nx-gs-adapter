package app.l2nx.gs.adapter.api.domain.skill.kind;

/**
 * Client operate-type code of a skill ({@code Skill.operateType}); the engine token travels in {@code
 * operateTypeRaw}. Named after the client code, not by meaning: the code is the identity every game line shares.
 * Every code folds to a {@link SkillOperation}:
 * <ul>
 *   <li>{@code ACTIVE}: {@code A1}-{@code A6}, {@code CA1}, {@code CA2}, {@code CA5}, {@code DA1}-{@code DA5}</li>
 *   <li>{@code PASSIVE}: {@code P}</li>
 *   <li>{@code TOGGLE}: {@code T}, {@code TG}, {@code AU}</li>
 * </ul>
 */
public enum SkillOperateType {
    /** Active, instant effect. */
    A1,
    /** Active, continuous (+ instant) effect. */
    A2,
    /** Active, instant on target + continuous on self. */
    A3,
    /** Active, instant + continuous (event herbs). */
    A4,
    /** Active aura. */
    A5,
    /** Active synergy. */
    A6,
    /** Channeling, instant effect per tick. */
    CA1,
    /** Channeling, variant (client meaning unknown). */
    CA2,
    /** Channeling, continuous effect per tick. */
    CA5,
    /** Directional (rush), instant effect. */
    DA1,
    /** Directional (rush), continuous effect. */
    DA2,
    /** Directional blink. */
    DA3,
    /** Directional, left, continuous effect. */
    DA4,
    /** Directional, right, continuous effect. */
    DA5,
    P,
    T,
    /** Toggle with group. */
    TG,
    AU
}
