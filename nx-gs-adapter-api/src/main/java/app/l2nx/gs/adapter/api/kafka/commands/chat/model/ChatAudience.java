package app.l2nx.gs.adapter.api.kafka.commands.chat.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Who a {@code SendChatMessageCommand} goes to, orthogonal to its channel. Carried as a string so a host that does not
 * know a newer audience refuses it with {@code VALIDATION_FAILED}.
 */
public enum ChatAudience {

    /** The addressee may be offline: no packet is sent, but the message is gated and echoed. */
    CHARACTER,

    CLAN,

    ALLIANCE,

    PARTY,

    ALL_ONLINE;

    /** {@code null} for a token this api does not know. */
    public static @Nullable ChatAudience fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ChatAudience.class, token);
    }
}
