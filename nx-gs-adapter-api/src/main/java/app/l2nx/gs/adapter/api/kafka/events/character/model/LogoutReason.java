package app.l2nx.gs.adapter.api.kafka.events.character.model;

/** Why a presence session ended; required on every {@code online=false} presence event. */
public enum LogoutReason {
    LOGOUT,

    DISCONNECT,

    /**
     * Client session: the client left while the character stays in the world as an offline trader. No session is
     * opened for the trader afterwards, including when the host restores it after a restart.
     */
    OFFLINE_PRIVATE_STORE,

    /** Client session: sent for every player still in the world at server shutdown. */
    SERVER_STOP,

    /** Client or app session: the character or account was banned. A client session reports a ban as this, not as {@link #DISCONNECT}. */
    BANNED,

    /** Client or app session: the character was kicked. A client session reports a kick as this, not as {@link #DISCONNECT}. */
    KICKED,

    APP_TIME_UP,

    APP_STOPPED,

    /** App session: stopped after a host restart before the restore reached the character. */
    APP_STOPPED_BEFORE_RESTORE,

    /** App session: the player logged in with a client, which ends the session before the live character loads. */
    APP_LOGIN,

    APP_OUT_OF_BAIT,

    APP_DAILY_LIMIT,

    APP_INVENTORY_FULL,

    APP_SOLD_OUT,

    /** App session: restoring after a host restart failed, e.g. no free spot. */
    APP_RESTORE_FAILED
}
