package app.l2nx.gs.adapter.api.spi.model.appsettings;

/** How the host picks a free spot of a pool for a new session. */
public enum SpotOrder {
    RANDOM,
    /** The free spot with the lowest id. */
    FIRST_FREE
}
