package app.l2nx.gs.adapter.api.kafka.commands.mail;

/** Only on OK replies: the mail exists with every attachment and the fee is charged, so there is no partial shape. */
public final class SendPlayerMailResult {

    private final long mailId;
    private final long feeAdena;

    public SendPlayerMailResult(long mailId, long feeAdena) {
        this.mailId = mailId;
        this.feeAdena = feeAdena;
    }

    /** Eventually consistent: the mail-read API may 404 until the asynchronous mail-ingest catches up. */
    public long getMailId() {
        return mailId;
    }

    /** Adena the host charged the sender for this mail only, on top of any attached adena; never {@code 0}. */
    public long getFeeAdena() {
        return feeAdena;
    }

    public Builder toBuilder() {
        return new Builder().mailId(mailId).feeAdena(feeAdena);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendPlayerMailResult)) return false;
        SendPlayerMailResult that = (SendPlayerMailResult) o;
        return mailId == that.mailId && feeAdena == that.feeAdena;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(mailId) + Long.hashCode(feeAdena);
    }

    @Override
    public String toString() {
        return "SendPlayerMailResult[mailId=" + mailId + ", feeAdena=" + feeAdena + "]";
    }

    public static final class Builder {
        private long mailId;
        private long feeAdena;

        public Builder mailId(long mailId) {
            this.mailId = mailId;
            return this;
        }

        public Builder feeAdena(long feeAdena) {
            this.feeAdena = feeAdena;
            return this;
        }

        public SendPlayerMailResult build() {
            return new SendPlayerMailResult(mailId, feeAdena);
        }
    }
}
