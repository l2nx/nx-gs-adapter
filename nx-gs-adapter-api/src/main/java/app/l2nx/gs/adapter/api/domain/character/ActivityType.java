package app.l2nx.gs.adapter.api.domain.character;

/**
 * Closed set of character activities; the wire value is the constant name and a host never invents a token. A new
 * activity ships in an api release first, then the platform, then the host.
 */
public enum ActivityType {

    /** Fishing by a character with a client attached. */
    FISHING,

    READING,

    AUTOFARMING,

    AUTOMACRO,

    /** A private store of a character with a client attached. */
    PRIVATE_STORE,

    /** A private store running without a client: left by a disconnected client, or opened as an app session. */
    OFFLINE_PRIVATE_STORE,

    /** Fishing as an app session, without a client. */
    OFFLINE_FISHING
}
