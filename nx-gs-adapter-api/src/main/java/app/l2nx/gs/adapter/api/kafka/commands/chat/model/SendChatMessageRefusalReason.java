package app.l2nx.gs.adapter.api.kafka.commands.chat.model;

import app.l2nx.gs.adapter.api.kafka.commands.chat.SendChatMessageRefusal;

/**
 * Wire value of {@link SendChatMessageRefusal#REASON} is the constant name. A missing clan or alliance is
 * {@code NOT_FOUND} without a reason.
 *
 * <table>
 *   <caption>Status per reason</caption>
 *   <tr><th>CommandStatus</th><th>Reasons</th></tr>
 *   <tr><td>{@code VALIDATION_FAILED}</td><td>{@link #ITEM_NOT_OWNED}</td></tr>
 *   <tr><td>{@code FORBIDDEN}</td><td>{@link #CHAT_FORBIDDEN}</td></tr>
 *   <tr><td>{@code NOT_FOUND}</td><td>{@link #TARGET_NOT_FOUND}</td></tr>
 *   <tr><td>{@code INVALID_STATE}</td><td>every other reason</td></tr>
 * </table>
 */
public enum SendChatMessageRefusalReason {

    /**
     * A chat gate of the game refuses the sender or the addressee: chat ban, level floor, jail, the addressee's block
     * list or message refusal mode.
     */
    CHAT_FORBIDDEN,

    NOT_IN_CLAN,

    NOT_IN_ALLIANCE,

    NOT_IN_PARTY,

    TARGET_NOT_FOUND,

    /** A linked item is missing or not the sender's. */
    ITEM_NOT_OWNED,

    /** The server's chat with the app is off; only a send from a character is refused. */
    CHAT_DISABLED,

    /** The channel is outside the server's list for the sender's state, in game or out of it. */
    CHANNEL_DISABLED,

    TARGET_OFFLINE
}
