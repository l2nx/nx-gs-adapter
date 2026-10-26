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
}
