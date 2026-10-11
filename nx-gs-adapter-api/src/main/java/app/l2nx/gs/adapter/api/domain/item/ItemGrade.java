package app.l2nx.gs.adapter.api.domain.item;

/**
 * Item grade (crystal type), also the expertise grade of a character. Sub-tiers such as {@code S80} or {@code R95}
 * are distinct constants because they gate different gear.
 */
public enum ItemGrade {
    NONE,
    D,
    C,
    B,
    A,
    S,
    S80,
    S84,
    R,
    R95,
    R99,
    /** Event items that have no regular grade. */
    EVENT
}
