package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SoldLine;
import java.util.List;
import java.util.Objects;

/**
 * Only on OK replies. The seller is credited {@code receivedTotalAdena = itemsTotalAdena - sellerFeeAdena} by mail; the
 * store owner pays {@code itemsTotalAdena + buyerFeeAdena}; both fees are burned. The constructor enforces
 * {@code receivedTotalAdena == itemsTotalAdena - sellerFeeAdena}, {@code 0 <= sellerFeeAdena <= itemsTotalAdena} and
 * {@code buyerFeeAdena >= 0}.
 */
public final class SellToPrivateStoreResult {

    private final long itemsTotalAdena;
    private final long sellerFeeAdena;
    private final long receivedTotalAdena;
    private final long buyerFeeAdena;
    private final List<SoldLine> sold;
    private final boolean storeClosed;
    private final long mailId;

    public SellToPrivateStoreResult(
            long itemsTotalAdena,
            long sellerFeeAdena,
            long receivedTotalAdena,
            long buyerFeeAdena,
            List<SoldLine> sold,
            boolean storeClosed,
            long mailId) {
        if (sellerFeeAdena < 0 || sellerFeeAdena > itemsTotalAdena) {
            throw new IllegalArgumentException(
                    "sellerFeeAdena (" + sellerFeeAdena + ") must be in 0..itemsTotalAdena (" + itemsTotalAdena + ")");
        }
        if (receivedTotalAdena != itemsTotalAdena - sellerFeeAdena) {
            throw new IllegalArgumentException("receivedTotalAdena (" + receivedTotalAdena
                    + ") must equal itemsTotalAdena - sellerFeeAdena (" + (itemsTotalAdena - sellerFeeAdena) + ")");
        }
        if (buyerFeeAdena < 0) {
            throw new IllegalArgumentException("buyerFeeAdena must not be negative (got " + buyerFeeAdena + ")");
        }
        this.itemsTotalAdena = itemsTotalAdena;
        this.sellerFeeAdena = sellerFeeAdena;
        this.receivedTotalAdena = receivedTotalAdena;
        this.buyerFeeAdena = buyerFeeAdena;
        this.sold = PrivateStoreLists.freeze(sold);
        this.storeClosed = storeClosed;
        this.mailId = mailId;
    }

    public long getItemsTotalAdena() {
        return itemsTotalAdena;
    }

    /** Burned: withheld from the seller's proceeds, not paid by the store owner; {@code 0} when the rate is {@code 0}. */
    public long getSellerFeeAdena() {
        return sellerFeeAdena;
    }

    /** Credited to the seller by mail. */
    public long getReceivedTotalAdena() {
        return receivedTotalAdena;
    }

    /** Burned: debited from the store owner on top of the lots; {@code 0} for a store opened in game. */
    public long getBuyerFeeAdena() {
        return buyerFeeAdena;
    }

    public List<SoldLine> getSold() {
        return sold;
    }

    public boolean isStoreClosed() {
        return storeClosed;
    }

    /** Eventually consistent: the mail-read API may 404 until the asynchronous mail-ingest catches up. */
    public long getMailId() {
        return mailId;
    }

    public Builder toBuilder() {
        return new Builder()
                .itemsTotalAdena(itemsTotalAdena)
                .sellerFeeAdena(sellerFeeAdena)
                .receivedTotalAdena(receivedTotalAdena)
                .buyerFeeAdena(buyerFeeAdena)
                .sold(sold)
                .storeClosed(storeClosed)
                .mailId(mailId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellToPrivateStoreResult)) return false;
        SellToPrivateStoreResult that = (SellToPrivateStoreResult) o;
        return itemsTotalAdena == that.itemsTotalAdena
                && sellerFeeAdena == that.sellerFeeAdena
                && receivedTotalAdena == that.receivedTotalAdena
                && buyerFeeAdena == that.buyerFeeAdena
                && storeClosed == that.storeClosed
                && mailId == that.mailId
                && Objects.equals(sold, that.sold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                itemsTotalAdena,
                sellerFeeAdena,
                receivedTotalAdena,
                buyerFeeAdena,
                sold,
                Boolean.valueOf(storeClosed),
                mailId);
    }

    @Override
    public String toString() {
        return "SellToPrivateStoreResult[itemsTotalAdena=" + itemsTotalAdena
                + ", sellerFeeAdena=" + sellerFeeAdena
                + ", receivedTotalAdena=" + receivedTotalAdena
                + ", buyerFeeAdena=" + buyerFeeAdena
                + ", sold=" + sold
                + ", storeClosed=" + storeClosed
                + ", mailId=" + mailId + "]";
    }

    public static final class Builder {
        private long itemsTotalAdena;
        private long sellerFeeAdena;
        private long receivedTotalAdena;
        private long buyerFeeAdena;
        private List<SoldLine> sold;
        private boolean storeClosed;
        private long mailId;

        public Builder itemsTotalAdena(long itemsTotalAdena) {
            this.itemsTotalAdena = itemsTotalAdena;
            return this;
        }

        public Builder sellerFeeAdena(long sellerFeeAdena) {
            this.sellerFeeAdena = sellerFeeAdena;
            return this;
        }

        public Builder receivedTotalAdena(long receivedTotalAdena) {
            this.receivedTotalAdena = receivedTotalAdena;
            return this;
        }

        public Builder buyerFeeAdena(long buyerFeeAdena) {
            this.buyerFeeAdena = buyerFeeAdena;
            return this;
        }

        public Builder sold(List<SoldLine> sold) {
            this.sold = sold;
            return this;
        }

        public Builder storeClosed(boolean storeClosed) {
            this.storeClosed = storeClosed;
            return this;
        }

        public Builder mailId(long mailId) {
            this.mailId = mailId;
            return this;
        }

        public SellToPrivateStoreResult build() {
            return new SellToPrivateStoreResult(
                    itemsTotalAdena, sellerFeeAdena, receivedTotalAdena, buyerFeeAdena, sold, storeClosed, mailId);
        }
    }
}
