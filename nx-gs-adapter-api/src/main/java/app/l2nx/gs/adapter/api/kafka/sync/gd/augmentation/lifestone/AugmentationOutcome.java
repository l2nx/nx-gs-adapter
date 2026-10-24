package app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone;

import java.util.Objects;

/**
 * {@code chancePercent} (0 &lt; p &lt;= 100) is final, computed by the host's roll code; never recompute it.
 * {@code slot}: 1 = low 16 bits of the augmentation word, 2 = high 16 bits. Chances of different slots are not
 * independent and must not be combined.
 */
public final class AugmentationOutcome {

    private final int augmentationTargetGroupId;
    private final int slot;
    private final int itemOptionId;
    private final double chancePercent;

    public AugmentationOutcome(int augmentationTargetGroupId, int slot, int itemOptionId, double chancePercent) {
        if (slot != 1 && slot != 2) {
            throw new IllegalArgumentException("slot must be 1 or 2, got " + slot);
        }
        if (!(chancePercent > 0d && chancePercent <= 100d)) {
            throw new IllegalArgumentException("chancePercent must be within (0, 100], got " + chancePercent);
        }
        this.augmentationTargetGroupId = augmentationTargetGroupId;
        this.slot = slot;
        this.itemOptionId = itemOptionId;
        this.chancePercent = chancePercent;
    }

    public int getAugmentationTargetGroupId() {
        return augmentationTargetGroupId;
    }

    public int getSlot() {
        return slot;
    }

    public int getItemOptionId() {
        return itemOptionId;
    }

    public double getChancePercent() {
        return chancePercent;
    }

    public Builder toBuilder() {
        return new Builder()
                .augmentationTargetGroupId(augmentationTargetGroupId)
                .slot(slot)
                .itemOptionId(itemOptionId)
                .chancePercent(chancePercent);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AugmentationOutcome)) return false;
        AugmentationOutcome that = (AugmentationOutcome) o;
        return augmentationTargetGroupId == that.augmentationTargetGroupId
                && slot == that.slot
                && itemOptionId == that.itemOptionId
                && Double.compare(chancePercent, that.chancePercent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(augmentationTargetGroupId, slot, itemOptionId, chancePercent);
    }

    @Override
    public String toString() {
        return "AugmentationOutcome[augmentationTargetGroupId=" + augmentationTargetGroupId + ", slot=" + slot
                + ", itemOptionId=" + itemOptionId + ", chancePercent=" + chancePercent + "]";
    }

    public static final class Builder {
        private int augmentationTargetGroupId;
        private int slot;
        private int itemOptionId;
        private double chancePercent;

        public Builder augmentationTargetGroupId(int augmentationTargetGroupId) {
            this.augmentationTargetGroupId = augmentationTargetGroupId;
            return this;
        }

        public Builder slot(int slot) {
            this.slot = slot;
            return this;
        }

        public Builder itemOptionId(int itemOptionId) {
            this.itemOptionId = itemOptionId;
            return this;
        }

        public Builder chancePercent(double chancePercent) {
            this.chancePercent = chancePercent;
            return this;
        }

        public AugmentationOutcome build() {
            return new AugmentationOutcome(augmentationTargetGroupId, slot, itemOptionId, chancePercent);
        }
    }
}
