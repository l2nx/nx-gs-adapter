package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import app.l2nx.gs.adapter.api.domain.item.AttributeTarget;
import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import java.util.Objects;

public final class AttributeLimit {

    private final ItemGrade grade;
    private final AttributeItemType type;
    private final AttributeTarget target;
    private final int maxPower;

    public AttributeLimit(ItemGrade grade, AttributeItemType type, AttributeTarget target, int maxPower) {
        if (maxPower < 0) {
            throw new IllegalArgumentException("AttributeLimit.maxPower must not be negative");
        }
        this.grade = Objects.requireNonNull(grade, "AttributeLimit.grade is required");
        this.type = Objects.requireNonNull(type, "AttributeLimit.type is required");
        this.target = Objects.requireNonNull(target, "AttributeLimit.target is required");
        this.maxPower = maxPower;
    }

    public ItemGrade getGrade() {
        return grade;
    }

    public AttributeItemType getType() {
        return type;
    }

    public AttributeTarget getTarget() {
        return target;
    }

    /** {@code 0}: this item type cannot be used on that grade. */
    public int getMaxPower() {
        return maxPower;
    }

    public Builder toBuilder() {
        return new Builder().grade(grade).type(type).target(target).maxPower(maxPower);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeLimit)) return false;
        AttributeLimit that = (AttributeLimit) o;
        return maxPower == that.maxPower && grade == that.grade && type == that.type && target == that.target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(grade, type, target, maxPower);
    }

    @Override
    public String toString() {
        return "AttributeLimit[grade=" + grade + ", type=" + type + ", target=" + target + ", maxPower=" + maxPower
                + "]";
    }

    public static final class Builder {
        private ItemGrade grade;
        private AttributeItemType type;
        private AttributeTarget target;
        private int maxPower;

        public Builder grade(ItemGrade grade) {
            this.grade = grade;
            return this;
        }

        public Builder type(AttributeItemType type) {
            this.type = type;
            return this;
        }

        public Builder target(AttributeTarget target) {
            this.target = target;
            return this;
        }

        public Builder maxPower(int maxPower) {
            this.maxPower = maxPower;
            return this;
        }

        public AttributeLimit build() {
            return new AttributeLimit(grade, type, target, maxPower);
        }
    }
}
