package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import java.util.Objects;

/**
 * Adena the sender pays for one mail: {@code feeAdena + feeAdenaPerAttachment * attachedLines}, where every attached
 * item line counts once whatever its quantity.
 */
public final class MailSettings {

    private final long feeAdena;
    private final long feeAdenaPerAttachment;

    public MailSettings(long feeAdena, long feeAdenaPerAttachment) {
        if (feeAdena < 0 || feeAdenaPerAttachment < 0) {
            throw new IllegalArgumentException("MailSettings fees must not be negative");
        }
        this.feeAdena = feeAdena;
        this.feeAdenaPerAttachment = feeAdenaPerAttachment;
    }

    public long getFeeAdena() {
        return feeAdena;
    }

    public long getFeeAdenaPerAttachment() {
        return feeAdenaPerAttachment;
    }

    public Builder toBuilder() {
        return new Builder().feeAdena(feeAdena).feeAdenaPerAttachment(feeAdenaPerAttachment);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailSettings)) return false;
        MailSettings that = (MailSettings) o;
        return feeAdena == that.feeAdena && feeAdenaPerAttachment == that.feeAdenaPerAttachment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(feeAdena, feeAdenaPerAttachment);
    }

    @Override
    public String toString() {
        return "MailSettings[feeAdena=" + feeAdena + ", feeAdenaPerAttachment=" + feeAdenaPerAttachment + "]";
    }

    public static final class Builder {
        private long feeAdena;
        private long feeAdenaPerAttachment;

        public Builder feeAdena(long feeAdena) {
            this.feeAdena = feeAdena;
            return this;
        }

        public Builder feeAdenaPerAttachment(long feeAdenaPerAttachment) {
            this.feeAdenaPerAttachment = feeAdenaPerAttachment;
            return this;
        }

        public MailSettings build() {
            return new MailSettings(feeAdena, feeAdenaPerAttachment);
        }
    }
}
