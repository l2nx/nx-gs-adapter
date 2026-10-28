package app.l2nx.gs.adapter.api.kafka.events.chat.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import app.l2nx.gs.adapter.api.kafka.events.chat.ChatMessageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Keys of the {@link ChatMessageEvent} {@code metadata} map; the wire string is {@link #key()}. Open vocabulary: hosts
 * may add keys, consumers ignore unknown ones.
 */
public enum ChatMetadataKey implements Vocabularies.Keyed {

    /** The command's {@code source} on the echo of a {@code SendChatMessageCommand}. */
    SOURCE("source"),

    /** {@code "true"} when delivered only to the speaker: store it (abuse signal) but show it only to the speaker. */
    SHADOWED("shadowed"),

    /** The build's own numeric chat type, decimal. */
    RAW_TYPE("rawType"),

    /** {@code "true"} when every online player received it, so no recipient list is sent; show it like {@code WORLD}. */
    SERVER_WIDE("serverWide"),

    /**
     * {@code "true"} when the message reached no game client of its addressee (e.g. a whisper to a character out of
     * game); absent when it did, or when there is no single addressee. Not tied to a channel.
     */
    UNDELIVERED("undelivered");

    private final String key;

    ChatMetadataKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    public static @Nullable ChatMetadataKey fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ChatMetadataKey.class, token);
    }
}
