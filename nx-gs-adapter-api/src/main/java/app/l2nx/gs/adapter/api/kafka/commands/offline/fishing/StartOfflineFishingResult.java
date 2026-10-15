package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import java.util.Objects;
import java.util.UUID;

public final class StartOfflineFishingResult {

    private final UUID sessionId;
    private final String spotPool;
    private final int spotId;
    private final long effectiveDurationMs;

    public StartOfflineFishingResult(UUID sessionId, String spotPool, int spotId, long effectiveDurationMs) {
        this.sessionId = Objects.requireNonNull(sessionId, "StartOfflineFishingResult.sessionId is required");
        this.spotPool = Objects.requireNonNull(spotPool, "StartOfflineFishingResult.spotPool is required");
        this.spotId = spotId;
        this.effectiveDurationMs = effectiveDurationMs;
    }

    /** The same id the session's presence events carry. */
    public UUID getSessionId() {
        return sessionId;
    }

    public String getSpotPool() {
        return spotPool;
    }

    public int getSpotId() {
        return spotId;
    }

    /** What the host will actually run; shorter than requested when the daily allowance is smaller. */
    public long getEffectiveDurationMs() {
        return effectiveDurationMs;
    }

    public Builder toBuilder() {
        return new Builder()
                .sessionId(sessionId)
                .spotPool(spotPool)
                .spotId(spotId)
                .effectiveDurationMs(effectiveDurationMs);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StartOfflineFishingResult)) return false;
        StartOfflineFishingResult that = (StartOfflineFishingResult) o;
        return spotId == that.spotId
                && effectiveDurationMs == that.effectiveDurationMs
                && sessionId.equals(that.sessionId)
                && spotPool.equals(that.spotPool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, spotPool, spotId, effectiveDurationMs);
    }

    @Override
    public String toString() {
        return "StartOfflineFishingResult[sessionId=" + sessionId
                + ", spotPool=" + spotPool
                + ", spotId=" + spotId
                + ", effectiveDurationMs=" + effectiveDurationMs + "]";
    }

    public static final class Builder {
        private UUID sessionId;
        private String spotPool;
        private int spotId;
        private long effectiveDurationMs;

        public Builder sessionId(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder spotPool(String spotPool) {
            this.spotPool = spotPool;
            return this;
        }

        public Builder spotId(int spotId) {
            this.spotId = spotId;
            return this;
        }

        public Builder effectiveDurationMs(long effectiveDurationMs) {
            this.effectiveDurationMs = effectiveDurationMs;
            return this;
        }

        public StartOfflineFishingResult build() {
            return new StartOfflineFishingResult(sessionId, spotPool, spotId, effectiveDurationMs);
        }
    }
}
