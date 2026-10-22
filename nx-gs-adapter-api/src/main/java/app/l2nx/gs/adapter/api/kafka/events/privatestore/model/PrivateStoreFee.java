package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

/** Fee parsed by {@link PrivateStoreMetadata}; {@code adena} is zero when a small deal rounds down. */
public final class PrivateStoreFee {

    /** Not a policy cap: above it the seller's proceeds would go negative. */
    public static final int MAX_SELLER_FEE_PERCENT = 100;

    private final long adena;
    private final int percent;

    private PrivateStoreFee(long adena, int percent) {
        this.adena = adena;
        this.percent = percent;
    }

    public static PrivateStoreFee of(long adena, int percent) {
        if (adena < 0) {
            throw new IllegalArgumentException("adena must not be negative (got " + adena + ")");
        }
        if (percent < 0) {
            throw new IllegalArgumentException("percent must not be negative (got " + percent + ")");
        }
        return new PrivateStoreFee(adena, percent);
    }

    public long getAdena() {
        return adena;
    }

    public int getPercent() {
        return percent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrivateStoreFee that = (PrivateStoreFee) o;
        return adena == that.adena && percent == that.percent;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(adena) + percent;
    }

    @Override
    public String toString() {
        return "PrivateStoreFee[adena=" + adena + ", percent=" + percent + "]";
    }
}
