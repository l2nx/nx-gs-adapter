package app.l2nx.gs.adapter.api.spi.model;

import java.util.Objects;

/** Host configuration the app-session engine needs, read through {@code OfflineSessionHost.settings()} on every use. */
public final class OfflineSessionSettings {

    private final long returnThresholdMs;
    private final int retentionDays;
    private final int restoreBatchSize;
    private final long restoreBatchIntervalMs;

    public OfflineSessionSettings(
            long returnThresholdMs,
            int retentionDays,
            int restoreBatchSize,
            long restoreBatchIntervalMs) {
        if (returnThresholdMs < 0 || retentionDays < 0 || restoreBatchIntervalMs < 0) {
            throw new IllegalArgumentException("OfflineSessionSettings values must not be negative");
        }
        if (restoreBatchSize < 1) {
            throw new IllegalArgumentException("OfflineSessionSettings.restoreBatchSize must be at least 1");
        }
        this.returnThresholdMs = returnThresholdMs;
        this.retentionDays = retentionDays;
        this.restoreBatchSize = restoreBatchSize;
        this.restoreBatchIntervalMs = restoreBatchIntervalMs;
    }

    /** A session that ends with {@code activeMs} below this returns the character to its origin. */
    public long getReturnThresholdMs() {
        return returnThresholdMs;
    }

    /** Closed sessions older than this are deleted at engine start; {@code 0} keeps them all. */
    public int getRetentionDays() {
        return retentionDays;
    }

    /** Sessions restored per batch; capped at half of {@code l2nx.io.workers} (at least one) so a restore never starves other IO. */
    public int getRestoreBatchSize() {
        return restoreBatchSize;
    }

    public long getRestoreBatchIntervalMs() {
        return restoreBatchIntervalMs;
    }

    public Builder toBuilder() {
        return new Builder()
                .returnThresholdMs(returnThresholdMs)
                .retentionDays(retentionDays)
                .restoreBatchSize(restoreBatchSize)
                .restoreBatchIntervalMs(restoreBatchIntervalMs);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfflineSessionSettings)) return false;
        OfflineSessionSettings that = (OfflineSessionSettings) o;
        return returnThresholdMs == that.returnThresholdMs
                && retentionDays == that.retentionDays
                && restoreBatchSize == that.restoreBatchSize
                && restoreBatchIntervalMs == that.restoreBatchIntervalMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnThresholdMs, retentionDays, restoreBatchSize, restoreBatchIntervalMs);
    }

    @Override
    public String toString() {
        return "OfflineSessionSettings[returnThresholdMs=" + returnThresholdMs
                + ", retentionDays=" + retentionDays
                + ", restoreBatchSize=" + restoreBatchSize
                + ", restoreBatchIntervalMs=" + restoreBatchIntervalMs + "]";
    }

    public static final class Builder {
        private long returnThresholdMs;
        private int retentionDays;
        private int restoreBatchSize = 1;
        private long restoreBatchIntervalMs;

        public Builder returnThresholdMs(long returnThresholdMs) {
            this.returnThresholdMs = returnThresholdMs;
            return this;
        }

        public Builder retentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }

        public Builder restoreBatchSize(int restoreBatchSize) {
            this.restoreBatchSize = restoreBatchSize;
            return this;
        }

        public Builder restoreBatchIntervalMs(long restoreBatchIntervalMs) {
            this.restoreBatchIntervalMs = restoreBatchIntervalMs;
            return this;
        }

        public OfflineSessionSettings build() {
            return new OfflineSessionSettings(
                    returnThresholdMs, retentionDays, restoreBatchSize, restoreBatchIntervalMs);
        }
    }
}
