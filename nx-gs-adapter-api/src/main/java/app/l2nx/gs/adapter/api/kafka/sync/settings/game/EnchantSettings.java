package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Server-wide enchant caps the engine applies; {@code null} = no server-wide cap, a set value is {@code >= 0}. */
public final class EnchantSettings {

    private final @Nullable Integer maxWeapon;
    private final @Nullable Integer maxArmor;
    private final @Nullable Integer maxJewelry;

    public EnchantSettings(@Nullable Integer maxWeapon, @Nullable Integer maxArmor, @Nullable Integer maxJewelry) {
        if (isNegative(maxWeapon) || isNegative(maxArmor) || isNegative(maxJewelry)) {
            throw new IllegalArgumentException("EnchantSettings caps must not be negative");
        }
        this.maxWeapon = maxWeapon;
        this.maxArmor = maxArmor;
        this.maxJewelry = maxJewelry;
    }

    private static boolean isNegative(@Nullable Integer value) {
        return value != null && value < 0;
    }

    public @Nullable Integer getMaxWeapon() {
        return maxWeapon;
    }

    public @Nullable Integer getMaxArmor() {
        return maxArmor;
    }

    /** Cap for the neck, ear and finger slots. */
    public @Nullable Integer getMaxJewelry() {
        return maxJewelry;
    }

    public Builder toBuilder() {
        return new Builder().maxWeapon(maxWeapon).maxArmor(maxArmor).maxJewelry(maxJewelry);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnchantSettings)) return false;
        EnchantSettings that = (EnchantSettings) o;
        return Objects.equals(maxWeapon, that.maxWeapon)
                && Objects.equals(maxArmor, that.maxArmor)
                && Objects.equals(maxJewelry, that.maxJewelry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxWeapon, maxArmor, maxJewelry);
    }

    @Override
    public String toString() {
        return "EnchantSettings[maxWeapon=" + maxWeapon + ", maxArmor=" + maxArmor + ", maxJewelry=" + maxJewelry + "]";
    }

    public static final class Builder {
        private @Nullable Integer maxWeapon;
        private @Nullable Integer maxArmor;
        private @Nullable Integer maxJewelry;

        public Builder maxWeapon(@Nullable Integer maxWeapon) {
            this.maxWeapon = maxWeapon;
            return this;
        }

        public Builder maxArmor(@Nullable Integer maxArmor) {
            this.maxArmor = maxArmor;
            return this;
        }

        public Builder maxJewelry(@Nullable Integer maxJewelry) {
            this.maxJewelry = maxJewelry;
            return this;
        }

        public EnchantSettings build() {
            return new EnchantSettings(maxWeapon, maxArmor, maxJewelry);
        }
    }
}
