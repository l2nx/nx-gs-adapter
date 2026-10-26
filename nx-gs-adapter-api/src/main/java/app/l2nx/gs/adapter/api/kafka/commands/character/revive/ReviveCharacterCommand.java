package app.l2nx.gs.adapter.api.kafka.commands.character.revive;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.character.revive.model.RestartPoint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ReviveCharacterCommand implements OwnerVerified, NxCommand<ReviveCharacterResult> {

    private final int characterId;
    private final UUID masterAccountId;
    private final String restartPoint;
    private final boolean disconnectClient;
    private final boolean ownerVerified;
    private final Instant deadline;
    private final UUID idempotencyKey;

    public ReviveCharacterCommand(
            int characterId,
            UUID masterAccountId,
            String restartPoint,
            boolean disconnectClient,
            boolean ownerVerified,
            Instant deadline,
            UUID idempotencyKey) {
        Objects.requireNonNull(restartPoint, "restartPoint");
        if (restartPoint.trim().isEmpty()) {
            throw new IllegalArgumentException("restartPoint must not be blank");
        }
        this.characterId = characterId;
        this.masterAccountId = Objects.requireNonNull(masterAccountId, "masterAccountId");
        this.restartPoint = restartPoint;
        this.disconnectClient = disconnectClient;
        this.ownerVerified = ownerVerified;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    public int getCharacterId() {
        return characterId;
    }

    public UUID getMasterAccountId() {
        return masterAccountId;
    }

    /**
     * A {@link RestartPoint} token, kept a string so a token from a later api release is refused with
     * {@code RESTART_POINT_UNSUPPORTED} instead of failing to deserialize.
     */
    public String getRestartPoint() {
        return restartPoint;
    }

    /**
     * In-world character only: {@code true} closes its client and revives it out of the game, {@code false} revives it
     * in the world.
     */
    public boolean isDisconnectClient() {
        return disconnectClient;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    public Instant getDeadline() {
        return deadline;
    }

    /** Stable across retries, unlike {@code correlationId}; a retry after success gets the recorded result, not {@code NOT_DEAD}. */
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .masterAccountId(masterAccountId)
                .restartPoint(restartPoint)
                .disconnectClient(disconnectClient)
                .ownerVerified(ownerVerified)
                .deadline(deadline)
                .idempotencyKey(idempotencyKey);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviveCharacterCommand)) return false;
        ReviveCharacterCommand that = (ReviveCharacterCommand) o;
        return characterId == that.characterId
                && disconnectClient == that.disconnectClient
                && ownerVerified == that.ownerVerified
                && Objects.equals(masterAccountId, that.masterAccountId)
                && Objects.equals(restartPoint, that.restartPoint)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                characterId, masterAccountId, restartPoint, disconnectClient, ownerVerified, deadline, idempotencyKey);
    }

    @Override
    public String toString() {
        return "ReviveCharacterCommand[characterId=" + characterId
                + ", masterAccountId=" + masterAccountId
                + ", restartPoint=" + restartPoint
                + ", disconnectClient=" + disconnectClient
                + ", ownerVerified=" + ownerVerified
                + ", deadline=" + deadline
                + ", idempotencyKey=" + idempotencyKey + "]";
    }

    public static final class Builder {
        private int characterId;
        private UUID masterAccountId;
        private String restartPoint;
        private boolean disconnectClient;
        private boolean ownerVerified;
        private Instant deadline;
        private UUID idempotencyKey;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder masterAccountId(UUID masterAccountId) {
            this.masterAccountId = masterAccountId;
            return this;
        }

        public Builder restartPoint(String restartPoint) {
            this.restartPoint = restartPoint;
            return this;
        }

        public Builder restartPoint(RestartPoint restartPoint) {
            this.restartPoint = restartPoint.name();
            return this;
        }

        public Builder disconnectClient(boolean disconnectClient) {
            this.disconnectClient = disconnectClient;
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

        public ReviveCharacterCommand build() {
            return new ReviveCharacterCommand(
                    characterId,
                    masterAccountId,
                    restartPoint,
                    disconnectClient,
                    ownerVerified,
                    deadline,
                    idempotencyKey);
        }
    }
}
