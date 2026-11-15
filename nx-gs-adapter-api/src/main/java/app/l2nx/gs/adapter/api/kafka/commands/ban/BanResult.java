package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Success payload of {@link BanCommand}: ids of the ban rows created, extended or found. A {@code HARD} fan-out returns
 * one id per dimension; a ban not persisted as an id-bearing row (e.g. shadow chat ban) returns an empty list.
 */
public final class BanResult {

    private final List<Long> banIds;
    private final @Nullable List<BanRecord> bans;

    public BanResult(List<Long> banIds, @Nullable List<BanRecord> bans) {
        if (banIds != null) {
            // contains(null) throws on immutable lists (List.of), so scan by hand.
            for (Long id : banIds) {
                if (id == null) {
                    throw new IllegalArgumentException("banIds must not hold null");
                }
            }
        }
        this.banIds = banIds == null
                ? Collections.<Long>emptyList()
                : Collections.unmodifiableList(new ArrayList<Long>(banIds));
        if (bans != null) {
            if (bans.size() != this.banIds.size()) {
                throw new IllegalArgumentException("bans must carry one entry per banIds id");
            }
            for (int i = 0; i < bans.size(); i++) {
                BanRecord ban = bans.get(i);
                if (ban == null || ban.getId() != this.banIds.get(i)) {
                    throw new IllegalArgumentException("bans must list the banIds ids in the same order");
                }
            }
        }
        this.bans = bans == null ? null : Collections.unmodifiableList(new ArrayList<BanRecord>(bans));
    }

    public List<Long> getBanIds() {
        return banIds;
    }

    /**
     * The {@link #getBanIds()} rows in the same order. {@code null} when the host does not report expiries (older
     * host).
     */
    public @Nullable List<BanRecord> getBans() {
        return bans;
    }

    public Builder toBuilder() {
        return new Builder().banIds(banIds).bans(bans);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanResult)) return false;
        BanResult that = (BanResult) o;
        return banIds.equals(that.banIds) && Objects.equals(bans, that.bans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(banIds, bans);
    }

    @Override
    public String toString() {
        return "BanResult[banIds=" + banIds + ", bans=" + bans + "]";
    }

    public static final class Builder {
        private List<Long> banIds;
        private @Nullable List<BanRecord> bans;

        public Builder banIds(List<Long> banIds) {
            this.banIds = banIds;
            return this;
        }

        public Builder bans(@Nullable List<BanRecord> bans) {
            this.bans = bans;
            return this;
        }

        public BanResult build() {
            return new BanResult(banIds, bans);
        }
    }
}
