package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionCheckResult;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Dry run of {@link StartOfflineFishingCommand}: the host runs the start's checks in the start's order and stops before
 * the first side effect. An absent choice skips the checks that need it (the shot grade check needs the rod too).
 * Gson bypasses the constructor, so the handler re-validates.
 */
public final class CheckOfflineFishingCommand implements OwnerVerified, NxCommand<AppSessionCheckResult> {

    private final int characterId;
    private final @Nullable Long durationMs;
    private final @Nullable Integer rodItemId;
    private final @Nullable Integer baitItemId;
    private final @Nullable Integer fishingShotItemId;
    private final boolean ownerVerified;
    private final Instant deadline;
    private final CommandInitiator initiator;

    public CheckOfflineFishingCommand(
            int characterId,
            @Nullable Long durationMs,
            @Nullable Integer rodItemId,
            @Nullable Integer baitItemId,
            @Nullable Integer fishingShotItemId,
            boolean ownerVerified,
            Instant deadline,
            CommandInitiator initiator) {
        if (durationMs != null && durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive when present");
        }
        this.characterId = characterId;
        this.durationMs = durationMs;
        this.rodItemId = rodItemId;
        this.baitItemId = baitItemId;
        this.fishingShotItemId = fishingShotItemId;
        this.ownerVerified = ownerVerified;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public int getCharacterId() {
        return characterId;
    }

    /** {@code null} skips the duration check and leaves {@code effectiveDurationMs} {@code null}. */
    public @Nullable Long getDurationMs() {
        return durationMs;
    }

    public @Nullable Integer getRodItemId() {
        return rodItemId;
    }

    public @Nullable Integer getBaitItemId() {
        return baitItemId;
    }

    public @Nullable Integer getFishingShotItemId() {
        return fishingShotItemId;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    public Instant getDeadline() {
        return deadline;
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
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CheckOfflineFishingCommand)) return false;
        CheckOfflineFishingCommand that = (CheckOfflineFishingCommand) o;
        return characterId == that.characterId
                && ownerVerified == that.ownerVerified
                && Objects.equals(durationMs, that.durationMs)
                && Objects.equals(rodItemId, that.rodItemId)
                && Objects.equals(baitItemId, that.baitItemId)
                && Objects.equals(fishingShotItemId, that.fishingShotItemId)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                characterId, durationMs, rodItemId, baitItemId, fishingShotItemId, ownerVerified, deadline, initiator);
    }

    @Override
    public String toString() {
        return "CheckOfflineFishingCommand[characterId=" + characterId
                + ", durationMs=" + durationMs
                + ", rodItemId=" + rodItemId
                + ", baitItemId=" + baitItemId
                + ", fishingShotItemId=" + fishingShotItemId
                + ", ownerVerified=" + ownerVerified
                + ", deadline=" + deadline
                + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private int characterId;
        private @Nullable Long durationMs;
        private @Nullable Integer rodItemId;
        private @Nullable Integer baitItemId;
        private @Nullable Integer fishingShotItemId;
        private boolean ownerVerified;
        private @Nullable Instant deadline;
        private @Nullable CommandInitiator initiator;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder durationMs(@Nullable Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder rodItemId(@Nullable Integer rodItemId) {
            this.rodItemId = rodItemId;
            return this;
        }

        public Builder baitItemId(@Nullable Integer baitItemId) {
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

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public CheckOfflineFishingCommand build() {
            return new CheckOfflineFishingCommand(
                    characterId,
                    durationMs,
                    rodItemId,
                    baitItemId,
                    fishingShotItemId,
                    ownerVerified,
                    deadline,
                    initiator);
        }
    }
}
