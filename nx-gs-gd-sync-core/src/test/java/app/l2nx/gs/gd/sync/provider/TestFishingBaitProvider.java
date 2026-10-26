package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingBait;
import app.l2nx.gs.adapter.api.spi.provider.FishingBaitProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestFishingBaitProvider implements FishingBaitProvider {

    public static volatile Collection<FishingBait> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "fishingbait";
    }

    @Override
    public Collection<FishingBait> snapshot() {
        return snapshot;
    }
}
