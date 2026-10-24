package app.l2nx.gs.adapter.api.domain.stat;

/**
 * Canonical operation of a stat modifier ({@code StatModifier.operation}); the engine operation token travels
 * in {@code operationRaw}. For a modifier on a {@code _RES} stat the operation is the one after the orientation
 * inversion described in {@link Stat}.
 */
public enum StatOperation {
    /** value += x. */
    ADD,
    /** value -= x. */
    SUBTRACT,
    /** value *= x (chained multiplications). */
    MULTIPLY,
    /** value /= x. */
    DIVIDE,
    /** value = x. */
    SET,
    /** Adds x percent; percents of one stat are summed and applied once. */
    PERCENT,
    /** value += base value * x. */
    BASE_MULTIPLY,
    /** A summon receives x of the owner stat. */
    SHARE,
    /** Item enchant bonus by enchant level. */
    ENCHANT,
    /** Maximum HP bonus of enchanted armor. */
    ENCHANT_HP
}
