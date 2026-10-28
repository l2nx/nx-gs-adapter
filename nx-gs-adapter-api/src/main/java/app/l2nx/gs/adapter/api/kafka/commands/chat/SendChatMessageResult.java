package app.l2nx.gs.adapter.api.kafka.commands.chat;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The platform stores the message from its echo event, not from this reply; {@link #getTargetInGame()} is the one
 * fact the reply adds.
 */
public final class SendChatMessageResult {

    private final int linesSent;
    private final int recipients;
    private final @Nullable Boolean targetInGame;

    public SendChatMessageResult(int linesSent, int recipients, @Nullable Boolean targetInGame) {
        this.linesSent = linesSent;
        this.recipients = recipients;
        this.targetInGame = targetInGame;
    }

    /** Non-empty lines after the host splits the text on {@code \n}. */
    public int getLinesSent() {
        return linesSent;
    }

    /**
     * Best-effort telemetry; hosts that do not track it MAY report {@code 0}, which does NOT mean failure. Never read
     * delivery from it: use {@link #getTargetInGame()}.
     */
    public int getRecipients() {
        return recipients;
    }

    /**
     * {@code WHISPER} only: whether the addressee is in game ({@code true} even for a shadowed whisper, so it does not
     * mean the packet reached the client). {@code null} on other channels and from a host older than the field.
     */
    public @Nullable Boolean getTargetInGame() {
        return targetInGame;
    }

    public Builder toBuilder() {
        return new Builder().linesSent(linesSent).recipients(recipients).targetInGame(targetInGame);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendChatMessageResult)) return false;
        SendChatMessageResult that = (SendChatMessageResult) o;
        return linesSent == that.linesSent
                && recipients == that.recipients
                && Objects.equals(targetInGame, that.targetInGame);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linesSent, recipients, targetInGame);
    }

    @Override
    public String toString() {
        return "SendChatMessageResult[linesSent=" + linesSent + ", recipients=" + recipients + ", targetInGame="
                + targetInGame + "]";
    }

    public static final class Builder {
        private int linesSent;
        private int recipients;
        private @Nullable Boolean targetInGame;

        public Builder linesSent(int linesSent) {
            this.linesSent = linesSent;
            return this;
        }

        public Builder recipients(int recipients) {
            this.recipients = recipients;
            return this;
        }

        public Builder targetInGame(@Nullable Boolean targetInGame) {
            this.targetInGame = targetInGame;
            return this;
        }

        public SendChatMessageResult build() {
            return new SendChatMessageResult(linesSent, recipients, targetInGame);
        }
    }
}
