package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
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
 * {@link AppSessionRefusal#REASON} in {@link CommandProblem#getExtensions() CommandProblem.extensions}. Gson bypasses
 * the constructor, so the handler re-validates.
 */
public final class StartOfflineFishingCommand implements OwnerVerified, NxCommand<StartOfflineFishingResult> {

    private final int characterId;
    private final long durationMs;
    private final int rodItemId;
    private final int baitItemId;
    private final @Nullable Integer fishingShotItemId;
    private final boolean ownerVerified;
    private final Instant deadline;
    private final UUID idempotencyKey;
    private final CommandInitiator initiator;

    public StartOfflineFishingCommand(
            int characterId,
            long durationMs,
            int rodItemId,
            int baitItemId,
            @Nullable Integer fishingShotItemId,
            boolean ownerVerified,
            Instant deadline,
            UUID idempotencyKey,
            CommandInitiator initiator) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive");
        }
        this.characterId = characterId;
        this.durationMs = durationMs;
        this.rodItemId = rodItemId;
        this.baitItemId = baitItemId;
        this.fishingShotItemId = fishingShotItemId;
        this.ownerVerified = ownerVerified;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public int getCharacterId() {
        return characterId;
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

    /** Inventory instance id of the fishing-shot stack, one shot per cast; {@code null} fishes without shots. */
    public @Nullable Integer getFishingShotItemId() {
        return fishingShotItemId;
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

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .durationMs(durationMs)
                .rodItemId(rodItemId)
                .baitItemId(baitItemId)
                .fishingShotItemId(fishingShotItemId)
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
        if (!(o instanceof StartOfflineFishingCommand)) return false;
        StartOfflineFishingCommand that = (StartOfflineFishingCommand) o;
        return characterId == that.characterId
                && durationMs == that.durationMs
                && rodItemId == that.rodItemId
                && baitItemId == that.baitItemId
                && Objects.equals(fishingShotItemId, that.fishingShotItemId)
                && ownerVerified == that.ownerVerified
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                characterId,
                durationMs,
                rodItemId,
                baitItemId,
                fishingShotItemId,
                ownerVerified,
                deadline,
                idempotencyKey,
                initiator);
    }

    @Override
    public String toString() {
        return "StartOfflineFishingCommand[characterId=" + characterId
                + ", durationMs=" + durationMs
                + ", rodItemId=" + rodItemId
                + ", baitItemId=" + baitItemId
                + ", fishingShotItemId=" + fishingShotItemId
                + ", ownerVerified=" + ownerVerified
                + ", deadline=" + deadline
                + ", idempotencyKey=" + idempotencyKey + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private int characterId;
        private long durationMs;
        private int rodItemId;
        private int baitItemId;
        private @Nullable Integer fishingShotItemId;
        private boolean ownerVerified;
        private Instant deadline;
        private UUID idempotencyKey;
        private CommandInitiator initiator;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
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

        public Builder fishingShotItemId(@Nullable Integer fishingShotItemId) {
            this.fishingShotItemId = fishingShotItemId;
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

        public StartOfflineFishingCommand build() {
            return new StartOfflineFishingCommand(
                    characterId,
                    durationMs,
                    rodItemId,
                    baitItemId,
                    fishingShotItemId,
                    ownerVerified,
                    deadline,
                    idempotencyKey,
                    initiator);
        }
    }
}
