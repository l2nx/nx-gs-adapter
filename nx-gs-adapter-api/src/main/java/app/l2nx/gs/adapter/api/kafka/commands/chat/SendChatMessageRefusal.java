package app.l2nx.gs.adapter.api.kafka.commands.chat;

/**
 * Keys a {@code SendChatMessageCommand} refusal puts in {@code CommandProblem.extensions}; {@link #REASON} holds a
 * {@code SendChatMessageRefusalReason} name.
 */
public final class SendChatMessageRefusal {

    private SendChatMessageRefusal() {}

    public static final String REASON = "reason";
}
