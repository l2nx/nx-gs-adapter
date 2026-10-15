package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ends the running offline-fishing session of {@code characterId}. Idempotent: with none running the reply is a success
 * with {@code stopped=false}. The other fields mean what they do on {@link StartOfflineFishingCommand}.
 */
public final class StopOfflineFishingCommand implements OwnerVerified, NxCommand<StopOfflineFishingResult> {

    private final int characterId;
    private final UUID masterAccountId;
    private final @Nullable String initiatorIp;
    private final boolean ownerVerified;
    private final Instant deadline;
    private final UUID idempotencyKey;

    public StopOfflineFishingCommand(
            int characterId,
            UUID masterAccountId,
            @Nullable String initiatorIp,
            boolean ownerVerified,
            Instant deadline,
            UUID idempotencyKey) {
        this.characterId = characterId;
        this.masterAccountId = Objects.requireNonNull(masterAccountId, "masterAccountId");
        this.initiatorIp = initiatorIp == null || initiatorIp.trim().isEmpty() ? null : initiatorIp;
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

    /** IP of the app request, put in by the platform; {@code null} when unknown. */
    public @Nullable String getInitiatorIp() {
        return initiatorIp;
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

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .masterAccountId(masterAccountId)
                .initiatorIp(initiatorIp)
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
        if (!(o instanceof StopOfflineFishingCommand)) return false;
        StopOfflineFishingCommand that = (StopOfflineFishingCommand) o;
        return characterId == that.characterId
                && ownerVerified == that.ownerVerified
                && Objects.equals(masterAccountId, that.masterAccountId)
                && Objects.equals(initiatorIp, that.initiatorIp)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, masterAccountId, initiatorIp, ownerVerified, deadline, idempotencyKey);
    }

    @Override
    public String toString() {
        return "StopOfflineFishingCommand[characterId=" + characterId
                + ", masterAccountId=" + masterAccountId
                + ", initiatorIp=" + initiatorIp
                + ", ownerVerified=" + ownerVerified
                + ", deadline=" + deadline
                + ", idempotencyKey=" + idempotencyKey + "]";
    }

    public static final class Builder {
        private int characterId;
        private UUID masterAccountId;
        private @Nullable String initiatorIp;
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

        public Builder initiatorIp(@Nullable String initiatorIp) {
            this.initiatorIp = initiatorIp;
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

        public StopOfflineFishingCommand build() {
            return new StopOfflineFishingCommand(
                    characterId, masterAccountId, initiatorIp, ownerVerified, deadline, idempotencyKey);
        }
    }
}
