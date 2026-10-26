package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The concrete subclass name is the wire type. A line that cannot be listed fails the whole command with
 * {@code VALIDATION_FAILED} / {@code LINES_REJECTED}, per-line reasons in the problem extension.
 */
public abstract class StartPrivateStoreCommand implements OwnerVerified, NxCommand<StartPrivateStoreResult> {

    public static final int MAX_TITLE_LENGTH = 29;

    private final int charId;
    private final @Nullable String title;
    private final @Nullable Boolean offlineAllowed;
    private final boolean ownerVerified;
    private final @Nullable Instant deadline;
    private final @Nullable UUID idempotencyKey;
    private final CommandInitiator initiator;

    StartPrivateStoreCommand(
            int charId,
            @Nullable String title,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline,
            UUID idempotencyKey,
            CommandInitiator initiator) {
        this.charId = charId;
        this.title = title == null || title.trim().isEmpty() ? null : title.trim();
        this.offlineAllowed = offlineAllowed;
        this.ownerVerified = ownerVerified;
        this.deadline = deadline;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public int getCharId() {
        return charId;
    }

    /** {@code null} falls back to the host's default banner. */
    public @Nullable String getTitle() {
        return title;
    }

    /**
     * {@code false} refuses the offline branch with {@code OFFLINE_QUOTA_EXCEEDED}; the online branch ignores it.
     * {@code null} (older platform) reads as allowed.
     */
    public @Nullable Boolean getOfflineAllowed() {
        return offlineAllowed;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    /** {@code null} (older platform) reads as "no expiry". */
    public @Nullable Instant getDeadline() {
        return deadline;
    }

    /**
     * Business identity of the start, stable across retries unlike {@code correlationId}. Gson bypasses the
     * constructor, so a command from a platform that predates the field reads {@code null}.
     */
    public @Nullable UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    abstract String storeFieldsToString();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StartPrivateStoreCommand that = (StartPrivateStoreCommand) o;
        return charId == that.charId
                && ownerVerified == that.ownerVerified
                && Objects.equals(title, that.title)
                && Objects.equals(offlineAllowed, that.offlineAllowed)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getClass(), charId, title, offlineAllowed, ownerVerified, deadline, idempotencyKey, initiator);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[charId=" + charId + ", title=" + title + ", " + storeFieldsToString()
                + ", offlineAllowed=" + offlineAllowed + ", ownerVerified=" + ownerVerified + ", deadline=" + deadline
                + ", idempotencyKey=" + idempotencyKey + ", initiator=" + initiator + "]";
    }
}
