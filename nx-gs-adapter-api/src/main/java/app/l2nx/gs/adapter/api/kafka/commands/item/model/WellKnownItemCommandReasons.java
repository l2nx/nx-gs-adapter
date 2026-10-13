package app.l2nx.gs.adapter.api.kafka.commands.item.model;

/** Canonical {@code extensions.reason} values of the critical refusals of the item commands; the set is open. */
public final class WellKnownItemCommandReasons {

    private WellKnownItemCommandReasons() {}

    /** {@code INVALID_STATE}: the stack is listed in an open private store. */
    public static final String ITEM_IN_STORE = "ITEM_IN_STORE";

    /** {@code INVALID_STATE}: the stack is held by a live engine object (summoned companion's control item, active enchant, casting). */
    public static final String ITEM_IN_USE = "ITEM_IN_USE";

    /** {@code INVALID_STATE}: the receiver's stack would pass its cap; refused because the engine would clamp it and lose the excess. */
    public static final String STACK_OVERFLOW = "STACK_OVERFLOW";

    /**
     * Boolean extension of a slot-capacity refusal: {@code false} when the receiver is out of the world and judged by a base
     * limit that never exceeds the real one, so the refusal may be false.
     */
    public static final String EXT_LIMITS_EXACT = "limitsExact";
}
