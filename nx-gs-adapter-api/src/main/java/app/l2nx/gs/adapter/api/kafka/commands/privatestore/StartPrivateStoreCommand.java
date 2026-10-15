package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.time.Instant;
import java.util.List;
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
    private final List<SellLine> lines;
    private final @Nullable Integer sellerFeePercent;
    private final @Nullable Boolean offlineAllowed;
    private final boolean ownerVerified;
    private final @Nullable Instant deadline;
    private final @Nullable UUID idempotencyKey;
    private final @Nullable String initiatorIp;

    StartPrivateStoreCommand(
            int charId,
            @Nullable String title,
            List<SellLine> lines,
            @Nullable Integer sellerFeePercent,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline,
            UUID idempotencyKey,
            @Nullable String initiatorIp) {
        this.charId = charId;
        this.title = title;
        PrivateStoreFees.checkPercent(sellerFeePercent, "sellerFeePercent");
        this.lines = PrivateStoreLists.requireDistinctLines(lines);
        this.sellerFeePercent = sellerFeePercent;
        this.offlineAllowed = offlineAllowed;
        this.ownerVerified = ownerVerified;
        this.deadline = deadline;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.initiatorIp = initiatorIp == null || initiatorIp.trim().isEmpty() ? null : initiatorIp;
    }

    public int getCharId() {
        return charId;
    }

    /** {@code null} falls back to the host's default banner. */
    public @Nullable String getTitle() {
        return title;
    }

    public List<SellLine> getLines() {
        return lines;
    }

    /**
     * Whole percent withheld from the seller's proceeds and burned on every sale from this store, rounded up; {@code null} or
     * {@code 0} = no fee. Outside {@code 0..}{@link PrivateStoreFees#MAX_FEE_PERCENT} is {@code VALIDATION_FAILED}; the host
     * re-validates because Gson bypasses the constructor.
     */
    public @Nullable Integer getSellerFeePercent() {
        return sellerFeePercent;
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

    /** IP of the app request that triggered the start, put in by the platform; {@code null} when unknown. */
    public @Nullable String getInitiatorIp() {
        return initiatorIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StartPrivateStoreCommand that = (StartPrivateStoreCommand) o;
        return charId == that.charId
                && ownerVerified == that.ownerVerified
                && Objects.equals(title, that.title)
                && Objects.equals(lines, that.lines)
                && Objects.equals(sellerFeePercent, that.sellerFeePercent)
                && Objects.equals(offlineAllowed, that.offlineAllowed)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(initiatorIp, that.initiatorIp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getClass(),
                charId,
                title,
                lines,
                sellerFeePercent,
                offlineAllowed,
                ownerVerified,
                deadline,
                idempotencyKey,
                initiatorIp);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[charId=" + charId + ", title=" + title + ", lines=" + lines
                + ", sellerFeePercent=" + sellerFeePercent + ", offlineAllowed=" + offlineAllowed
                + ", ownerVerified=" + ownerVerified + ", deadline=" + deadline + ", idempotencyKey=" + idempotencyKey
                + ", initiatorIp=" + initiatorIp + "]";
    }
}
