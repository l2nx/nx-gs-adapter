package app.l2nx.gs.adapter.api.kafka.events.mail;

import java.time.Instant;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * Mail row created on the host; one event per row, each with its own {@link #getMailId() mailId}, including bounces of rejected mails ({@link MailReturnedEvent}).
 * <p>{@link #getSubject() subject} and {@link #getBody() body} are plaintext - treat the topic as sensitive.
 * <p>{@link #getMetadata() metadata} is an optional open string-to-string map; {@code null} when absent, consumers ignore unknown keys.
 */
public final class MailSentEvent {

    private final UUID eventId;
    private final long mailId;
    private final long senderCharacterId;
    private final @Nullable String senderName;
    private final long receiverCharacterId;
    private final String subject;
    private final @Nullable String body;
    private final Instant expiresAt;
    private final long codAmount;
    private final List<MailItemMovement> attachments;
    private final @Nullable Map<String, String> metadata;

    public MailSentEvent(
            UUID eventId,
            long mailId,
            long senderCharacterId,
            @Nullable String senderName,
            long receiverCharacterId,
            String subject,
            @Nullable String body,
            Instant expiresAt,
            long codAmount,
            @Nullable List<MailItemMovement> attachments,
            @Nullable Map<String, String> metadata) {
        this.eventId = eventId;
        this.mailId = mailId;
        this.senderCharacterId = senderCharacterId;
        this.senderName = senderName;
        this.receiverCharacterId = receiverCharacterId;
        this.subject = subject;
        this.body = body;
        this.expiresAt = expiresAt;
        this.codAmount = codAmount;
        this.attachments = freezeList(attachments);
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    /** UUIDv7; upper 48 bits encode occurredAt. */
    public UUID getEventId() {
        return eventId;
    }

    /** Host {@code messages} row PK; partition key (8-byte BE) shared by all four lifecycle events of this mail. */
    public long getMailId() {
        return mailId;
    }

    /** {@code 0} for system / NPC mail (see {@link #getSenderName()}). */
    public long getSenderCharacterId() {
        return senderCharacterId;
    }

    /** {@code null} for player-to-player mail; the name then comes from char CDC. */
    public @Nullable String getSenderName() {
        return senderName;
    }

    public long getReceiverCharacterId() {
        return receiverCharacterId;
    }

    public String getSubject() {
        return subject;
    }

    public @Nullable String getBody() {
        return body;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** COD adena the receiver pays on accept; {@code 0} = none. */
    public long getCodAmount() {
        return codAmount;
    }

    public List<MailItemMovement> getAttachments() {
        return attachments;
    }

    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public Builder toBuilder() {
        return new Builder()
                .eventId(eventId)
                .mailId(mailId)
                .senderCharacterId(senderCharacterId)
                .senderName(senderName)
                .receiverCharacterId(receiverCharacterId)
                .subject(subject)
                .body(body)
                .expiresAt(expiresAt)
                .codAmount(codAmount)
                .attachments(attachments)
                .metadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<MailItemMovement> freezeList(@Nullable List<MailItemMovement> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<MailItemMovement>(src));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailSentEvent)) return false;
        MailSentEvent that = (MailSentEvent) o;
        return mailId == that.mailId
                && senderCharacterId == that.senderCharacterId
                && receiverCharacterId == that.receiverCharacterId
                && codAmount == that.codAmount
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(senderName, that.senderName)
                && Objects.equals(subject, that.subject)
                && Objects.equals(body, that.body)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(attachments, that.attachments)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                eventId,
                mailId,
                senderCharacterId,
                senderName,
                receiverCharacterId,
                subject,
                body,
                expiresAt,
                codAmount,
                attachments,
                metadata);
    }

    @Override
    public String toString() {
        return "MailSentEvent[eventId=" + eventId
                + ", mailId=" + mailId
                + ", senderCharacterId=" + senderCharacterId
                + ", senderName=" + senderName
                + ", receiverCharacterId=" + receiverCharacterId
                + ", subject=" + subject
                + ", body=" + body
                + ", expiresAt=" + expiresAt
                + ", codAmount=" + codAmount
                + ", attachments=" + attachments
                + ", metadata=" + metadata + "]";
    }

    public static final class Builder {
        private UUID eventId;
        private long mailId;
        private long senderCharacterId;
        private @Nullable String senderName;
        private long receiverCharacterId;
        private String subject;
        private @Nullable String body;
        private Instant expiresAt;
        private long codAmount;
        private @Nullable List<MailItemMovement> attachments;
        private @Nullable Map<String, String> metadata;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder mailId(long mailId) {
            this.mailId = mailId;
            return this;
        }

        public Builder senderCharacterId(long senderCharacterId) {
            this.senderCharacterId = senderCharacterId;
            return this;
        }

        public Builder senderName(@Nullable String senderName) {
            this.senderName = senderName;
            return this;
        }

        public Builder receiverCharacterId(long receiverCharacterId) {
            this.receiverCharacterId = receiverCharacterId;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(@Nullable String body) {
            this.body = body;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder codAmount(long codAmount) {
            this.codAmount = codAmount;
            return this;
        }

        public Builder attachments(@Nullable List<MailItemMovement> attachments) {
            this.attachments = attachments;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public MailSentEvent build() {
            return new MailSentEvent(
                    eventId,
                    mailId,
                    senderCharacterId,
                    senderName,
                    receiverCharacterId,
                    subject,
                    body,
                    expiresAt,
                    codAmount,
                    attachments,
                    metadata);
        }
    }
}
