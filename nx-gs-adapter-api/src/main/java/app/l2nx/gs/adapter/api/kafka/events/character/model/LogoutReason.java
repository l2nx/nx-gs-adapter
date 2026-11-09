package app.l2nx.gs.adapter.api.kafka.events.character.model;

/** Why a presence session ended; required on every {@code online=false} presence event. */
public enum LogoutReason {
    LOGOUT,

    DISCONNECT,

    /**
     * Client left while the character stays as an offline trader: the game session ends and a store session opens under
     * a new id ({@code source = CLIENT}, {@code activity = OFFLINE_PRIVATE_STORE}).
     */
    OFFLINE_PRIVATE_STORE,

    /** Native offline store session: everything is sold or bought. An app store ends with {@link #APP_SOLD_OUT}. */
    SOLD_OUT,

    /** Client session: sent for every player still in the world at server shutdown. */
    SERVER_STOP,

    /** Client or app session: the character or account was banned. A client session reports a ban as this, not as {@link #DISCONNECT}. */
    BANNED,

    /** Client or app session: the character was kicked. A client session reports a kick as this, not as {@link #DISCONNECT}. */
    KICKED,

    /** App session: its plan ran out, or a store's paid offline time did (reported by the host). */
    APP_TIME_UP,

    APP_STOPPED,

    /** App session: stopped after a host restart before the restore reached the character. */
    APP_STOPPED_BEFORE_RESTORE,

    /** App session: the player logged in with a client, which ends the session before the live character loads. */
    APP_LOGIN,

    APP_OUT_OF_BAIT,

    APP_BAIT_LEVEL_MISMATCH,

    APP_NO_ROD,

    APP_DAILY_LIMIT,

    APP_INVENTORY_FULL,

    APP_OVERWEIGHT,

    APP_SOLD_OUT,

    /**
     * App session: the game stopped the placed character's action (it was moved off its spot, the store was closed by
     * the game, or the action could not resume). Not a staff kick ({@link #KICKED}) and not the player's stop
     * ({@link #APP_STOPPED}).
     */
    APP_INTERRUPTED,

    /** App session: restoring after a host restart failed, e.g. no free spot. */
    APP_RESTORE_FAILED
}
