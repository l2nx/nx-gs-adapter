package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRecord;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Success payload of {@link BanCommand}: every ban row created, extended or found, one per dimension of a
 * {@code HARD} fan-out, a shadow chat ban included. {@code observedAt} (host time) orders this reply against the ban
 * sync stream, the newest observation of a row winning.
 */
public final class BanResult {

    private final List<BanRecord> bans;
    private final Instant observedAt;

    public BanResult(List<BanRecord> bans, Instant observedAt) {
        List<BanRecord> frozen = BanLists.freeze(bans, "bans");
        if (frozen.isEmpty()) {
            throw new IllegalArgumentException("bans must not be empty");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
        this.bans = frozen;
        this.observedAt = observedAt;
    }

    public List<BanRecord> getBans() {
        return bans;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Builder toBuilder() {
        return new Builder().bans(bans).observedAt(observedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanResult)) return false;
        BanResult that = (BanResult) o;
        return Objects.equals(bans, that.bans) && Objects.equals(observedAt, that.observedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bans, observedAt);
    }

    @Override
    public String toString() {
        return "BanResult[bans=" + bans + ", observedAt=" + observedAt + "]";
    }

    public static final class Builder {
        private List<BanRecord> bans;
        private Instant observedAt;

        public Builder bans(List<BanRecord> bans) {
            this.bans = bans;
            return this;
        }

        public Builder observedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public BanResult build() {
            return new BanResult(bans, observedAt);
        }
    }
}
