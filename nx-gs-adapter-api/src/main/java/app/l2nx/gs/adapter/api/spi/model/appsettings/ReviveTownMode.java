package app.l2nx.gs.adapter.api.spi.model.appsettings;

public enum ReviveTownMode {
    NATIVE,
    /**
     * A random spot of {@link AppRules#reviveTownSpotPools()} no running app session holds, else any of them; a
     * character with karma still takes {@link #NATIVE}.
     */
    SPOT_POOL
}
