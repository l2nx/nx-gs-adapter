package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingShot;
import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Source of the fishing shot catalog for the {@code gd-sync} module, discovered via {@link ServiceLoader}.
 * A server without fishing registers no provider.
 */
public interface FishingShotProvider {

    /** gd-sync entity name; always {@code "fishingshot"}. */
    String entityName();

    /**
     * One record per item the server charges as a fishing shot; empty when it charges none. {@code null} means the
     * host is not ready: the burst is aborted.
     */
    Collection<FishingShot> snapshot();
}
