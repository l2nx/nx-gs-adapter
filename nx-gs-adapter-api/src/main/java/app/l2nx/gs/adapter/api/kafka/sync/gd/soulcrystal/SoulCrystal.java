package app.l2nx.gs.adapter.api.kafka.sync.gd.soulcrystal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One node of the soul-crystal leveling chain. {@code level} is {@code null} when the build has no stage number;
 * {@code nextItemTemplateId} (successful level-up) is {@code null} at the chain top; {@code cursedNextItemTemplateId}
 * (failed level-up) is {@code null} when the build has none.
 */
public final class SoulCrystal {

    private final int itemTemplateId;
    private final @Nullable Integer level;
    private final @Nullable Integer nextItemTemplateId;
    private final @Nullable Integer cursedNextItemTemplateId;

    public SoulCrystal(
            int itemTemplateId,
            @Nullable Integer level,
            @Nullable Integer nextItemTemplateId,
            @Nullable Integer cursedNextItemTemplateId) {
        this.itemTemplateId = itemTemplateId;
        this.level = level;
        this.nextItemTemplateId = nextItemTemplateId;
        this.cursedNextItemTemplateId = cursedNextItemTemplateId;
    }

    public int getItemTemplateId() {
        return itemTemplateId;
    }

    public @Nullable Integer getLevel() {
        return level;
    }

    public @Nullable Integer getNextItemTemplateId() {
        return nextItemTemplateId;
    }

    public @Nullable Integer getCursedNextItemTemplateId() {
        return cursedNextItemTemplateId;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemTemplateId(itemTemplateId)
                .level(level)
                .nextItemTemplateId(nextItemTemplateId)
                .cursedNextItemTemplateId(cursedNextItemTemplateId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoulCrystal)) return false;
        SoulCrystal that = (SoulCrystal) o;
        return itemTemplateId == that.itemTemplateId
                && Objects.equals(level, that.level)
                && Objects.equals(nextItemTemplateId, that.nextItemTemplateId)
                && Objects.equals(cursedNextItemTemplateId, that.cursedNextItemTemplateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, level, nextItemTemplateId, cursedNextItemTemplateId);
    }

    @Override
    public String toString() {
        return "SoulCrystal[itemTemplateId=" + itemTemplateId + ", level=" + level + ", nextItemTemplateId="
                + nextItemTemplateId + ", cursedNextItemTemplateId=" + cursedNextItemTemplateId + "]";
    }

    public static final class Builder {
        private int itemTemplateId;
        private @Nullable Integer level;
        private @Nullable Integer nextItemTemplateId;
        private @Nullable Integer cursedNextItemTemplateId;

        public Builder itemTemplateId(int itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder level(@Nullable Integer level) {
            this.level = level;
            return this;
        }

        public Builder nextItemTemplateId(@Nullable Integer nextItemTemplateId) {
            this.nextItemTemplateId = nextItemTemplateId;
            return this;
        }

        public Builder cursedNextItemTemplateId(@Nullable Integer cursedNextItemTemplateId) {
            this.cursedNextItemTemplateId = cursedNextItemTemplateId;
            return this;
        }

        public SoulCrystal build() {
            return new SoulCrystal(itemTemplateId, level, nextItemTemplateId, cursedNextItemTemplateId);
        }
    }
}
