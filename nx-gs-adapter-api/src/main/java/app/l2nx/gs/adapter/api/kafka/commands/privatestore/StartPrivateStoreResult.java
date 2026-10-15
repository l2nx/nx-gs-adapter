package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class StartPrivateStoreResult {

    private final String storeType;
    private final int acceptedCount;
    private final @Nullable Boolean offline;
    private final @Nullable UUID sessionId;

    public StartPrivateStoreResult(
            String storeType, int acceptedCount, @Nullable Boolean offline, @Nullable UUID sessionId) {
        if (storeType == null) {
            throw new IllegalArgumentException("storeType is required");
        }
        this.storeType = storeType;
        this.acceptedCount = acceptedCount;
        this.offline = offline;
        this.sessionId = sessionId;
    }

    /** Host-defined open vocabulary (e.g. {@code "SELL"}, {@code "PACKAGE_SELL"}), not a closed enum. */
    public String getStoreType() {
        return storeType;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    /**
     * {@code true} = offline branch (offline seat), {@code false} = online. {@code null} (older host): unknown, so the
     * platform keeps the offline-quota slot it took before dispatch.
     */
    public @Nullable Boolean getOffline() {
        return offline;
    }

    /** App session id when the store was opened as an app session; {@code null} otherwise and from an older host. */
    public @Nullable UUID getSessionId() {
        return sessionId;
    }

    public Builder toBuilder() {
        return new Builder()
                .storeType(storeType)
                .acceptedCount(acceptedCount)
                .offline(offline)
                .sessionId(sessionId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StartPrivateStoreResult)) return false;
        StartPrivateStoreResult that = (StartPrivateStoreResult) o;
        return acceptedCount == that.acceptedCount
                && Objects.equals(offline, that.offline)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(storeType, that.storeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeType, acceptedCount, offline, sessionId);
    }

    @Override
    public String toString() {
        return "StartPrivateStoreResult[storeType=" + storeType
                + ", acceptedCount=" + acceptedCount
                + ", offline=" + offline
                + ", sessionId=" + sessionId + "]";
    }

    public static final class Builder {
        private @Nullable String storeType;
        private int acceptedCount;
        private @Nullable Boolean offline;
        private @Nullable UUID sessionId;

        public Builder storeType(String storeType) {
            this.storeType = storeType;
            return this;
        }

        public Builder acceptedCount(int acceptedCount) {
            this.acceptedCount = acceptedCount;
            return this;
        }

        public Builder offline(@Nullable Boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder sessionId(@Nullable UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public StartPrivateStoreResult build() {
            return new StartPrivateStoreResult(storeType, acceptedCount, offline, sessionId);
        }
    }
}
