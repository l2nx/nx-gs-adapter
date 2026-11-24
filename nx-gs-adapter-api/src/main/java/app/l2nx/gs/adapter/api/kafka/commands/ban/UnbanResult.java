package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Success payload of {@link UnbanCommand}: cleared rows, one per dimension of a deprecated {@code HARD} subject; empty
 * for a no-op success. {@code observedAt} orders the reply as in {@link BanResult}.
 * The wire carries both {@code unbannedBans} and {@code removedBans}; a reader takes {@code unbannedBans} when set, an
 * empty list included.
 */
public final class UnbanResult {

    private final @Nullable List<BanRef> unbannedBans;
    // TODO: drop removedBans once the adapter of every active game server writes unbannedBans.
    private final @Nullable List<BanRef> removedBans;
    private final Instant observedAt;

    public UnbanResult(@Nullable List<BanRef> unbannedBans, @Nullable List<BanRef> removedBans, Instant observedAt) {
        List<BanRef> frozen = BanLists.freeze(unbannedBans != null ? unbannedBans : removedBans, "unbannedBans");
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
        this.unbannedBans = frozen;
        this.removedBans = frozen;
        this.observedAt = observedAt;
    }

    public List<BanRef> getUnbannedBans() {
        return unbannedBans != null ? unbannedBans : removedBans;
    }

    /** @deprecated the former name of {@link #getUnbannedBans()}. */
    @Deprecated
    public List<BanRef> getRemovedBans() {
        return getUnbannedBans();
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Builder toBuilder() {
        return new Builder().unbannedBans(getUnbannedBans()).observedAt(observedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnbanResult)) return false;
        UnbanResult that = (UnbanResult) o;
        return Objects.equals(getUnbannedBans(), that.getUnbannedBans()) && Objects.equals(observedAt, that.observedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUnbannedBans(), observedAt);
    }

    @Override
    public String toString() {
        return "UnbanResult[unbannedBans=" + getUnbannedBans() + ", observedAt=" + observedAt + "]";
    }

    public static final class Builder {
        private List<BanRef> unbannedBans;
        private Instant observedAt;

        public Builder unbannedBans(List<BanRef> unbannedBans) {
            this.unbannedBans = unbannedBans;
            return this;
        }

        /** @deprecated the former name of {@link #unbannedBans(List)}. */
        @Deprecated
        public Builder removedBans(List<BanRef> removedBans) {
            return unbannedBans(removedBans);
        }

        public Builder observedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public UnbanResult build() {
            return new UnbanResult(unbannedBans, unbannedBans, observedAt);
        }
    }
}
