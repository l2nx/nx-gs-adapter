package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * {@code extensions.reason} tokens of start and stop refusals, one set for both; the wire token is {@link #name()}.
 * Open vocabulary: consumers tolerate unknown tokens.
 */
public enum PrivateStoreReason {
    IN_COMBAT,
    SELLING_BUFFS,
    CANNOT_ACT,
    NOT_IN_TRADE_ZONE,
    NO_FREE_SEAT,
    ADENA_OVERFLOW,
    TOO_MANY_LINES,
    CHARACTER_BANNED,
    CHARACTER_JAILED,
    OFFLINE_FEE_UNPAID,

    /** {@code INVALID_STATE}: the command carries {@code offlineAllowed = false}. */
    OFFLINE_QUOTA_EXCEEDED,

    /** {@code INVALID_STATE}: the start is offline-only and the character is in the game with a client. */
    CHARACTER_ONLINE,

    /** {@code INVALID_STATE} with {@code retryAt}: the character left the game too recently for an offline start. */
    RECENTLY_LOGGED_OUT,

    RESTORE_IN_PROGRESS,
    COMMAND_EXPIRED,
    ITEM_LOCKED,
    TRANSACTIONS_FORBIDDEN,
    TITLE_TOO_LONG,

    /** {@code VALIDATION_FAILED}: a line cannot be listed; the per-line reasons are in {@link DroppedLine#EXTENSION}. */
    LINES_REJECTED,

    DUPLICATE_ITEM_ID,

    DUPLICATE_ITEM_TEMPLATE_ID,

    /** {@code INVALID_STATE}: owner's adena does not cover the buy list plus buyer fee (and offline price, if in adena). */
    NOT_ENOUGH_ADENA,

    STORE_NOT_OPEN;

    public static @Nullable PrivateStoreReason fromToken(@Nullable String token) {
        return Vocabularies.fromToken(PrivateStoreReason.class, token);
    }
}
