package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

/** Canonical {@code extensions.reason} values specific to the private-store start commands; the set is open. */
public final class WellKnownStartPrivateStoreReasons {

    private WellKnownStartPrivateStoreReasons() {}

    public static final String LINES_REJECTED = "LINES_REJECTED";

    /** Name of the problem extension carrying the {@code List<DroppedLine>} of a {@link #LINES_REJECTED} refusal. */
    public static final String DROPPED = "dropped";
}
