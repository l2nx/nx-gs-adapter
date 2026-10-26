package app.l2nx.gs.adapter.api.kafka.sync.gd.fishing;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An item the server accepts in the bait slot. {@code chancePercent} (0..100) is the base catch chance of one cast,
 * before rod, shot and penalties. A {@code null} level bound means no bound. The fishing time pair is set together
 * ({@code 0 < min <= max}) or is {@code null} when the build times fishing by the rod only.
 */
public final class FishingBait {

    private final int itemTemplateId;
    private final double chancePercent;
    private final @Nullable Integer minCharacterLevel;
    private final @Nullable Integer maxCharacterLevel;
    private final @Nullable Long fishingTimeMinMs;
    private final @Nullable Long fishingTimeMaxMs;

    public FishingBait(
            int itemTemplateId,
            double chancePercent,
            @Nullable Integer minCharacterLevel,
            @Nullable Integer maxCharacterLevel,
            @Nullable Long fishingTimeMinMs,
            @Nullable Long fishingTimeMaxMs) {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId must be positive, got " + itemTemplateId);
        }
        if (!(chancePercent >= 0.0 && chancePercent <= 100.0)) {
            throw new IllegalArgumentException(
                    "chancePercent must be a finite number within [0, 100], got " + chancePercent);
        }
        requirePositive(minCharacterLevel, "minCharacterLevel");
        requirePositive(maxCharacterLevel, "maxCharacterLevel");
        if (minCharacterLevel != null && maxCharacterLevel != null && minCharacterLevel > maxCharacterLevel) {
            throw new IllegalArgumentException(
                    "minCharacterLevel " + minCharacterLevel + " exceeds maxCharacterLevel " + maxCharacterLevel);
        }
        if ((fishingTimeMinMs == null) != (fishingTimeMaxMs == null)) {
            throw new IllegalArgumentException("fishingTimeMinMs and fishingTimeMaxMs are set together, got "
                    + fishingTimeMinMs + "/" + fishingTimeMaxMs);
        }
        if (fishingTimeMinMs != null && (fishingTimeMinMs <= 0L || fishingTimeMinMs > fishingTimeMaxMs)) {
            throw new IllegalArgumentException(
                    "fishing time must satisfy 0 < min <= max, got " + fishingTimeMinMs + "/" + fishingTimeMaxMs);
        }
        this.itemTemplateId = itemTemplateId;
        this.chancePercent = chancePercent;
        this.minCharacterLevel = minCharacterLevel;
        this.maxCharacterLevel = maxCharacterLevel;
        this.fishingTimeMinMs = fishingTimeMinMs;
        this.fishingTimeMaxMs = fishingTimeMaxMs;
    }

    private static void requirePositive(@Nullable Integer value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
    }

    public int getItemTemplateId() {
        return itemTemplateId;
    }

    public double getChancePercent() {
        return chancePercent;
    }

    public @Nullable Integer getMinCharacterLevel() {
        return minCharacterLevel;
    }

    public @Nullable Integer getMaxCharacterLevel() {
        return maxCharacterLevel;
    }

    public @Nullable Long getFishingTimeMinMs() {
        return fishingTimeMinMs;
    }

    public @Nullable Long getFishingTimeMaxMs() {
        return fishingTimeMaxMs;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemTemplateId(itemTemplateId)
                .chancePercent(chancePercent)
                .minCharacterLevel(minCharacterLevel)
                .maxCharacterLevel(maxCharacterLevel)
                .fishingTimeMinMs(fishingTimeMinMs)
                .fishingTimeMaxMs(fishingTimeMaxMs);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FishingBait)) return false;
        FishingBait that = (FishingBait) o;
        return itemTemplateId == that.itemTemplateId
                && Double.compare(chancePercent, that.chancePercent) == 0
                && Objects.equals(minCharacterLevel, that.minCharacterLevel)
                && Objects.equals(maxCharacterLevel, that.maxCharacterLevel)
                && Objects.equals(fishingTimeMinMs, that.fishingTimeMinMs)
                && Objects.equals(fishingTimeMaxMs, that.fishingTimeMaxMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                itemTemplateId,
                chancePercent,
                minCharacterLevel,
                maxCharacterLevel,
                fishingTimeMinMs,
                fishingTimeMaxMs);
    }

    @Override
    public String toString() {
        return "FishingBait[itemTemplateId=" + itemTemplateId + ", chancePercent=" + chancePercent
                + ", minCharacterLevel=" + minCharacterLevel + ", maxCharacterLevel=" + maxCharacterLevel
                + ", fishingTimeMinMs="
                + fishingTimeMinMs + ", fishingTimeMaxMs=" + fishingTimeMaxMs + "]";
    }

    public static final class Builder {
        private int itemTemplateId;
        private double chancePercent;
        private @Nullable Integer minCharacterLevel;
        private @Nullable Integer maxCharacterLevel;
        private @Nullable Long fishingTimeMinMs;
        private @Nullable Long fishingTimeMaxMs;

        public Builder itemTemplateId(int itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder chancePercent(double chancePercent) {
            this.chancePercent = chancePercent;
            return this;
        }

        public Builder minCharacterLevel(@Nullable Integer minCharacterLevel) {
            this.minCharacterLevel = minCharacterLevel;
            return this;
        }

        public Builder maxCharacterLevel(@Nullable Integer maxCharacterLevel) {
            this.maxCharacterLevel = maxCharacterLevel;
            return this;
        }

        public Builder fishingTimeMinMs(@Nullable Long fishingTimeMinMs) {
            this.fishingTimeMinMs = fishingTimeMinMs;
            return this;
        }

        public Builder fishingTimeMaxMs(@Nullable Long fishingTimeMaxMs) {
            this.fishingTimeMaxMs = fishingTimeMaxMs;
            return this;
        }

        public FishingBait build() {
            return new FishingBait(
                    itemTemplateId,
                    chancePercent,
                    minCharacterLevel,
                    maxCharacterLevel,
                    fishingTimeMinMs,
                    fishingTimeMaxMs);
        }
    }
}
