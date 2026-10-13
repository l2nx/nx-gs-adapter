package app.l2nx.gs.adapter.api.kafka.commands.mail.model;

import app.l2nx.gs.adapter.api.kafka.commands.mail.AcceptMailCommand;

/**
 * Canonical {@code extensions.reason} values on refusals of {@link AcceptMailCommand}. Open string so a new reason is
 * not a breaking change; the host never sends player-facing text. Malformed commands carry {@code field} instead,
 * {@code COMMAND_EXPIRED} neither.
 *
 * <p>{@code NOT_FOUND}: {@link #MAIL_NOT_FOUND} (also a different recipient, sender or COD).</p>
 * <p>{@code FORBIDDEN}: {@link #CHARACTER_BANNED}, {@link #TRANSACTIONS_FORBIDDEN}, {@link #ITEM_LOCKED},
 * {@link #RECEIVE_FORBIDDEN}.</p>
 * <p>{@code INVALID_STATE}: {@link #NO_ATTACHMENTS}, {@link #INVENTORY_FULL}, {@link #OVERWEIGHT},
 * {@link #NOT_ENOUGH_ADENA}, {@link #ADENA_OVERFLOW}, {@link #RECEIVE_FEE_REQUIRED}, {@link #RECEIVER_DATA_UNAVAILABLE},
 * {@link #CHARACTER_BUSY}, {@link #STORE_OPEN}, {@link #RESTORE_IN_PROGRESS}.</p>
 * <p>{@code INTERNAL_ERROR}: {@link #OUTCOME_UNKNOWN}.</p>
 */
public final class WellKnownAcceptMailReasons {

    private WellKnownAcceptMailReasons() {}

    public static final String MAIL_NOT_FOUND = "MAIL_NOT_FOUND";

    public static final String CHARACTER_BANNED = "CHARACTER_BANNED";
    public static final String TRANSACTIONS_FORBIDDEN = "TRANSACTIONS_FORBIDDEN";
    public static final String ITEM_LOCKED = "ITEM_LOCKED";
    public static final String RECEIVE_FORBIDDEN = "RECEIVE_FORBIDDEN";

    public static final String NO_ATTACHMENTS = "NO_ATTACHMENTS";
    public static final String INVENTORY_FULL = "INVENTORY_FULL";
    public static final String OVERWEIGHT = "OVERWEIGHT";
    public static final String NOT_ENOUGH_ADENA = "NOT_ENOUGH_ADENA";
    public static final String ADENA_OVERFLOW = "ADENA_OVERFLOW";
    public static final String RECEIVE_FEE_REQUIRED = "RECEIVE_FEE_REQUIRED";
    public static final String RECEIVER_DATA_UNAVAILABLE = "RECEIVER_DATA_UNAVAILABLE";
    public static final String CHARACTER_BUSY = "CHARACTER_BUSY";
    public static final String STORE_OPEN = "STORE_OPEN";
    public static final String RESTORE_IN_PROGRESS = "RESTORE_IN_PROGRESS";

    public static final String COMMAND_EXPIRED = "COMMAND_EXPIRED";
    public static final String OUTCOME_UNKNOWN = "OUTCOME_UNKNOWN";

    /** {@link #NOT_ENOUGH_ADENA}: COD plus receive fee. */
    public static final String EXT_REQUIRED_ADENA = "requiredAdena";

    public static final String EXT_AVAILABLE_ADENA = "availableAdena";
    public static final String EXT_REQUIRED_SLOTS = "requiredSlots";
    public static final String EXT_FREE_SLOTS = "freeSlots";
    public static final String EXT_REQUIRED_WEIGHT = "requiredWeight";
    public static final String EXT_FREE_WEIGHT = "freeWeight";
    public static final String EXT_FEE_ADENA = "feeAdena";
}
