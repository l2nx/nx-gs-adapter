package app.l2nx.gs.gd.sync;

import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.targetgroup.AugmentationTargetGroup;
import app.l2nx.gs.adapter.api.spi.provider.AugmentationTargetGroupProvider;
import java.util.Collection;
import java.util.Collections;

public final class TestAugmentationTargetGroupProvider implements AugmentationTargetGroupProvider {

    static volatile Collection<AugmentationTargetGroup> snapshot = Collections.emptyList();

    @Override
    public String entityName() {
        return "augmentationtargetgroup";
    }

    @Override
    public Collection<AugmentationTargetGroup> snapshot() {
        return snapshot;
    }
}
