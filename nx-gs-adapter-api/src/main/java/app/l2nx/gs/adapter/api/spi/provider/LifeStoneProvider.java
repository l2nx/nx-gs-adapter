package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone.LifeStone;
import java.util.Collection;

/**
 * Source of the augmentation stones for the {@code gd-sync} module, discovered via {@link java.util.ServiceLoader}.
 */
public interface LifeStoneProvider {

    /** gd-sync entity name; always {@code "lifestone"}. */
    String entityName();

    /**
     * Full current set, one record per stone item template. {@code null} means the host is not ready: the burst is
     * aborted.
     */
    Collection<LifeStone> snapshot();
}
