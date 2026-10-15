package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import app.l2nx.gs.adapter.api.kafka.commands.CommandProblem;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionRefusal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Sends a character fishing without a client. The host decides every rule; a refusal carries a reason under
 * {@link AppSessionRefusal#REASON} in {@link CommandProblem#getExtensions() CommandProblem.extensions}. Idempotent per
 * {@code idempotencyKey}. Gson bypasses the constructor, so the handler re-validates.
 */
public final class StartOfflineFishingCommand implements OwnerVerified, NxCommand<StartOfflineFishingResult> {

    private final int characterId;
    private final UUID masterAccountId;
    private final @Nullable String initiatorIp;
    private final long durationMs;
    private final int rodItemId;
    private final int baitItemId;
    private final boolean ownerVerified;
    private final Instant deadline;
    private final UUID idempotencyKey;

    public StartOfflineFishingCommand(
            int characterId,
            UUID masterAccountId,
            @Nullable String initiatorIp,
            long durationMs,
            int rodItemId,
            int baitItemId,
            boolean ownerVerified,
            Instant deadline,
            UUID idempotencyKey) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive");
        }
        this.characterId = characterId;
        this.masterAccountId = Objects.requireNonNull(masterAccountId, "masterAccountId");
        this.initiatorIp = initiatorIp == null || initiatorIp.trim().isEmpty() ? null : initiatorIp;
        this.durationMs = durationMs;
        this.rodItemId = rodItemId;
        this.baitItemId = baitItemId;
        this.ownerVerified = ownerVerified;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    public int getCharacterId() {
        return characterId;
    }

    /** Master account of the initiator, put in by the platform. */
    public UUID getMasterAccountId() {
        return masterAccountId;
    }

    /** IP of the app request, put in by the platform; {@code null} when unknown. */
    public @Nullable String getInitiatorIp() {
        return initiatorIp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /** Inventory instance id (not a template id) of the chosen rod. */
    public int getRodItemId() {
        return rodItemId;
    }

    /** Inventory instance id of the bait stack; the session never switches bait. */
    public int getBaitItemId() {
        return baitItemId;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    /** Past this the host refuses with {@code COMMAND_EXPIRED} before touching anything; guards against Kafka backlog. */
    public Instant getDeadline() {
        return deadline;
    }

    /** Business identity of the start, stable across retries, unlike {@code correlationId} (one attempt). */
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .masterAccountId(masterAccountId)
                .initiatorIp(initiatorIp)
                .durationMs(durationMs)
                .rodItemId(rodItemId)
                .baitItemId(baitItemId)
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
        if (!(o instanceof StartOfflineFishingCommand)) return false;
        StartOfflineFishingCommand that = (StartOfflineFishingCommand) o;
        return characterId == that.characterId
                && durationMs == that.durationMs
                && rodItemId == that.rodItemId
                && baitItemId == that.baitItemId
                && ownerVerified == that.ownerVerified
                && Objects.equals(masterAccountId, that.masterAccountId)
                && Objects.equals(initiatorIp, that.initiatorIp)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                characterId,
                masterAccountId,
                initiatorIp,
                durationMs,
                rodItemId,
                baitItemId,
                ownerVerified,
                deadline,
                idempotencyKey);
    }

    @Override
    public String toString() {
        return "StartOfflineFishingCommand[characterId=" + characterId
                + ", masterAccountId=" + masterAccountId
                + ", initiatorIp=" + initiatorIp
                + ", durationMs=" + durationMs
                + ", rodItemId=" + rodItemId
                + ", baitItemId=" + baitItemId
                + ", ownerVerified=" + ownerVerified
                + ", deadline=" + deadline
                + ", idempotencyKey=" + idempotencyKey + "]";
    }

    public static final class Builder {
        private int characterId;
        private UUID masterAccountId;
        private @Nullable String initiatorIp;
        private long durationMs;
        private int rodItemId;
        private int baitItemId;
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

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder rodItemId(int rodItemId) {
            this.rodItemId = rodItemId;
            return this;
        }

        public Builder baitItemId(int baitItemId) {
            this.baitItemId = baitItemId;
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

        public StartOfflineFishingCommand build() {
            return new StartOfflineFishingCommand(
                    characterId,
                    masterAccountId,
                    initiatorIp,
                    durationMs,
                    rodItemId,
                    baitItemId,
                    ownerVerified,
                    deadline,
                    idempotencyKey);
        }
    }
}
