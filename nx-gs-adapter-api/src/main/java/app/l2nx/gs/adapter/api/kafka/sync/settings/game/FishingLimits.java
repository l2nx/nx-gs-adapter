package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import java.util.Objects;

/** Which limits of one fishing at a time apply; {@code false} = the limit refuses nobody. */
public final class FishingLimits {

    private final boolean accountLimit;
    private final boolean hwidLimit;
    private final boolean ipLimit;

    public FishingLimits(boolean accountLimit, boolean hwidLimit, boolean ipLimit) {
        this.accountLimit = accountLimit;
        this.hwidLimit = hwidLimit;
        this.ipLimit = ipLimit;
    }

    public boolean isAccountLimit() {
        return accountLimit;
    }

    public boolean isHwidLimit() {
        return hwidLimit;
    }

    public boolean isIpLimit() {
        return ipLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FishingLimits)) return false;
        FishingLimits that = (FishingLimits) o;
        return accountLimit == that.accountLimit && hwidLimit == that.hwidLimit && ipLimit == that.ipLimit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountLimit, hwidLimit, ipLimit);
    }

    @Override
    public String toString() {
        return "FishingLimits[accountLimit=" + accountLimit + ", hwidLimit=" + hwidLimit + ", ipLimit=" + ipLimit + "]";
    }
}
