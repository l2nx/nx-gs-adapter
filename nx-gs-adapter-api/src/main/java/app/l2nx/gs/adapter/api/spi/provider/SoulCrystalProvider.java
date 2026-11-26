package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.soulcrystal.SoulCrystal;
import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Source of the soul-crystal leveling chain for the {@code gd-sync} module, discovered via {@link ServiceLoader}.
 */
public interface SoulCrystalProvider {

    /** gd-sync entity name; always {@code "soulcrystal"}. */
    String entityName();

    /**
     * One record per soul-crystal item template; empty only when the build has no soul crystals. {@code null} means the
     * host is not ready: the burst is aborted.
     */
    Collection<SoulCrystal> snapshot();
}
