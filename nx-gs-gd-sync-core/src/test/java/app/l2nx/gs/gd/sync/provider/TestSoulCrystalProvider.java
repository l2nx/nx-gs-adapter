package app.l2nx.gs.gd.sync.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.soulcrystal.SoulCrystal;
import app.l2nx.gs.adapter.api.spi.provider.SoulCrystalProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestSoulCrystalProvider implements SoulCrystalProvider {

    public static volatile Collection<SoulCrystal> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "soulcrystal";
    }

    @Override
    public Collection<SoulCrystal> snapshot() {
        return snapshot;
    }
}
