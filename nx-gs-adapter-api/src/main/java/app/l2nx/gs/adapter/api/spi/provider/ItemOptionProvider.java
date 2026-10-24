package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption.ItemOption;
import java.util.Collection;

/**
 * Source of the item option catalog for the {@code gd-sync} module, discovered via {@link java.util.ServiceLoader}.
 */
public interface ItemOptionProvider {

    /** gd-sync entity name; always {@code "itemoption"}. */
    String entityName();

    /**
     * Every option the engine loaded, augmentation and enchant options alike, one record per option id. {@code null}
     * means the host is not ready: the burst is aborted.
     */
    Collection<ItemOption> snapshot();
}
