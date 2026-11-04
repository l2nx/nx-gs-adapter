package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.spi.ConnectContext;

/**
 * The start gate of runtime sync: ticks run only once the module has started and the host has declared its start, so no
 * runtime event of a server generation carries a time before that generation's {@code started_at}. Obtained through
 * {@link ConnectContext#runtimeSync()}, or the static {@code NxAdapter.runtimeSync()} for host code outside a module;
 * both are one instance, usable from JVM start. A context built without it gets a no-op that never fires.
 */
public interface NxRuntimeSync {

    /**
     * The host's call, once, right after it publishes {@code ServerStartedEvent}, whether that event went out or is held
     * until the events channel binds. A one-way fact of this JVM: safe from any thread, early and repeated calls
     * included, and it outlives a reconnect. There is no timer and no fallback: a host that never calls it gets no ticks.
     */
    void hostStarted();

    /**
     * The module's side: {@code callback} runs once the host has declared its start, at once on the calling thread when
     * it already has, otherwise on the thread of {@link #hostStarted()}. The last registration wins.
     */
    void onHostStarted(Runnable callback);
}
