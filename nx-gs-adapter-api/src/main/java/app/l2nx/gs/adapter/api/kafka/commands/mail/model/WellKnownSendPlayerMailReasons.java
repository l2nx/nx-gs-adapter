package app.l2nx.gs.adapter.api.kafka.commands.mail.model;

import app.l2nx.gs.adapter.api.kafka.commands.mail.SendPlayerMailCommand;

/**
 * Canonical {@code extensions.reason} values on refusals of {@link SendPlayerMailCommand}. Open string so a new reason
 * is not a breaking change; the host never sends player-facing text. Malformed commands carry {@code field} instead,
 * {@code COMMAND_EXPIRED} neither.
 *
 * <p>{@code NOT_FOUND}: {@link #RECIPIENT_NOT_FOUND}.</p>
 * <p>{@code FORBIDDEN}: {@link #CHARACTER_BANNED}, {@link #RECIPIENT_BANNED}, {@link #CHARACTER_JAILED},
 * {@link #TRANSACTIONS_FORBIDDEN}, {@link #ITEM_LOCKED}, {@link #RECIPIENT_BLOCKED_SENDER}, {@link #RECIPIENT_GM_ONLY}.</p>
 * <p>{@code VALIDATION_FAILED}: {@link #SELF_SEND}, {@link #TOO_MANY_ATTACHMENTS}, {@link #TOO_MANY_LINES},
 * {@link #ADENA_OVERFLOW}, {@link #COD_REQUIRES_ITEMS}, {@link #ITEM_NOT_TRANSFERABLE} (with an {@code itemId}
 * extension).</p>
 * <p>{@code INVALID_STATE}: {@link #NOT_ENOUGH_ADENA}, {@link #RECIPIENT_INBOX_FULL}, {@link #OUTBOX_FULL},
 * {@link #CHARACTER_BUSY}, {@link #MAIL_DISABLED}, {@link #ATTACHMENTS_DISABLED}, {@link #LEVEL_TOO_LOW},
 * {@link #RESTORE_IN_PROGRESS}.</p>
 * <p>{@code INTERNAL_ERROR}: {@link #MAIL_DELIVERY_FAILED}, {@link #OUTCOME_UNKNOWN}.</p>
 */
public final class WellKnownSendPlayerMailReasons {

    private WellKnownSendPlayerMailReasons() {}

    public static final String RECIPIENT_NOT_FOUND = "RECIPIENT_NOT_FOUND";

    public static final String CHARACTER_BANNED = "CHARACTER_BANNED";
    public static final String RECIPIENT_BANNED = "RECIPIENT_BANNED";
    public static final String CHARACTER_JAILED = "CHARACTER_JAILED";
    public static final String TRANSACTIONS_FORBIDDEN = "TRANSACTIONS_FORBIDDEN";
    public static final String ITEM_LOCKED = "ITEM_LOCKED";
    public static final String RECIPIENT_BLOCKED_SENDER = "RECIPIENT_BLOCKED_SENDER";
    public static final String RECIPIENT_GM_ONLY = "RECIPIENT_GM_ONLY";

    public static final String SELF_SEND = "SELF_SEND";
    public static final String TOO_MANY_ATTACHMENTS = "TOO_MANY_ATTACHMENTS";
    public static final String TOO_MANY_LINES = "TOO_MANY_LINES";
    public static final String ADENA_OVERFLOW = "ADENA_OVERFLOW";
    public static final String COD_REQUIRES_ITEMS = "COD_REQUIRES_ITEMS";
    public static final String ITEM_NOT_TRANSFERABLE = "ITEM_NOT_TRANSFERABLE";

    public static final String NOT_ENOUGH_ADENA = "NOT_ENOUGH_ADENA";
    public static final String RECIPIENT_INBOX_FULL = "RECIPIENT_INBOX_FULL";
    public static final String OUTBOX_FULL = "OUTBOX_FULL";
    public static final String CHARACTER_BUSY = "CHARACTER_BUSY";
    public static final String MAIL_DISABLED = "MAIL_DISABLED";
    public static final String ATTACHMENTS_DISABLED = "ATTACHMENTS_DISABLED";
    public static final String LEVEL_TOO_LOW = "LEVEL_TOO_LOW";
    public static final String RESTORE_IN_PROGRESS = "RESTORE_IN_PROGRESS";

    public static final String MAIL_DELIVERY_FAILED = "MAIL_DELIVERY_FAILED";
    public static final String OUTCOME_UNKNOWN = "OUTCOME_UNKNOWN";
}
