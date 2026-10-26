package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.mail.model.MailItem;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Delivers a system mail to one character, routed by {@code charId}; the host MAY split it into several mails over its
 * attachment cap, all or none. {@code VALIDATION_FAILED} covers missing {@code charId}/{@code title} and a malformed
 * {@link MailItem}: Gson bypasses the constructor, so the handler must check.
 *
 * <p>Idempotent per {@code idempotencyKey} ({@code correlationId} when {@code null}): a retry replays the stored result,
 * or is refused with {@code INTERNAL_ERROR} while the first attempt is in flight. The caller MUST reuse the key when
 * retrying the same send; a fresh key mails and grants again.</p>
 */
public final class SendMailCommand implements NxCommand<SendMailResult> {

    private final Long charId;
    private final @Nullable String author;
    private final String title;
    private final @Nullable String body;
    private final List<MailItem> items;
    private final @Nullable Long adena;
    private final @Nullable UUID idempotencyKey;
    private final CommandInitiator initiator;

    public SendMailCommand(
            Long charId,
            @Nullable String author,
            String title,
            @Nullable String body,
            @Nullable List<MailItem> items,
            CommandInitiator initiator) {
        this(charId, author, title, body, items, null, null, initiator);
    }

    public SendMailCommand(
            Long charId,
            @Nullable String author,
            String title,
            @Nullable String body,
            @Nullable List<MailItem> items,
            @Nullable Long adena,
            @Nullable UUID idempotencyKey,
            CommandInitiator initiator) {
        if (charId == null) {
            throw new IllegalArgumentException("charId is required");
        }
        if (title == null) {
            throw new IllegalArgumentException("title is required");
        }
        if (adena != null && adena <= 0) {
            throw new IllegalArgumentException("adena must be positive when present");
        }
        this.charId = charId;
        this.author = author;
        this.title = title;
        this.body = body;
        this.items = MailLists.freeze(items);
        this.adena = adena;
        this.idempotencyKey = idempotencyKey;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharId() {
        return charId;
    }

    /** {@code null} falls back to the host's default sender name. Display only. */
    public @Nullable String getAuthor() {
        return author;
    }

    /** The host's {@code MailManager} rejects blank titles. */
    public String getTitle() {
        return title;
    }

    public @Nullable String getBody() {
        return body;
    }

    /** Non-null; {@code null} is normalized to empty (text-only mail). Gson-bypassed entries need re-validation. */
    public List<MailItem> getItems() {
        return items;
    }

    /** Positive when present; the host attaches it as adena. Additive: an old host ignores it. */
    public @Nullable Long getAdena() {
        return adena;
    }

    /**
     * Stable across retries, unlike {@code correlationId} (one attempt). {@code null}: older platform, the host falls
     * back to the correlation id.
     */
    public @Nullable UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .charId(charId)
                .author(author)
                .title(title)
                .body(body)
                .items(items)
                .adena(adena)
                .idempotencyKey(idempotencyKey)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendMailCommand)) return false;
        SendMailCommand that = (SendMailCommand) o;
        return Objects.equals(charId, that.charId)
                && Objects.equals(author, that.author)
                && Objects.equals(title, that.title)
                && Objects.equals(body, that.body)
                && Objects.equals(items, that.items)
                && Objects.equals(adena, that.adena)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(charId, author, title, body, items, adena, idempotencyKey, initiator);
    }

    @Override
    public String toString() {
        return "SendMailCommand[charId=" + charId
                + ", author=" + author
                + ", title=" + title
                + ", body=" + body
                + ", items=" + items
                + ", adena=" + adena
                + ", idempotencyKey=" + idempotencyKey + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long charId;
        private @Nullable String author;
        private String title;
        private @Nullable String body;
        private @Nullable List<MailItem> items;
        private @Nullable Long adena;
        private @Nullable UUID idempotencyKey;
        private CommandInitiator initiator;

        public Builder charId(Long charId) {
            this.charId = charId;
            return this;
        }

        public Builder author(@Nullable String author) {
            this.author = author;
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

        public Builder items(@Nullable List<MailItem> items) {
            this.items = items;
            return this;
        }

        public Builder adena(@Nullable Long adena) {
            this.adena = adena;
            return this;
        }

        public Builder idempotencyKey(@Nullable UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public SendMailCommand build() {
            return new SendMailCommand(charId, author, title, body, items, adena, idempotencyKey, initiator);
        }
    }
}
