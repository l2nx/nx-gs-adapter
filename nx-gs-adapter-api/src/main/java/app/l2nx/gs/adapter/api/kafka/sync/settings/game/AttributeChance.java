package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import app.l2nx.gs.adapter.api.domain.item.AttributeTarget;
import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A {@code null} grade means any grade, a {@code null} target both. */
public final class AttributeChance {

    private final @Nullable ItemGrade grade;
    private final AttributeItemType type;
    private final @Nullable AttributeTarget target;
    private final double chancePercent;

    public AttributeChance(
            @Nullable ItemGrade grade, AttributeItemType type, @Nullable AttributeTarget target, double chancePercent) {
        if (!(chancePercent >= 0 && chancePercent <= 100)) {
            throw new IllegalArgumentException("AttributeChance.chancePercent must be within 0..100");
        }
        this.grade = grade;
        this.type = Objects.requireNonNull(type, "AttributeChance.type is required");
        this.target = target;
        this.chancePercent = chancePercent;
    }

    public @Nullable ItemGrade getGrade() {
        return grade;
    }

    public AttributeItemType getType() {
        return type;
    }

    public @Nullable AttributeTarget getTarget() {
        return target;
    }

    public double getChancePercent() {
        return chancePercent;
    }

    public Builder toBuilder() {
        return new Builder().grade(grade).type(type).target(target).chancePercent(chancePercent);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeChance)) return false;
        AttributeChance that = (AttributeChance) o;
        return Double.compare(chancePercent, that.chancePercent) == 0
                && grade == that.grade
                && type == that.type
                && target == that.target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(grade, type, target, chancePercent);
    }

    @Override
    public String toString() {
        return "AttributeChance[grade=" + grade + ", type=" + type + ", target=" + target + ", chancePercent="
                + chancePercent + "]";
    }

    public static final class Builder {
        private @Nullable ItemGrade grade;
        private AttributeItemType type;
        private @Nullable AttributeTarget target;
        private double chancePercent;

        public Builder grade(@Nullable ItemGrade grade) {
            this.grade = grade;
            return this;
        }

        public Builder type(AttributeItemType type) {
            this.type = type;
            return this;
        }

        public Builder target(@Nullable AttributeTarget target) {
            this.target = target;
            return this;
        }

        public Builder chancePercent(double chancePercent) {
            this.chancePercent = chancePercent;
            return this;
        }

        public AttributeChance build() {
            return new AttributeChance(grade, type, target, chancePercent);
        }
    }
}
