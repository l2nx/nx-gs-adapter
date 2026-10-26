package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingRod;
import app.l2nx.gs.adapter.api.spi.provider.FishingRodProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestFishingRodProvider implements FishingRodProvider {

    public static volatile Collection<FishingRod> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "fishingrod";
    }

    @Override
    public Collection<FishingRod> snapshot() {
        return snapshot;
    }
}
