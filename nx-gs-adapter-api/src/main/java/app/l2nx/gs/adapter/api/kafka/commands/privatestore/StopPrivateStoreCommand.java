package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Closes whatever private store the character has open. Errors: {@code NOT_FOUND} (char not online),
 * {@code INVALID_STATE} (no store open), {@code COMMAND_EXPIRED}.
 */
public final class StopPrivateStoreCommand implements OwnerVerified, NxCommand<StopPrivateStoreResult> {

    private final int characterId;
    private final boolean ownerVerified;
    private final @Nullable Instant deadline;
    private final CommandInitiator initiator;

    public StopPrivateStoreCommand(
            int characterId, boolean ownerVerified, @Nullable Instant deadline, CommandInitiator initiator) {
        this.characterId = characterId;
        this.ownerVerified = ownerVerified;
        this.deadline = deadline;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public int getCharacterId() {
        return characterId;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    /** {@code null} (older platform) reads as "no expiry". */
    public @Nullable Instant getDeadline() {
        return deadline;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .ownerVerified(ownerVerified)
                .deadline(deadline)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StopPrivateStoreCommand)) return false;
        StopPrivateStoreCommand that = (StopPrivateStoreCommand) o;
        return characterId == that.characterId
                && ownerVerified == that.ownerVerified
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, ownerVerified, deadline, initiator);
    }

    @Override
    public String toString() {
        return "StopPrivateStoreCommand[characterId=" + characterId + ", ownerVerified=" + ownerVerified + ", deadline="
                + deadline + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private int characterId;
        private boolean ownerVerified;
        private @Nullable Instant deadline;
        private CommandInitiator initiator;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder ownerVerified(boolean ownerVerified) {
            this.ownerVerified = ownerVerified;
            return this;
        }

        public Builder deadline(@Nullable Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public StopPrivateStoreCommand build() {
            return new StopPrivateStoreCommand(characterId, ownerVerified, deadline, initiator);
        }
    }
}
