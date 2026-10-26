package app.l2nx.gs.adapter.api.kafka.commands.character.revive;

/** Keys a revive refusal puts in {@code CommandProblem.extensions}; {@link #REASON} holds a {@code ReviveRefusalReason} name. */
public final class ReviveRefusal {

    private ReviveRefusal() {}

    public static final String REASON = "reason";
}
