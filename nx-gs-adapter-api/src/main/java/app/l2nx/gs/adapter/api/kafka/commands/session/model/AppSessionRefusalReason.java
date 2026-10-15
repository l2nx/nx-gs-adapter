package app.l2nx.gs.adapter.api.kafka.commands.session.model;

import app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionRefusal;

/**
 * Stable {@code extensions.reason} of a refused app-session command; the wire value is the constant name and the host
 * never sends player-facing text. Extras travel under the keys of {@link AppSessionRefusal}.
 *
 * <p>The {@code CommandStatus} carrying each code:</p>
 * <table>
 *   <caption>Status per reason</caption>
 *   <tr><th>CommandStatus</th><th>Reasons</th></tr>
 *   <tr><td>{@code VALIDATION_FAILED}</td><td>{@link #INVALID_ROD}, {@link #INVALID_BAIT}, {@link #INVALID_DURATION}</td></tr>
 *   <tr><td>{@code FORBIDDEN}</td><td>{@link #CHARACTER_BANNED}, {@link #CHARACTER_JAILED}</td></tr>
 *   <tr><td>{@code INVALID_STATE}</td><td>{@link #NO_ROD}, {@link #NO_BAIT} and every other reason</td></tr>
 * </table>
 */
public enum AppSessionRefusalReason {

    /** Carries {@code retryAt}. */
    RECENTLY_LOGGED_OUT,

    /** Carries {@code retryAt}. */
    COOLDOWN,

    CHARACTER_ONLINE,

    DEAD,

    KARMA,

    CHARACTER_JAILED,

    CHARACTER_BANNED,

    /** The character logged out where a session may not start (olympiad, event, instance, siege). */
    UNSAFE_LOGOUT_LOCATION,

    IN_PRIVATE_STORE,

    NO_ROD,

    /** The chosen bait is missing or its count is zero. */
    NO_BAIT,

    INVALID_ROD,

    INVALID_BAIT,

    DAILY_LIMIT,

    RESTART_SOON,

    INVALID_DURATION,

    FEATURE_DISABLED,

    NO_FREE_SPOT,

    /** Carries {@code scope}. */
    ALREADY_ACTIVE
}
