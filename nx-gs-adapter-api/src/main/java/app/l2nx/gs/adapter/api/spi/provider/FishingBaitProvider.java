package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingBait;
import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Source of the fishing bait catalog for the {@code gd-sync} module, discovered via {@link ServiceLoader}.
 * A server without fishing registers no provider.
 */
public interface FishingBaitProvider {

    /** gd-sync entity name; always {@code "fishingbait"}. */
    String entityName();

    /**
     * One record per item the server accepts in the bait slot, whatever its item template type says; empty when it
     * accepts none. {@code null} means the host is not ready: the burst is aborted.
     */
    Collection<FishingBait> snapshot();
}
