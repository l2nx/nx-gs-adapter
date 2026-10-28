package app.l2nx.gs.adapter.api.kafka.events.chat.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Open vocabulary: a channel this enum does not name travels as {@link #unknownToken(int) UNKNOWN_<int>}, so the wire
 * fields stay strings and consumers read them with {@link #fromToken(String)}.
 */
public enum ChatChannel {
    GENERAL,
    SHOUT,

    /** Private tell; {@code targetCharId} / {@code targetCharName} are set. */
    WHISPER,

    PARTY,
    CLAN,
    ALLIANCE,
    TRADE,
    WORLD,
    HERO,
    GM,
    PETITION,
    PETITION_GM,
    ANNOUNCEMENT,
    CRITICAL_ANNOUNCEMENT,
    SCREEN_ANNOUNCEMENT,
    BATTLEFIELD,
    BOAT,
    FRIEND,

    /** External IM relay. */
    MSN,

    PARTY_ROOM,
    COMMAND_CHANNEL,

    /** Command-channel leaders only. */
    COMMAND_CHANNEL_COMMANDER,

    NPC_GENERAL,
    NPC_SHOUT,
    NPC_WHISPER;

    private static final String UNKNOWN_PREFIX = "UNKNOWN_";

    /** A channel a player may write to from the app ({@code SendChatMessageCommand} with a sender character). */
    public boolean sendableFromApp() {
        return this == CLAN || this == ALLIANCE || this == PARTY || this == WHISPER;
    }

    /** {@link #sendableFromApp()} while the sender is out of game; a party exists only in game. */
    public boolean sendableOffline() {
        return sendableFromApp() && this != PARTY;
    }

    /** {@code null} for a token this api does not know, {@code UNKNOWN_<int>} included. */
    public static @Nullable ChatChannel fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ChatChannel.class, token);
    }

    /** The token of a build's chat type that no constant names; {@code rawType} is the build's own number. */
    public static String unknownToken(int rawType) {
        return UNKNOWN_PREFIX + rawType;
    }
}
