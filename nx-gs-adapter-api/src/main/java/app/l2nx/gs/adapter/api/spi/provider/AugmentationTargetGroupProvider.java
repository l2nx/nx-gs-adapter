package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.targetgroup.AugmentationTargetGroup;
import java.util.Collection;

/**
 * Source of the augmentation target groups for the {@code gd-sync} module, discovered via
 * {@link java.util.ServiceLoader}. Group ids must stay the same across snapshots: life stones reference them.
 */
public interface AugmentationTargetGroupProvider {

    /** gd-sync entity name; always {@code "augmentationtargetgroup"}. */
    String entityName();

    /** Full current set, one record per group. {@code null} means the host is not ready: the burst is aborted. */
    Collection<AugmentationTargetGroup> snapshot();
}
