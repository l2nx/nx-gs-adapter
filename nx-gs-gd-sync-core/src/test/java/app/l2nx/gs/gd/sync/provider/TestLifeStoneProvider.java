package app.l2nx.gs.gd.sync;

import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone.LifeStone;
import app.l2nx.gs.adapter.api.spi.provider.LifeStoneProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestLifeStoneProvider implements LifeStoneProvider {

    static volatile Collection<LifeStone> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "lifestone";
    }

    @Override
    public Collection<LifeStone> snapshot() {
        return snapshot;
    }
}
