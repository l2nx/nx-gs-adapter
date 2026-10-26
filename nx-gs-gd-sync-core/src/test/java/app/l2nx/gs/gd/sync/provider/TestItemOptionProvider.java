package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption.ItemOption;
import app.l2nx.gs.adapter.api.spi.provider.ItemOptionProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestItemOptionProvider implements ItemOptionProvider {

    public static volatile Collection<ItemOption> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "itemoption";
    }

    @Override
    public Collection<ItemOption> snapshot() {
        return snapshot;
    }
}
