package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ends the running offline-fishing session of {@code characterId}. Idempotent: with none running the reply is a success
 * with {@code stopped=false}. The other fields mean what they do on {@link StartOfflineFishingCommand}.
 */
public final class StopOfflineFishingCommand implements OwnerVerified, NxCommand<StopOfflineFishingResult> {

    private final int characterId;
    private final boolean ownerVerified;
    private final Instant deadline;
    private final UUID idempotencyKey;
    private final CommandInitiator initiator;

    public StopOfflineFishingCommand(
            int characterId, boolean ownerVerified, Instant deadline, UUID idempotencyKey, CommandInitiator initiator) {
        this.characterId = characterId;
        this.ownerVerified = ownerVerified;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public int getCharacterId() {
        return characterId;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
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
                .idempotencyKey(idempotencyKey)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StopOfflineFishingCommand)) return false;
        StopOfflineFishingCommand that = (StopOfflineFishingCommand) o;
        return characterId == that.characterId
                && ownerVerified == that.ownerVerified
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, ownerVerified, deadline, idempotencyKey, initiator);
    }

    @Override
    public String toString() {
        return "StopOfflineFishingCommand[characterId=" + characterId
                + ", ownerVerified=" + ownerVerified
                + ", deadline=" + deadline
                + ", idempotencyKey=" + idempotencyKey + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private int characterId;
        private boolean ownerVerified;
        private Instant deadline;
        private UUID idempotencyKey;
        private CommandInitiator initiator;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder ownerVerified(boolean ownerVerified) {
            this.ownerVerified = ownerVerified;
            return this;
        }

        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public StopOfflineFishingCommand build() {
            return new StopOfflineFishingCommand(characterId, ownerVerified, deadline, idempotencyKey, initiator);
        }
    }
}
