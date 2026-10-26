package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.itemtemplate.ItemTemplate;
import app.l2nx.gs.adapter.api.spi.provider.ItemTemplateProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link #callCount} tracks {@link #snapshot()} invocations to prove the readiness gate never touches the provider. */
public final class TestItemTemplateProvider implements ItemTemplateProvider {

    public static volatile Collection<ItemTemplate> snapshot = Collections.emptyList();
    public static final AtomicInteger callCount = new AtomicInteger();

    @Override
    public String entityName() {
        return "itemtemplate";
    }

    @Override
    public Collection<ItemTemplate> snapshot() {
        callCount.incrementAndGet();
        return snapshot;
    }
}
