package app.l2nx.gs.adapter.api.spi.model.receipt;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ReceiptRow {

    private final String key;
    private final String command;
    private final ReceiptStatus status;
    private final @Nullable Integer buyerCharacterId;
    private final @Nullable Integer sellerCharacterId;
    private final @Nullable Long chargedAdena;
    private final @Nullable Integer mailId;
    private final @Nullable String claimJson;

    private ReceiptRow(Builder b) {
        this.key = Objects.requireNonNull(b.key, "key");
        this.command = Objects.requireNonNull(b.command, "command");
        this.status = Objects.requireNonNull(b.status, "status");
        this.buyerCharacterId = b.buyerCharacterId;
        this.sellerCharacterId = b.sellerCharacterId;
        this.chargedAdena = b.chargedAdena;
        this.mailId = b.mailId;
        this.claimJson = b.claimJson;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getKey() {
        return key;
    }

    public String getCommand() {
        return command;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public @Nullable Integer getBuyerCharacterId() {
        return buyerCharacterId;
    }

    public @Nullable Integer getSellerCharacterId() {
        return sellerCharacterId;
    }

    /** {@code null} until a charge or settle writes it; {@code 0} after a recorded refund. */
    public @Nullable Long getChargedAdena() {
        return chargedAdena;
    }

    public @Nullable Integer getMailId() {
        return mailId;
    }

    public @Nullable String getClaimJson() {
        return claimJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReceiptRow)) return false;
        ReceiptRow that = (ReceiptRow) o;
        return key.equals(that.key)
                && command.equals(that.command)
                && status == that.status
                && Objects.equals(buyerCharacterId, that.buyerCharacterId)
                && Objects.equals(sellerCharacterId, that.sellerCharacterId)
                && Objects.equals(chargedAdena, that.chargedAdena)
                && Objects.equals(mailId, that.mailId)
                && Objects.equals(claimJson, that.claimJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, command, status, buyerCharacterId, sellerCharacterId, chargedAdena, mailId, claimJson);
    }

    @Override
    public String toString() {
        return "ReceiptRow[key=" + key
                + ", command=" + command
                + ", status=" + status
                + ", buyerCharacterId=" + buyerCharacterId
                + ", sellerCharacterId=" + sellerCharacterId
                + ", chargedAdena=" + chargedAdena
                + ", mailId=" + mailId
                + ", claimJson=" + claimJson + "]";
    }

    public static final class Builder {
        private @Nullable String key;
        private @Nullable String command;
        private @Nullable ReceiptStatus status;
        private @Nullable Integer buyerCharacterId;
        private @Nullable Integer sellerCharacterId;
        private @Nullable Long chargedAdena;
        private @Nullable Integer mailId;
        private @Nullable String claimJson;

        private Builder() {}

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder status(ReceiptStatus status) {
            this.status = status;
            return this;
        }

        public Builder buyerCharacterId(@Nullable Integer buyerCharacterId) {
            this.buyerCharacterId = buyerCharacterId;
            return this;
        }

        public Builder sellerCharacterId(@Nullable Integer sellerCharacterId) {
            this.sellerCharacterId = sellerCharacterId;
            return this;
        }

        public Builder chargedAdena(@Nullable Long chargedAdena) {
            this.chargedAdena = chargedAdena;
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

        public ReceiptRow build() {
            return new ReceiptRow(this);
        }
    }
}
