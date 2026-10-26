package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingRod;
import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Source of the fishing rod catalog for the {@code gd-sync} module, discovered via {@link ServiceLoader}.
 * A server without fishing registers no provider.
 */
public interface FishingRodProvider {

    /** gd-sync entity name; always {@code "fishingrod"}. */
    String entityName();

    /**
     * One record per item the server accepts as a fishing rod; empty when it accepts none. {@code null} means the host
     * is not ready: the burst is aborted.
     */
    Collection<FishingRod> snapshot();
}
