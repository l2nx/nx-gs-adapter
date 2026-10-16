package app.l2nx.gs.adapter.api.spi.model.receipt;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a claim records next to the key: the parties of a purchase and the mail that carries its goods, or an opaque
 * host JSON from which recovery can tell what the command was about to do. Every field is optional.
 */
public final class ReceiptClaim {

    private final @Nullable Integer buyerCharacterId;
    private final @Nullable Integer sellerCharacterId;
    private final @Nullable Integer mailId;
    private final @Nullable String claimJson;

    private ReceiptClaim(Builder b) {
        this.buyerCharacterId = b.buyerCharacterId;
        this.sellerCharacterId = b.sellerCharacterId;
        this.mailId = b.mailId;
        this.claimJson = b.claimJson;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ReceiptClaim purchase(int buyerCharacterId, int sellerCharacterId, int mailId) {
        return builder()
                .buyerCharacterId(buyerCharacterId)
                .sellerCharacterId(sellerCharacterId)
                .mailId(mailId)
                .build();
    }

    public static ReceiptClaim ofClaimJson(String claimJson) {
        return builder()
                .claimJson(Objects.requireNonNull(claimJson, "claimJson"))
                .build();
    }

    public @Nullable Integer getBuyerCharacterId() {
        return buyerCharacterId;
    }

    public @Nullable Integer getSellerCharacterId() {
        return sellerCharacterId;
    }

    public @Nullable Integer getMailId() {
        return mailId;
    }

    /** Host-defined; the store keeps it verbatim and compares it byte for byte in the amend operations. */
    public @Nullable String getClaimJson() {
        return claimJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReceiptClaim)) return false;
        ReceiptClaim that = (ReceiptClaim) o;
        return Objects.equals(buyerCharacterId, that.buyerCharacterId)
                && Objects.equals(sellerCharacterId, that.sellerCharacterId)
                && Objects.equals(mailId, that.mailId)
                && Objects.equals(claimJson, that.claimJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buyerCharacterId, sellerCharacterId, mailId, claimJson);
    }

    @Override
    public String toString() {
        return "ReceiptClaim[buyerCharacterId=" + buyerCharacterId
                + ", sellerCharacterId=" + sellerCharacterId
                + ", mailId=" + mailId
                + ", claimJson=" + claimJson + "]";
    }

    public static final class Builder {
        private @Nullable Integer buyerCharacterId;
        private @Nullable Integer sellerCharacterId;
        private @Nullable Integer mailId;
        private @Nullable String claimJson;

        private Builder() {}

        public Builder buyerCharacterId(@Nullable Integer buyerCharacterId) {
            this.buyerCharacterId = buyerCharacterId;
            return this;
        }

        public Builder sellerCharacterId(@Nullable Integer sellerCharacterId) {
            this.sellerCharacterId = sellerCharacterId;
            return this;
        }

        public Builder mailId(@Nullable Integer mailId) {
            this.mailId = mailId;
            return this;
        }

        public Builder claimJson(@Nullable String claimJson) {
            this.claimJson = claimJson;
            return this;
        }

        public ReceiptClaim build() {
            return new ReceiptClaim(this);
        }
    }
}
