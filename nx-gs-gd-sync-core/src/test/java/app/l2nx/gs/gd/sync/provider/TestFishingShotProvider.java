package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingShot;
import app.l2nx.gs.adapter.api.spi.provider.FishingShotProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestFishingShotProvider implements FishingShotProvider {

    public static volatile Collection<FishingShot> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "fishingshot";
    }

    @Override
    public Collection<FishingShot> snapshot() {
        return snapshot;
    }
}
