package app.l2nx.gs.adapter.api.spi;

import app.l2nx.gs.adapter.api.spi.capability.NxRuntimeSync;

final class NoOpRuntimeSync implements NxRuntimeSync {

    static final NoOpRuntimeSync INSTANCE = new NoOpRuntimeSync();

    private NoOpRuntimeSync() {}

    @Override
    public void hostStarted() {}

    @Override
    public void onHostStarted(Runnable callback) {}
}
