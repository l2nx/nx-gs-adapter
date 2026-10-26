package app.l2nx.gs.adapter.api.kafka.commands.character;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Disconnects an online character, either dropping it to the login screen or closing the client.
 *
 * <p>Reply: {@code CommandResult<KickCharacterResult>}. Errors: {@code NOT_FOUND} (no such character),
 * {@code INVALID_STATE} (not online), {@code VALIDATION_FAILED} ({@code charId} missing / out of range),
 * {@code INTERNAL_ERROR}.</p>
 *
 * <p>Routed by {@code charId}. Not idempotent (a re-delivery after the player relogged kicks again), so hosts
 * dedupe on the correlation id.</p>
 */
public final class KickCharacterCommand implements NxCommand<KickCharacterResult> {

    private final Long charId;
    private final boolean closeClient;
    private final @Nullable String staffNotes;
    private final CommandInitiator initiator;

    public KickCharacterCommand(
            Long charId, boolean closeClient, @Nullable String staffNotes, CommandInitiator initiator) {
        if (charId == null) {
            throw new IllegalArgumentException("charId is required");
        }
        this.charId = charId;
        this.closeClient = closeClient;
        this.staffNotes = staffNotes;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharId() {
        return charId;
    }

    /** {@code true} closes the game client; {@code false} drops the player to the login screen. */
    public boolean isCloseClient() {
        return closeClient;
    }

    /** Staff-only note: never shown in-game, logged by the host and surfaced on the platform command audit. */
    public @Nullable String getStaffNotes() {
        return staffNotes;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .charId(charId)
                .closeClient(closeClient)
                .staffNotes(staffNotes)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KickCharacterCommand)) return false;
        KickCharacterCommand that = (KickCharacterCommand) o;
        return closeClient == that.closeClient
                && Objects.equals(charId, that.charId)
                && Objects.equals(staffNotes, that.staffNotes)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(charId, closeClient, staffNotes, initiator);
    }

    @Override
    public String toString() {
        return "KickCharacterCommand[charId=" + charId + ", closeClient=" + closeClient + ", staffNotes=" + staffNotes
                + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long charId;
        private boolean closeClient;
        private @Nullable String staffNotes;
        private CommandInitiator initiator;

        public Builder charId(Long charId) {
            this.charId = charId;
            return this;
        }

        public Builder closeClient(boolean closeClient) {
            this.closeClient = closeClient;
            return this;
        }

        public Builder staffNotes(@Nullable String staffNotes) {
            this.staffNotes = staffNotes;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public KickCharacterCommand build() {
            return new KickCharacterCommand(charId, closeClient, staffNotes, initiator);
        }
    }
}
