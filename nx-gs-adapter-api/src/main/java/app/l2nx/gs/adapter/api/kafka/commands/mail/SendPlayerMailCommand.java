package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.mail.model.WellKnownSendPlayerMailReasons;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Mail from a character (online or offline) to another, optionally cash on delivery; items and adena are taken from
 * the sender. All-or-nothing, idempotent per {@code idempotencyKey}, routed by {@code senderCharacterId}. Host-configured
 * limits (attachment count, maximum adena) are enforced by the host, not the wire type.
 *
 * <p>Refusals carry a {@code reason} from {@link WellKnownSendPlayerMailReasons} in
 * {@link app.l2nx.gs.adapter.api.kafka.commands.CommandProblem#getExtensions() CommandProblem.extensions}, or a
 * {@code field} extension when malformed. Gson bypasses the constructor, so the handler re-validates.</p>
 */
public final class SendPlayerMailCommand implements OwnerVerified, NxCommand<SendPlayerMailResult> {

    private final Long senderCharacterId;
    private final Long recipientCharacterId;
    private final String title;
    private final @Nullable String body;
    private final List<PlayerMailItem> items;
    private final @Nullable Long adena;
    private final @Nullable Long codPrice;
    private final boolean ownerVerified;
    private final UUID idempotencyKey;
    private final Instant deadline;
    private final CommandInitiator initiator;

    public SendPlayerMailCommand(
            Long senderCharacterId,
            Long recipientCharacterId,
            String title,
            @Nullable String body,
            @Nullable List<PlayerMailItem> items,
            @Nullable Long adena,
            @Nullable Long codPrice,
            boolean ownerVerified,
            UUID idempotencyKey,
            Instant deadline,
            CommandInitiator initiator) {
        if (senderCharacterId == null) {
            throw new IllegalArgumentException("senderCharacterId is required");
        }
        if (recipientCharacterId == null) {
            throw new IllegalArgumentException("recipientCharacterId is required");
        }
        if (title == null) {
            throw new IllegalArgumentException("title is required");
        }
        if (adena != null && adena <= 0) {
            throw new IllegalArgumentException("adena must be positive when present");
        }
        if (codPrice != null && codPrice <= 0) {
            throw new IllegalArgumentException("codPrice must be positive when present");
        }
        this.senderCharacterId = senderCharacterId;
        this.recipientCharacterId = recipientCharacterId;
        this.title = title;
        this.body = body;
        this.items = MailLists.freeze(items);
        this.adena = adena;
        this.codPrice = codPrice;
        this.ownerVerified = ownerVerified;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    /** Items and adena are taken from this character; it need not be online. */
    public Long getSenderCharacterId() {
        return senderCharacterId;
    }

    /** Must differ from the sender; a recipient on the sender's account is allowed. */
    public Long getRecipientCharacterId() {
        return recipientCharacterId;
    }

    /** Non-blank; at most 128 chars and 255 UTF-8 bytes. */
    public String getTitle() {
        return title;
    }

    /** {@code null} is an empty body; at most 512 chars. */
    public @Nullable String getBody() {
        return body;
    }

    /** Non-null; {@code null} is normalized to empty (no item attachments). */
    public List<PlayerMailItem> getItems() {
        return items;
    }

    /** Positive when present; counts as one attachment line for the host limit and the fee. */
    public @Nullable Long getAdena() {
        return adena;
    }

    /** Positive when present; requires at least one {@link #getItems() items} line, adena alone does not satisfy it. */
    public @Nullable Long getCodPrice() {
        return codPrice;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    /** Stable across retries, unlike {@code correlationId} (one attempt); the host deduplicates and replays by it. */
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Past this the host refuses with {@code COMMAND_EXPIRED} before touching anything; guards against Kafka backlog. */
    public Instant getDeadline() {
        return deadline;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .senderCharacterId(senderCharacterId)
                .recipientCharacterId(recipientCharacterId)
                .title(title)
                .body(body)
                .items(items)
                .adena(adena)
                .codPrice(codPrice)
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
        if (!(o instanceof SendPlayerMailCommand)) return false;
        SendPlayerMailCommand that = (SendPlayerMailCommand) o;
        return ownerVerified == that.ownerVerified
                && Objects.equals(senderCharacterId, that.senderCharacterId)
                && Objects.equals(recipientCharacterId, that.recipientCharacterId)
                && Objects.equals(title, that.title)
                && Objects.equals(body, that.body)
                && Objects.equals(items, that.items)
                && Objects.equals(adena, that.adena)
                && Objects.equals(codPrice, that.codPrice)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                senderCharacterId,
                recipientCharacterId,
                title,
                body,
                items,
                adena,
                codPrice,
                ownerVerified,
                idempotencyKey,
                deadline,
                initiator);
    }

    @Override
    public String toString() {
        return "SendPlayerMailCommand[senderCharacterId=" + senderCharacterId
                + ", recipientCharacterId=" + recipientCharacterId
                + ", title=" + title
                + ", body=" + body
                + ", items=" + items
                + ", adena=" + adena
                + ", codPrice=" + codPrice
                + ", ownerVerified=" + ownerVerified
                + ", idempotencyKey=" + idempotencyKey
                + ", deadline=" + deadline + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long senderCharacterId;
        private Long recipientCharacterId;
        private String title;
        private @Nullable String body;
        private @Nullable List<PlayerMailItem> items;
        private @Nullable Long adena;
        private @Nullable Long codPrice;
        private boolean ownerVerified;
        private UUID idempotencyKey;
        private Instant deadline;
        private CommandInitiator initiator;

        public Builder senderCharacterId(Long senderCharacterId) {
            this.senderCharacterId = senderCharacterId;
            return this;
        }

        public Builder recipientCharacterId(Long recipientCharacterId) {
            this.recipientCharacterId = recipientCharacterId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder body(@Nullable String body) {
            this.body = body;
            return this;
        }

        public Builder items(@Nullable List<PlayerMailItem> items) {
            this.items = items;
            return this;
        }

        public Builder adena(@Nullable Long adena) {
            this.adena = adena;
            return this;
        }

        public Builder codPrice(@Nullable Long codPrice) {
            this.codPrice = codPrice;
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

        public SendPlayerMailCommand build() {
            return new SendPlayerMailCommand(
                    senderCharacterId,
                    recipientCharacterId,
                    title,
                    body,
                    items,
                    adena,
                    codPrice,
                    ownerVerified,
                    idempotencyKey,
                    deadline,
                    initiator);
        }
    }
}
