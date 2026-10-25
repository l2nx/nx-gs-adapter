package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class AttributeSettings {

    private final int maxWeaponAttributes;
    private final int maxArmorAttributes;
    private final List<AttributeOppositePair> oppositePairs;
    private final List<AttributeChance> chances;
    private final List<AttributeLimit> limits;

    public AttributeSettings(
            int maxWeaponAttributes,
            int maxArmorAttributes,
            @Nullable List<AttributeOppositePair> oppositePairs,
            @Nullable List<AttributeChance> chances,
            @Nullable List<AttributeLimit> limits) {
        if (maxWeaponAttributes < 0 || maxArmorAttributes < 0) {
            throw new IllegalArgumentException("AttributeSettings attribute counts must not be negative");
        }
        this.maxWeaponAttributes = maxWeaponAttributes;
        this.maxArmorAttributes = maxArmorAttributes;
        this.oppositePairs = frozen(oppositePairs);
        this.chances = frozen(chances);
        this.limits = frozen(limits);
    }

    private static <T> List<T> frozen(@Nullable List<T> source) {
        return source == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public int getMaxWeaponAttributes() {
        return maxWeaponAttributes;
    }

    public int getMaxArmorAttributes() {
        return maxArmorAttributes;
    }

    public List<AttributeOppositePair> getOppositePairs() {
        return oppositePairs;
    }

    public List<AttributeChance> getChances() {
        return chances;
    }

    public List<AttributeLimit> getLimits() {
        return limits;
    }

    public Builder toBuilder() {
        return new Builder()
                .maxWeaponAttributes(maxWeaponAttributes)
                .maxArmorAttributes(maxArmorAttributes)
                .oppositePairs(oppositePairs)
                .chances(chances)
                .limits(limits);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeSettings)) return false;
        AttributeSettings that = (AttributeSettings) o;
        return maxWeaponAttributes == that.maxWeaponAttributes
                && maxArmorAttributes == that.maxArmorAttributes
                && Objects.equals(oppositePairs, that.oppositePairs)
                && Objects.equals(chances, that.chances)
                && Objects.equals(limits, that.limits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxWeaponAttributes, maxArmorAttributes, oppositePairs, chances, limits);
    }

    @Override
    public String toString() {
        return "AttributeSettings[maxWeaponAttributes=" + maxWeaponAttributes
                + ", maxArmorAttributes=" + maxArmorAttributes
                + ", oppositePairs=" + oppositePairs
                + ", chances=" + chances
                + ", limits=" + limits + "]";
    }

    public static final class Builder {
        private int maxWeaponAttributes;
        private int maxArmorAttributes;
        private @Nullable List<AttributeOppositePair> oppositePairs;
        private @Nullable List<AttributeChance> chances;
        private @Nullable List<AttributeLimit> limits;

        public Builder maxWeaponAttributes(int maxWeaponAttributes) {
            this.maxWeaponAttributes = maxWeaponAttributes;
            return this;
        }

        public Builder maxArmorAttributes(int maxArmorAttributes) {
            this.maxArmorAttributes = maxArmorAttributes;
            return this;
        }

        public Builder oppositePairs(@Nullable List<AttributeOppositePair> oppositePairs) {
            this.oppositePairs = oppositePairs;
            return this;
        }

        public Builder chances(@Nullable List<AttributeChance> chances) {
            this.chances = chances;
            return this;
        }

        public Builder limits(@Nullable List<AttributeLimit> limits) {
            this.limits = limits;
            return this;
        }

        public AttributeSettings build() {
            return new AttributeSettings(maxWeaponAttributes, maxArmorAttributes, oppositePairs, chances, limits);
        }
    }
}
