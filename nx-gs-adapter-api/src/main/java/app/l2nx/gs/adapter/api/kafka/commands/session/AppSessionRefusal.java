package app.l2nx.gs.adapter.api.kafka.commands.session;

import app.l2nx.gs.adapter.api.kafka.commands.session.model.AlreadyActiveScope;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;

/** Names of the {@code CommandProblem.extensions} entries of a refused app-session command. */
public final class AppSessionRefusal {

    private AppSessionRefusal() {}

    /** An {@link AppSessionRefusalReason} constant name. */
    public static final String REASON = "reason";

    /**
     * ISO-8601 UTC instant the player may retry at; set for {@code RECENTLY_LOGGED_OUT} and {@code COOLDOWN}, and for
     * {@code DAILY_LIMIT} (the moment the daily allowance resets) when the host knows it.
     */
    public static final String RETRY_AT = "retryAt";

    /** An {@link AlreadyActiveScope} constant name; set for {@code ALREADY_ACTIVE}. */
    public static final String SCOPE = "scope";

    /**
     * Id ({@code int}) of the character that holds the slot, in the game client or from the app; optional, only with
     * {@code ALREADY_ACTIVE}. Absent for scope {@code CHARACTER} (it is the character itself) and when the host does not
     * know it. Staff diagnostics: a platform never shows it, nor the scope, to players.
     */
    public static final String BLOCKED_BY_CHARACTER_ID = "blockedByCharacterId";

    public static final String REQUIRED_SLOTS = "requiredSlots";

    public static final String FREE_SLOTS = "freeSlots";

    public static final String REQUIRED_WEIGHT = "requiredWeight";

    public static final String FREE_WEIGHT = "freeWeight";
}
