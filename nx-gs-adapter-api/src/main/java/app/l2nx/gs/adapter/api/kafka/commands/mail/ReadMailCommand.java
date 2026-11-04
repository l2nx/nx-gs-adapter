package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Marks one mail as read by its receiver, online or offline. Idempotent, so no idempotency key or deadline; an already
 * read mail answers {@code OK}. Gson bypasses the constructor, so the handler re-validates.
 *
 * <p>Reply {@code CommandResult<Void>}; {@code NOT_FOUND} / {@code MAIL_NOT_FOUND} when the mail is absent or
 * {@code characterId} is not its receiver.</p>
 */
public final class ReadMailCommand implements NxCommand<Void> {

    private final Long characterId;
    private final Long mailId;
    private final CommandInitiator initiator;

    public ReadMailCommand(Long characterId, Long mailId, CommandInitiator initiator) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        if (mailId == null) {
            throw new IllegalArgumentException("mailId is required");
        }
        this.characterId = characterId;
        this.mailId = mailId;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharacterId() {
        return characterId;
    }

    /** Host mail id (an {@code int} in the core); the same value {@code MailReadEvent} carries. */
    public Long getMailId() {
        return mailId;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder().characterId(characterId).mailId(mailId).initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMailCommand)) return false;
        ReadMailCommand that = (ReadMailCommand) o;
        return Objects.equals(characterId, that.characterId)
                && Objects.equals(mailId, that.mailId)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, mailId, initiator);
    }

    @Override
    public String toString() {
        return "ReadMailCommand[characterId=" + characterId + ", mailId=" + mailId + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long characterId;
        private Long mailId;
        private CommandInitiator initiator;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder mailId(Long mailId) {
            this.mailId = mailId;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public ReadMailCommand build() {
            return new ReadMailCommand(characterId, mailId, initiator);
        }
    }
}
