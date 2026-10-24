package app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.targetgroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Item templates that enter the augmentation roll the same way. {@code id} is the host's key, stable across snapshots. */
public final class AugmentationTargetGroup {

    private final int id;
    private final List<Integer> itemTemplateIds;

    public AugmentationTargetGroup(int id, List<Integer> itemTemplateIds) {
        this.id = id;
        this.itemTemplateIds = Collections.unmodifiableList(
                new ArrayList<Integer>(Objects.requireNonNull(itemTemplateIds, "itemTemplateIds")));
    }

    public int getId() {
        return id;
    }

    public List<Integer> getItemTemplateIds() {
        return itemTemplateIds;
    }

    public Builder toBuilder() {
        return new Builder().id(id).itemTemplateIds(itemTemplateIds);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AugmentationTargetGroup)) return false;
        AugmentationTargetGroup that = (AugmentationTargetGroup) o;
        return id == that.id && itemTemplateIds.equals(that.itemTemplateIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, itemTemplateIds);
    }

    @Override
    public String toString() {
        return "AugmentationTargetGroup[id=" + id + ", itemTemplateIds=" + itemTemplateIds.size() + "]";
    }

    public static final class Builder {
        private int id;
        private List<Integer> itemTemplateIds = Collections.emptyList();

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder itemTemplateIds(List<Integer> itemTemplateIds) {
            this.itemTemplateIds = itemTemplateIds;
            return this;
        }

        public AugmentationTargetGroup build() {
            return new AugmentationTargetGroup(id, itemTemplateIds);
        }
    }
}
