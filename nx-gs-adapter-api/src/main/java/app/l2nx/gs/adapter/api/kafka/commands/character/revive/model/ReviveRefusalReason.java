package app.l2nx.gs.adapter.api.kafka.commands.character.revive.model;

import app.l2nx.gs.adapter.api.kafka.commands.character.revive.ReviveRefusal;

/**
 * Wire value of {@link ReviveRefusal#REASON} is the constant name; no player-facing text. A missing character is
 * {@code NOT_FOUND} without a reason, a passed deadline {@code COMMAND_EXPIRED}.
 *
 * <table>
 *   <caption>Status per reason</caption>
 *   <tr><th>CommandStatus</th><th>Reasons</th></tr>
 *   <tr><td>{@code VALIDATION_FAILED}</td><td>{@link #RESTART_POINT_UNSUPPORTED}</td></tr>
 *   <tr><td>{@code FORBIDDEN}</td><td>{@link #CHARACTER_BANNED}, {@link #CHARACTER_JAILED}</td></tr>
 *   <tr><td>{@code INVALID_STATE}</td><td>every other reason</td></tr>
 * </table>
 */
public enum ReviveRefusalReason {
    FEATURE_DISABLED,

    RESTART_POINT_UNSUPPORTED,

    /** The restart point exists but not for this character (no clan hall, no castle); never for {@code TOWN}. */
    RESTART_POINT_UNAVAILABLE,

    CHARACTER_BANNED,

    CHARACTER_JAILED,

    NOT_DEAD,

    /** Dead in an instance, olympiad, event, siege or on a vehicle. */
    UNSAFE_LOGOUT_LOCATION
}
