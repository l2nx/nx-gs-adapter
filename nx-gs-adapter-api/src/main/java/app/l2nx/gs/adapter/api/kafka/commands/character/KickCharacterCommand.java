package app.l2nx.gs.adapter.api.kafka.commands.character;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Disconnects an online character, either dropping it to the login screen or closing the client.
 *
 * <p>Reply: {@code CommandResult<KickCharacterResult>}. Errors: {@code NOT_FOUND} (no such character),
 * {@code INVALID_STATE} (not online), {@code VALIDATION_FAILED} ({@code characterId} missing / out of range),
 * {@code INTERNAL_ERROR}.</p>
 *
 * <p>Routed by {@code characterId}. Not idempotent (a re-delivery after the player relogged kicks again), so hosts
 * dedupe on the correlation id.</p>
 */
public final class KickCharacterCommand implements NxCommand<KickCharacterResult> {

    private final Long characterId;
    private final boolean closeClient;
    private final @Nullable String staffNotes;
    private final CommandInitiator initiator;

    public KickCharacterCommand(
            Long characterId, boolean closeClient, @Nullable String staffNotes, CommandInitiator initiator) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        this.characterId = characterId;
        this.closeClient = closeClient;
        this.staffNotes = staffNotes;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharacterId() {
        return characterId;
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
                .characterId(characterId)
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
                && Objects.equals(characterId, that.characterId)
                && Objects.equals(staffNotes, that.staffNotes)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, closeClient, staffNotes, initiator);
    }

    @Override
    public String toString() {
        return "KickCharacterCommand[characterId=" + characterId + ", closeClient=" + closeClient + ", staffNotes=" + staffNotes
                + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long characterId;
        private boolean closeClient;
        private @Nullable String staffNotes;
        private CommandInitiator initiator;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
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
            return new KickCharacterCommand(characterId, closeClient, staffNotes, initiator);
        }
    }
}
