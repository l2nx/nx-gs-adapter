package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Private-store rules of the server. The offline fee is either fully set (all three fields, each {@code > 0}) or fully
 * {@code null}, which means the offline mode of a private store is free. A store with a live client never pays it.
 */
public final class PrivateStoreSettings {

    private final @Nullable Integer offlineFeeItemTemplateId;
    private final @Nullable Long offlineFeeItemTemplateCount;
    private final @Nullable Integer offlineFeePeriodHours;

    public PrivateStoreSettings(
            @Nullable Integer offlineFeeItemTemplateId,
            @Nullable Long offlineFeeItemTemplateCount,
            @Nullable Integer offlineFeePeriodHours) {
        boolean anySet = offlineFeeItemTemplateId != null
                || offlineFeeItemTemplateCount != null
                || offlineFeePeriodHours != null;
        boolean allSet = offlineFeeItemTemplateId != null
                && offlineFeeItemTemplateCount != null
                && offlineFeePeriodHours != null;
        if (anySet && !allSet) {
            throw new IllegalArgumentException("PrivateStoreSettings offline fee fields must be all set or all null");
        }
        if (allSet
                && (offlineFeeItemTemplateId <= 0 || offlineFeeItemTemplateCount <= 0 || offlineFeePeriodHours <= 0)) {
            throw new IllegalArgumentException("PrivateStoreSettings offline fee values must be positive");
        }
        this.offlineFeeItemTemplateId = offlineFeeItemTemplateId;
        this.offlineFeeItemTemplateCount = offlineFeeItemTemplateCount;
        this.offlineFeePeriodHours = offlineFeePeriodHours;
    }

    public @Nullable Integer getOfflineFeeItemTemplateId() {
        return offlineFeeItemTemplateId;
    }

    public @Nullable Long getOfflineFeeItemTemplateCount() {
        return offlineFeeItemTemplateCount;
    }

    public @Nullable Integer getOfflineFeePeriodHours() {
        return offlineFeePeriodHours;
    }

    public Builder toBuilder() {
        return new Builder()
                .offlineFeeItemTemplateId(offlineFeeItemTemplateId)
                .offlineFeeItemTemplateCount(offlineFeeItemTemplateCount)
                .offlineFeePeriodHours(offlineFeePeriodHours);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrivateStoreSettings)) return false;
        PrivateStoreSettings that = (PrivateStoreSettings) o;
        return Objects.equals(offlineFeeItemTemplateId, that.offlineFeeItemTemplateId)
                && Objects.equals(offlineFeeItemTemplateCount, that.offlineFeeItemTemplateCount)
                && Objects.equals(offlineFeePeriodHours, that.offlineFeePeriodHours);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offlineFeeItemTemplateId, offlineFeeItemTemplateCount, offlineFeePeriodHours);
    }

    @Override
    public String toString() {
        return "PrivateStoreSettings[offlineFeeItemTemplateId=" + offlineFeeItemTemplateId
                + ", offlineFeeItemTemplateCount=" + offlineFeeItemTemplateCount
                + ", offlineFeePeriodHours=" + offlineFeePeriodHours + "]";
    }

    public static final class Builder {
        private @Nullable Integer offlineFeeItemTemplateId;
        private @Nullable Long offlineFeeItemTemplateCount;
        private @Nullable Integer offlineFeePeriodHours;

        public Builder offlineFeeItemTemplateId(@Nullable Integer offlineFeeItemTemplateId) {
            this.offlineFeeItemTemplateId = offlineFeeItemTemplateId;
            return this;
        }

        public Builder offlineFeeItemTemplateCount(@Nullable Long offlineFeeItemTemplateCount) {
            this.offlineFeeItemTemplateCount = offlineFeeItemTemplateCount;
            return this;
        }

        public Builder offlineFeePeriodHours(@Nullable Integer offlineFeePeriodHours) {
            this.offlineFeePeriodHours = offlineFeePeriodHours;
            return this;
        }

        public PrivateStoreSettings build() {
            return new PrivateStoreSettings(
                    offlineFeeItemTemplateId, offlineFeeItemTemplateCount, offlineFeePeriodHours);
        }
    }
}
