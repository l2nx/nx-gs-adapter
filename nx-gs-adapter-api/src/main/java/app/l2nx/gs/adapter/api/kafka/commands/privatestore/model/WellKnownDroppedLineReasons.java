package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

/**
 * Canonical {@link DroppedLine#getReason()} values. Open string so a new reason is not a breaking change.
 * {@link #NOT_AVAILABLE} is the fallback for a line the host's native availability checks refuse without a finer code.
 */
public final class WellKnownDroppedLineReasons {

    private WellKnownDroppedLineReasons() {}

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_TRADEABLE = "NOT_TRADEABLE";
    public static final String ITEM_BLOCKED = "ITEM_BLOCKED";
    public static final String EQUIPPED = "EQUIPPED";
    public static final String BAD_COUNT = "BAD_COUNT";
    public static final String PRICE_OVERFLOW = "PRICE_OVERFLOW";
    public static final String ADENA = "ADENA";
    public static final String PET_CONTROL_ITEM = "PET_CONTROL_ITEM";
    public static final String ENCHANTING = "ENCHANTING";
    public static final String SKILL_CONSUMABLE = "SKILL_CONSUMABLE";
    public static final String PET_COLLAR_NOT_EMPTY = "PET_COLLAR_NOT_EMPTY";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
}
