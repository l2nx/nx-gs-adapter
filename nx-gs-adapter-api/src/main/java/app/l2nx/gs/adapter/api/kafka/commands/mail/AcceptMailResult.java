package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.events.mail.MailItemMovement;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Reply payload of {@link AcceptMailCommand}, carried only on status {@code OK}; an unknown outcome is an
 * {@code INTERNAL_ERROR} with no payload, so the caller treats an accept as done only on {@code OK}.
 */
public final class AcceptMailResult {

    private final long mailId;
    private final List<MailItemMovement> items;
    private final long codPaidAdena;
    private final long receiveFeeAdena;

    public AcceptMailResult(
            long mailId, @Nullable List<MailItemMovement> items, long codPaidAdena, long receiveFeeAdena) {
        if (codPaidAdena < 0) {
            throw new IllegalArgumentException("codPaidAdena must be >= 0 (got " + codPaidAdena + ")");
        }
        if (receiveFeeAdena < 0) {
            throw new IllegalArgumentException("receiveFeeAdena must be >= 0 (got " + receiveFeeAdena + ")");
        }
        this.mailId = mailId;
        this.items = MailLists.freeze(items);
        this.codPaidAdena = codPaidAdena;
        this.receiveFeeAdena = receiveFeeAdena;
    }

    public long getMailId() {
        return mailId;
    }

    /** Non-null; empty for a mail without item attachments. */
    public List<MailItemMovement> getItems() {
        return items;
    }

    public long getCodPaidAdena() {
        return codPaidAdena;
    }

    public long getReceiveFeeAdena() {
        return receiveFeeAdena;
    }

    public Builder toBuilder() {
        return new Builder()
                .mailId(mailId)
                .items(items)
                .codPaidAdena(codPaidAdena)
                .receiveFeeAdena(receiveFeeAdena);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AcceptMailResult)) return false;
        AcceptMailResult that = (AcceptMailResult) o;
        return mailId == that.mailId
                && codPaidAdena == that.codPaidAdena
                && receiveFeeAdena == that.receiveFeeAdena
                && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mailId, items, codPaidAdena, receiveFeeAdena);
    }

    @Override
    public String toString() {
        return "AcceptMailResult[mailId=" + mailId
                + ", items=" + items
                + ", codPaidAdena=" + codPaidAdena
                + ", receiveFeeAdena=" + receiveFeeAdena + "]";
    }

    public static final class Builder {
        private long mailId;
        private @Nullable List<MailItemMovement> items;
        private long codPaidAdena;
        private long receiveFeeAdena;

        public Builder mailId(long mailId) {
            this.mailId = mailId;
            return this;
        }

        public Builder items(@Nullable List<MailItemMovement> items) {
            this.items = items;
            return this;
        }

        public Builder codPaidAdena(long codPaidAdena) {
            this.codPaidAdena = codPaidAdena;
            return this;
        }

        public Builder receiveFeeAdena(long receiveFeeAdena) {
            this.receiveFeeAdena = receiveFeeAdena;
            return this;
        }

        public AcceptMailResult build() {
            return new AcceptMailResult(mailId, items, codPaidAdena, receiveFeeAdena);
        }
    }
}
