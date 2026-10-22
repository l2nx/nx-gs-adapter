package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.PrivateStoreFees;

/**
 * Seller fee parsed from purchase-event metadata by {@link WellKnownPrivateStoreMetadata#sellerFee}. Always valid:
 * {@code adena >= 0} (a small deal rounds to zero) and {@code percent} within {@code 0..}{@link PrivateStoreFees#MAX_FEE_PERCENT}.
 */
public final class SellerFee {

    private final long adena;
    private final int percent;

    private SellerFee(long adena, int percent) {
        this.adena = adena;
        this.percent = percent;
    }

    public static SellerFee of(long adena, int percent) {
        if (adena < 0) {
            throw new IllegalArgumentException("adena must not be negative (got " + adena + ")");
        }
        if (!PrivateStoreFees.isValidPercent(percent)) {
            throw new IllegalArgumentException(
                    "percent must be in 0.." + PrivateStoreFees.MAX_FEE_PERCENT + " (got " + percent + ")");
        }
        return new SellerFee(adena, percent);
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
        SellerFee that = (SellerFee) o;
        return adena == that.adena && percent == that.percent;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(adena) + percent;
    }

    @Override
    public String toString() {
        return "SellerFee[adena=" + adena + ", percent=" + percent + "]";
    }
}
