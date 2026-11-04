package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Recipient-side counterpart of {@link SendPlayerMailCommand}: accepts one mail's attachments and pays its COD;
 * all-or-nothing, idempotent per {@code idempotencyKey}, routed by {@code receiverCharacterId}. A live mail not matching the
 * {@code expected*} fields is {@code MAIL_NOT_FOUND}; Gson bypasses the constructor, so the handler re-validates.
 */
public final class AcceptMailCommand implements OwnerVerified, NxCommand<AcceptMailResult> {

    private final Long receiverCharacterId;
    private final Long mailId;
    private final Long expectedSenderCharacterId;
    private final Long expectedCodAdena;
    private final @Nullable Long acceptedFeeAdena;
    private final boolean ownerVerified;
    private final UUID idempotencyKey;
    private final Instant deadline;
    private final CommandInitiator initiator;

    public AcceptMailCommand(
            Long receiverCharacterId,
            Long mailId,
            Long expectedSenderCharacterId,
            Long expectedCodAdena,
            @Nullable Long acceptedFeeAdena,
            boolean ownerVerified,
            UUID idempotencyKey,
            Instant deadline,
            CommandInitiator initiator) {
        if (receiverCharacterId == null) {
            throw new IllegalArgumentException("receiverCharacterId is required");
        }
        if (mailId == null) {
            throw new IllegalArgumentException("mailId is required");
        }
        if (expectedSenderCharacterId == null) {
            throw new IllegalArgumentException("expectedSenderCharacterId is required");
        }
        if (expectedCodAdena == null) {
            throw new IllegalArgumentException("expectedCodAdena is required");
        }
        if (expectedCodAdena < 0) {
            throw new IllegalArgumentException("expectedCodAdena must not be negative (got " + expectedCodAdena + ")");
        }
        if (acceptedFeeAdena != null && acceptedFeeAdena < 0) {
            throw new IllegalArgumentException(
                    "acceptedFeeAdena must not be negative when present (got " + acceptedFeeAdena + ")");
        }
        this.receiverCharacterId = receiverCharacterId;
        this.mailId = mailId;
        this.expectedSenderCharacterId = expectedSenderCharacterId;
        this.expectedCodAdena = expectedCodAdena;
        this.acceptedFeeAdena = acceptedFeeAdena;
        this.ownerVerified = ownerVerified;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    /** Items and adena move to and from this character; it need not be online. */
    public Long getReceiverCharacterId() {
        return receiverCharacterId;
    }

    public Long getMailId() {
        return mailId;
    }

    public Long getExpectedSenderCharacterId() {
        return expectedSenderCharacterId;
    }

    public Long getExpectedCodAdena() {
        return expectedCodAdena;
    }

    /**
     * Largest receive fee the player agreed to pay, {@code null} = no consent yet; the host charges exactly the fee it
     * computed, never this amount.
     */
    public @Nullable Long getAcceptedFeeAdena() {
        return acceptedFeeAdena;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    /** Stable across retries, unlike {@code correlationId} (one attempt); the host deduplicates and replays by it. */
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Past this the host refuses with {@code COMMAND_EXPIRED}; guards against Kafka backlog. */
    public Instant getDeadline() {
        return deadline;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .receiverCharacterId(receiverCharacterId)
                .mailId(mailId)
                .expectedSenderCharacterId(expectedSenderCharacterId)
                .expectedCodAdena(expectedCodAdena)
                .acceptedFeeAdena(acceptedFeeAdena)
                .ownerVerified(ownerVerified)
                .idempotencyKey(idempotencyKey)
                .deadline(deadline)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AcceptMailCommand)) return false;
        AcceptMailCommand that = (AcceptMailCommand) o;
        return ownerVerified == that.ownerVerified
                && Objects.equals(receiverCharacterId, that.receiverCharacterId)
                && Objects.equals(mailId, that.mailId)
                && Objects.equals(expectedSenderCharacterId, that.expectedSenderCharacterId)
                && Objects.equals(expectedCodAdena, that.expectedCodAdena)
                && Objects.equals(acceptedFeeAdena, that.acceptedFeeAdena)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                receiverCharacterId,
                mailId,
                expectedSenderCharacterId,
                expectedCodAdena,
                acceptedFeeAdena,
                ownerVerified,
                idempotencyKey,
                deadline,
                initiator);
    }

    @Override
    public String toString() {
        return "AcceptMailCommand[receiverCharacterId=" + receiverCharacterId
                + ", mailId=" + mailId
                + ", expectedSenderCharacterId=" + expectedSenderCharacterId
                + ", expectedCodAdena=" + expectedCodAdena
                + ", acceptedFeeAdena=" + acceptedFeeAdena
                + ", ownerVerified=" + ownerVerified
                + ", idempotencyKey=" + idempotencyKey
                + ", deadline=" + deadline + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long receiverCharacterId;
        private Long mailId;
        private Long expectedSenderCharacterId;
        private Long expectedCodAdena;
        private @Nullable Long acceptedFeeAdena;
        private boolean ownerVerified;
        private UUID idempotencyKey;
        private Instant deadline;
        private CommandInitiator initiator;

        public Builder receiverCharacterId(Long receiverCharacterId) {
            this.receiverCharacterId = receiverCharacterId;
            return this;
        }

        public Builder mailId(Long mailId) {
            this.mailId = mailId;
            return this;
        }

        public Builder expectedSenderCharacterId(Long expectedSenderCharacterId) {
            this.expectedSenderCharacterId = expectedSenderCharacterId;
            return this;
        }

        public Builder expectedCodAdena(Long expectedCodAdena) {
            this.expectedCodAdena = expectedCodAdena;
            return this;
        }

        public Builder acceptedFeeAdena(@Nullable Long acceptedFeeAdena) {
            this.acceptedFeeAdena = acceptedFeeAdena;
            return this;
        }

        public Builder ownerVerified(boolean ownerVerified) {
            this.ownerVerified = ownerVerified;
            return this;
        }

        public Builder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public AcceptMailCommand build() {
            return new AcceptMailCommand(
                    receiverCharacterId,
                    mailId,
                    expectedSenderCharacterId,
                    expectedCodAdena,
                    acceptedFeeAdena,
                    ownerVerified,
                    idempotencyKey,
                    deadline,
                    initiator);
        }
    }
}
