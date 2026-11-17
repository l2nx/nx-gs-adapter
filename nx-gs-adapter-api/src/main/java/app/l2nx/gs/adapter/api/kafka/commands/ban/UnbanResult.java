package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Success payload of {@link UnbanCommand}: every cleared row, one per dimension of a {@code HARD} target; empty for a
 * no-op success. {@code observedAt} orders the reply as in {@link BanResult}.
 */
public final class UnbanResult {

    private final List<BanRef> removedBans;
    private final Instant observedAt;

    public UnbanResult(List<BanRef> removedBans, Instant observedAt) {
        List<BanRef> frozen = BanLists.freeze(removedBans, "removedBans");
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
        this.removedBans = frozen;
        this.observedAt = observedAt;
    }

    public List<BanRef> getRemovedBans() {
        return removedBans;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Builder toBuilder() {
        return new Builder().removedBans(removedBans).observedAt(observedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnbanResult)) return false;
        UnbanResult that = (UnbanResult) o;
        return Objects.equals(removedBans, that.removedBans) && Objects.equals(observedAt, that.observedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(removedBans, observedAt);
    }

    @Override
    public String toString() {
        return "UnbanResult[removedBans=" + removedBans + ", observedAt=" + observedAt + "]";
    }

    public static final class Builder {
        private List<BanRef> removedBans;
        private Instant observedAt;

        public Builder removedBans(List<BanRef> removedBans) {
            this.removedBans = removedBans;
            return this;
        }

        public Builder observedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public UnbanResult build() {
            return new UnbanResult(removedBans, observedAt);
        }
    }
}
