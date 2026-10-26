package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone.LifeStone;
import app.l2nx.gs.adapter.api.spi.provider.LifeStoneProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestLifeStoneProvider implements LifeStoneProvider {

    public static volatile Collection<LifeStone> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "lifestone";
    }

    @Override
    public Collection<LifeStone> snapshot() {
        return snapshot;
    }
}
