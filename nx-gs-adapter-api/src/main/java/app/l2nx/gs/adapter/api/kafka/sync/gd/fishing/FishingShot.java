package app.l2nx.gs.adapter.api.kafka.sync.gd.fishing;

import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@code grade} is collapsed like {@link FishingRod#getShotGrade()} so the two compare with {@code equals}, never
 * {@link ItemGrade#EVENT}; {@code null} means the shot fits any rod.
 */
public final class FishingShot {

    private final int itemTemplateId;
    private final @Nullable ItemGrade grade;

    public FishingShot(int itemTemplateId, @Nullable ItemGrade grade) {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId must be positive, got " + itemTemplateId);
        }
        if (grade == ItemGrade.EVENT) {
            throw new IllegalArgumentException("grade must not be EVENT");
        }
        this.itemTemplateId = itemTemplateId;
        this.grade = grade;
    }

    public int getItemTemplateId() {
        return itemTemplateId;
    }

    public @Nullable ItemGrade getGrade() {
        return grade;
    }

    public Builder toBuilder() {
        return new Builder().itemTemplateId(itemTemplateId).grade(grade);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FishingShot)) return false;
        FishingShot that = (FishingShot) o;
        return itemTemplateId == that.itemTemplateId && grade == that.grade;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, grade);
    }

    @Override
    public String toString() {
        return "FishingShot[itemTemplateId=" + itemTemplateId + ", grade=" + grade + "]";
    }

    public static final class Builder {
        private int itemTemplateId;
        private @Nullable ItemGrade grade;

        public Builder itemTemplateId(int itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder grade(@Nullable ItemGrade grade) {
            this.grade = grade;
            return this;
        }

        public FishingShot build() {
            return new FishingShot(itemTemplateId, grade);
        }
    }
}
