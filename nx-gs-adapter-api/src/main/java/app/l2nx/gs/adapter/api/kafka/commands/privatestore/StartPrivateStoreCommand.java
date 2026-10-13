package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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

    StartPrivateStoreCommand(
            int charId,
            @Nullable String title,
            List<SellLine> lines,
            @Nullable Integer sellerFeePercent,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline) {
        this.charId = charId;
        this.title = title;
        PrivateStoreFees.checkPercent(sellerFeePercent, "sellerFeePercent");
        this.lines = PrivateStoreLists.requireDistinctLines(lines);
        this.sellerFeePercent = sellerFeePercent;
        this.offlineAllowed = offlineAllowed;
        this.ownerVerified = ownerVerified;
        this.deadline = deadline;
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
                && Objects.equals(deadline, that.deadline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getClass(), charId, title, lines, sellerFeePercent, offlineAllowed, ownerVerified, deadline);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[charId=" + charId + ", title=" + title + ", lines=" + lines
                + ", sellerFeePercent=" + sellerFeePercent + ", offlineAllowed=" + offlineAllowed
                + ", ownerVerified=" + ownerVerified + ", deadline=" + deadline + "]";
    }
}
