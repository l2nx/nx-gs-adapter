package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.BoughtLine;
import java.util.List;
import java.util.Objects;

/**
 * Only on OK replies. {@code paidTotalAdena = itemsTotalAdena + buyerFeeAdena}; the seller is credited
 * {@code itemsTotalAdena - sellerFeeAdena}. The constructor enforces both and {@code 0 <= sellerFeeAdena <= itemsTotalAdena}.
 */
public final class BuyFromPrivateStoreResult {

    private final long itemsTotalAdena;
    private final long buyerFeeAdena;
    private final long sellerFeeAdena;
    private final long paidTotalAdena;
    private final List<BoughtLine> bought;
    private final boolean storeClosed;
    private final long mailId;

    public BuyFromPrivateStoreResult(
            long itemsTotalAdena,
            long buyerFeeAdena,
            long sellerFeeAdena,
            long paidTotalAdena,
            List<BoughtLine> bought,
            boolean storeClosed,
            long mailId) {
        if (paidTotalAdena != itemsTotalAdena + buyerFeeAdena) {
            throw new IllegalArgumentException("paidTotalAdena (" + paidTotalAdena
                    + ") must equal itemsTotalAdena + buyerFeeAdena (" + (itemsTotalAdena + buyerFeeAdena) + ")");
        }
        if (sellerFeeAdena < 0 || sellerFeeAdena > itemsTotalAdena) {
            throw new IllegalArgumentException(
                    "sellerFeeAdena (" + sellerFeeAdena + ") must be in 0..itemsTotalAdena (" + itemsTotalAdena + ")");
        }
        this.itemsTotalAdena = itemsTotalAdena;
        this.buyerFeeAdena = buyerFeeAdena;
        this.sellerFeeAdena = sellerFeeAdena;
        this.paidTotalAdena = paidTotalAdena;
        this.bought = PrivateStoreLists.freeze(bought);
        this.storeClosed = storeClosed;
        this.mailId = mailId;
    }

    public long getItemsTotalAdena() {
        return itemsTotalAdena;
    }

    /** Burned: debited from the buyer on top of the lots, credited to nobody. */
    public long getBuyerFeeAdena() {
        return buyerFeeAdena;
    }

    /** Burned: withheld from the seller's proceeds, not paid by the buyer; {@code 0} when the store carries no fee. */
    public long getSellerFeeAdena() {
        return sellerFeeAdena;
    }

    public long getPaidTotalAdena() {
        return paidTotalAdena;
    }

    public List<BoughtLine> getBought() {
        return bought;
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
                .buyerFeeAdena(buyerFeeAdena)
                .sellerFeeAdena(sellerFeeAdena)
                .paidTotalAdena(paidTotalAdena)
                .bought(bought)
                .storeClosed(storeClosed)
                .mailId(mailId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyFromPrivateStoreResult)) return false;
        BuyFromPrivateStoreResult that = (BuyFromPrivateStoreResult) o;
        return itemsTotalAdena == that.itemsTotalAdena
                && buyerFeeAdena == that.buyerFeeAdena
                && sellerFeeAdena == that.sellerFeeAdena
                && paidTotalAdena == that.paidTotalAdena
                && storeClosed == that.storeClosed
                && mailId == that.mailId
                && Objects.equals(bought, that.bought);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                itemsTotalAdena,
                buyerFeeAdena,
                sellerFeeAdena,
                paidTotalAdena,
                bought,
                Boolean.valueOf(storeClosed),
                mailId);
    }

    @Override
    public String toString() {
        return "BuyFromPrivateStoreResult[itemsTotalAdena=" + itemsTotalAdena
                + ", buyerFeeAdena=" + buyerFeeAdena
                + ", sellerFeeAdena=" + sellerFeeAdena
                + ", paidTotalAdena=" + paidTotalAdena
                + ", bought=" + bought
                + ", storeClosed=" + storeClosed
                + ", mailId=" + mailId + "]";
    }

    public static final class Builder {
        private long itemsTotalAdena;
        private long buyerFeeAdena;
        private long sellerFeeAdena;
        private long paidTotalAdena;
        private List<BoughtLine> bought;
        private boolean storeClosed;
        private long mailId;

        public Builder itemsTotalAdena(long itemsTotalAdena) {
            this.itemsTotalAdena = itemsTotalAdena;
            return this;
        }

        public Builder buyerFeeAdena(long buyerFeeAdena) {
            this.buyerFeeAdena = buyerFeeAdena;
            return this;
        }

        public Builder sellerFeeAdena(long sellerFeeAdena) {
            this.sellerFeeAdena = sellerFeeAdena;
            return this;
        }

        public Builder paidTotalAdena(long paidTotalAdena) {
            this.paidTotalAdena = paidTotalAdena;
            return this;
        }

        public Builder bought(List<BoughtLine> bought) {
            this.bought = bought;
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

        public BuyFromPrivateStoreResult build() {
            return new BuyFromPrivateStoreResult(
                    itemsTotalAdena, buyerFeeAdena, sellerFeeAdena, paidTotalAdena, bought, storeClosed, mailId);
        }
    }
}
