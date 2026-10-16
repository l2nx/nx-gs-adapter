package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Marks one mail as read by its receiver, online or offline. Idempotent, so no idempotency key or deadline; an already
 * read mail answers {@code OK}. Gson bypasses the constructor, so the handler re-validates.
 *
 * <p>Reply {@code CommandResult<Void>}; {@code NOT_FOUND} / {@code MAIL_NOT_FOUND} when the mail is absent or
 * {@code charId} is not its receiver.</p>
 */
public final class ReadMailCommand implements NxCommand<Void> {

    private final Long charId;
    private final Long mailId;

    public ReadMailCommand(Long charId, Long mailId) {
        if (charId == null) {
            throw new IllegalArgumentException("charId is required");
        }
        if (mailId == null) {
            throw new IllegalArgumentException("mailId is required");
        }
        this.charId = charId;
        this.mailId = mailId;
    }

    public Long getCharId() {
        return charId;
    }

    /** Host mail id (an {@code int} in the core); the same value {@code MailReadEvent} carries. */
    public Long getMailId() {
        return mailId;
    }

    public Builder toBuilder() {
        return new Builder().charId(charId).mailId(mailId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMailCommand)) return false;
        ReadMailCommand that = (ReadMailCommand) o;
        return Objects.equals(charId, that.charId) && Objects.equals(mailId, that.mailId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(charId, mailId);
    }

    @Override
    public String toString() {
        return "ReadMailCommand[charId=" + charId + ", mailId=" + mailId + "]";
    }

    public static final class Builder {
        private Long charId;
        private Long mailId;

        public Builder charId(Long charId) {
            this.charId = charId;
            return this;
        }

        public Builder mailId(Long mailId) {
            this.mailId = mailId;
            return this;
        }

        public ReadMailCommand build() {
            return new ReadMailCommand(charId, mailId);
        }
    }
}
