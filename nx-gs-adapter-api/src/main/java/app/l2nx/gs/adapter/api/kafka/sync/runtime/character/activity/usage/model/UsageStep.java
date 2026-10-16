package app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model;

import java.util.Objects;

/** One tier of an {@link ActivityUsage}: once {@code after} is consumed, the activity's effect is scaled. */
public final class UsageStep {

    private final long after;
    private final double multiplier;

    public UsageStep(long after, double multiplier) {
        if (after < 0) {
            throw new IllegalArgumentException("UsageStep.after must not be negative");
        }
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("UsageStep.multiplier must be a finite non-negative number");
        }
        this.after = after;
        this.multiplier = multiplier;
    }

    /** Consumed amount at which the tier starts, in the {@code unit} of the owning row. */
    public long getAfter() {
        return after;
    }

    /** Factor applied to the activity's effect (for fishing, the catch chance) from {@link #getAfter() after} on. */
    public double getMultiplier() {
        return multiplier;
    }

    public Builder toBuilder() {
        return new Builder().after(after).multiplier(multiplier);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsageStep)) return false;
        UsageStep that = (UsageStep) o;
        return after == that.after && Double.compare(multiplier, that.multiplier) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(after, multiplier);
    }

    @Override
    public String toString() {
        return "UsageStep[after=" + after + ", multiplier=" + multiplier + "]";
    }

    public static final class Builder {
        private long after;
        private double multiplier;

        public Builder after(long after) {
            this.after = after;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public UsageStep build() {
            return new UsageStep(after, multiplier);
        }
    }
}
