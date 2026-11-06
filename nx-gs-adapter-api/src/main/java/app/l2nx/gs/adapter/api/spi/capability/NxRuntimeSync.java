package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.spi.ConnectContext;

/**
 * The start gate of runtime sync: ticks run only once the module has started and the host has declared its start, so no
 * runtime event of a server generation carries a time before that generation's {@code started_at}. Both access paths
 * ({@link ConnectContext#runtimeSync()}, static {@code NxAdapter.runtimeSync()}) share one instance.
 */
public interface NxRuntimeSync {

    /**
     * The host's call, right after it publishes {@code ServerStartedEvent} (sent or held). Safe from any thread, early
     * and repeated calls included, and it outlives a reconnect. A host that never calls it gets no ticks.
     */
    void hostStarted();

    /**
     * The module's side: {@code callback} runs once the host has declared its start, at once on the calling thread when
     * it already has, otherwise on the thread of {@link #hostStarted()}. The last registration wins.
     */
    void onHostStarted(Runnable callback);
}
