package app.l2nx.gs.adapter.api.kafka.sync.gd.fishing;

import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@code fishingTimeMs} (positive) overrides the bait's fishing time; {@code null} when the build times by the bait.
 * {@code fishingTimeReductionMs} (not negative) is taken off the bait's time, {@code chanceBonusPercent} (-100..100) is
 * added to the bait's chance in points; {@code null} means none.
 *
 * <p>{@code shotGrade} is the shot grade collapsed to its main grade ({@code S80} becomes {@code S}), never
 * {@link ItemGrade#EVENT}; {@code null} means no grade check.</p>
 */
public final class FishingRod {

    private final int itemTemplateId;
    private final @Nullable Long fishingTimeMs;
    private final @Nullable Long fishingTimeReductionMs;
    private final @Nullable Double chanceBonusPercent;
    private final @Nullable ItemGrade shotGrade;

    public FishingRod(
            int itemTemplateId,
            @Nullable Long fishingTimeMs,
            @Nullable Long fishingTimeReductionMs,
            @Nullable Double chanceBonusPercent,
            @Nullable ItemGrade shotGrade) {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId must be positive, got " + itemTemplateId);
        }
        if (fishingTimeMs != null && fishingTimeMs <= 0L) {
            throw new IllegalArgumentException("fishingTimeMs must be positive, got " + fishingTimeMs);
        }
        if (fishingTimeReductionMs != null && fishingTimeReductionMs < 0L) {
            throw new IllegalArgumentException(
                    "fishingTimeReductionMs must not be negative, got " + fishingTimeReductionMs);
        }
        if (chanceBonusPercent != null && !(chanceBonusPercent >= -100.0 && chanceBonusPercent <= 100.0)) {
            throw new IllegalArgumentException(
                    "chanceBonusPercent must be a finite number within [-100, 100], got " + chanceBonusPercent);
        }
        if (shotGrade == ItemGrade.EVENT) {
            throw new IllegalArgumentException("shotGrade must not be EVENT");
        }
        this.itemTemplateId = itemTemplateId;
        this.fishingTimeMs = fishingTimeMs;
        this.fishingTimeReductionMs = fishingTimeReductionMs;
        this.chanceBonusPercent = chanceBonusPercent;
        this.shotGrade = shotGrade;
    }

    public int getItemTemplateId() {
        return itemTemplateId;
    }

    public @Nullable Long getFishingTimeMs() {
        return fishingTimeMs;
    }

    public @Nullable Long getFishingTimeReductionMs() {
        return fishingTimeReductionMs;
    }

    public @Nullable Double getChanceBonusPercent() {
        return chanceBonusPercent;
    }

    public @Nullable ItemGrade getShotGrade() {
        return shotGrade;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemTemplateId(itemTemplateId)
                .fishingTimeMs(fishingTimeMs)
                .fishingTimeReductionMs(fishingTimeReductionMs)
                .chanceBonusPercent(chanceBonusPercent)
                .shotGrade(shotGrade);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FishingRod)) return false;
        FishingRod that = (FishingRod) o;
        return itemTemplateId == that.itemTemplateId
                && Objects.equals(fishingTimeMs, that.fishingTimeMs)
                && Objects.equals(fishingTimeReductionMs, that.fishingTimeReductionMs)
                && Objects.equals(chanceBonusPercent, that.chanceBonusPercent)
                && shotGrade == that.shotGrade;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, fishingTimeMs, fishingTimeReductionMs, chanceBonusPercent, shotGrade);
    }

    @Override
    public String toString() {
        return "FishingRod[itemTemplateId=" + itemTemplateId + ", fishingTimeMs=" + fishingTimeMs
                + ", fishingTimeReductionMs="
                + fishingTimeReductionMs + ", chanceBonusPercent=" + chanceBonusPercent + ", shotGrade=" + shotGrade
                + "]";
    }

    public static final class Builder {
        private int itemTemplateId;
        private @Nullable Long fishingTimeMs;
        private @Nullable Long fishingTimeReductionMs;
        private @Nullable Double chanceBonusPercent;
        private @Nullable ItemGrade shotGrade;

        public Builder itemTemplateId(int itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder fishingTimeMs(@Nullable Long fishingTimeMs) {
            this.fishingTimeMs = fishingTimeMs;
            return this;
        }

        public Builder fishingTimeReductionMs(@Nullable Long fishingTimeReductionMs) {
            this.fishingTimeReductionMs = fishingTimeReductionMs;
            return this;
        }

        public Builder chanceBonusPercent(@Nullable Double chanceBonusPercent) {
            this.chanceBonusPercent = chanceBonusPercent;
            return this;
        }

        public Builder shotGrade(@Nullable ItemGrade shotGrade) {
            this.shotGrade = shotGrade;
            return this;
        }

        public FishingRod build() {
            return new FishingRod(itemTemplateId, fishingTimeMs, fishingTimeReductionMs, chanceBonusPercent, shotGrade);
        }
    }
}
