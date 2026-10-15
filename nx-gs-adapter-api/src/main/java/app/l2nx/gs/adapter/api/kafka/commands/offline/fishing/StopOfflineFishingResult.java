package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class StopOfflineFishingResult {

    private final @Nullable UUID sessionId;
    private final boolean stopped;

    public StopOfflineFishingResult(@Nullable UUID sessionId, boolean stopped) {
        this.sessionId = sessionId;
        this.stopped = stopped;
    }

    /** The session this command ended; {@code null} when nothing ran. */
    public @Nullable UUID getSessionId() {
        return sessionId;
    }

    public boolean isStopped() {
        return stopped;
    }

    public Builder toBuilder() {
        return new Builder().sessionId(sessionId).stopped(stopped);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StopOfflineFishingResult)) return false;
        StopOfflineFishingResult that = (StopOfflineFishingResult) o;
        return stopped == that.stopped && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, stopped);
    }

    @Override
    public String toString() {
        return "StopOfflineFishingResult[sessionId=" + sessionId + ", stopped=" + stopped + "]";
    }

    public static final class Builder {
        private @Nullable UUID sessionId;
        private boolean stopped;

        public Builder sessionId(@Nullable UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder stopped(boolean stopped) {
            this.stopped = stopped;
            return this;
        }

        public StopOfflineFishingResult build() {
            return new StopOfflineFishingResult(sessionId, stopped);
        }
    }
}
