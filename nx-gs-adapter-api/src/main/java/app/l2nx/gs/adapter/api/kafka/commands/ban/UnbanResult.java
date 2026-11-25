package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Success payload of {@link UnbanCommand}: cleared rows, empty for a no-op success.
 * {@code observedAt} orders the reply as in {@link BanResult}.
 */
public final class UnbanResult {

    private final List<BanRef> unbannedBans;
    private final Instant observedAt;

    public UnbanResult(List<BanRef> unbannedBans, Instant observedAt) {
        List<BanRef> frozen = BanLists.freeze(unbannedBans, "unbannedBans");
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
        this.unbannedBans = frozen;
        this.observedAt = observedAt;
    }

    public List<BanRef> getUnbannedBans() {
        return unbannedBans;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Builder toBuilder() {
        return new Builder().unbannedBans(unbannedBans).observedAt(observedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnbanResult)) return false;
        UnbanResult that = (UnbanResult) o;
        return Objects.equals(unbannedBans, that.unbannedBans) && Objects.equals(observedAt, that.observedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unbannedBans, observedAt);
    }

    @Override
    public String toString() {
        return "UnbanResult[unbannedBans=" + unbannedBans + ", observedAt=" + observedAt + "]";
    }

    public static final class Builder {
        private List<BanRef> unbannedBans;
        private Instant observedAt;

        public Builder unbannedBans(List<BanRef> unbannedBans) {
            this.unbannedBans = unbannedBans;
            return this;
        }

        public Builder observedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public UnbanResult build() {
            return new UnbanResult(unbannedBans, observedAt);
        }
    }
}
