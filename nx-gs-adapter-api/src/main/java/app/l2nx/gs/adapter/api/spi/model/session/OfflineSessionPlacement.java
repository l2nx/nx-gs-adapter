package app.l2nx.gs.adapter.api.spi.model.session;

import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Answer of {@code OfflineSessionHost.placeInWorld}: the spot the character now stands on, or why it was not placed. */
public final class OfflineSessionPlacement {

    private final boolean placed;
    private final int spotId;
    private final @Nullable String typeState;
    private final @Nullable AppSessionRefusalReason refusal;
    private final @Nullable String detail;
    private final boolean transientRefusal;

    private OfflineSessionPlacement(
            boolean placed,
            int spotId,
            @Nullable String typeState,
            @Nullable AppSessionRefusalReason refusal,
            @Nullable String detail,
            boolean transientRefusal) {
        this.placed = placed;
        this.spotId = spotId;
        this.typeState = typeState;
        this.refusal = refusal;
        this.detail = detail;
        this.transientRefusal = transientRefusal;
    }

    /** {@code spotId} is the session's own spot or a free one of its pool. */
    public static OfflineSessionPlacement placed(int spotId) {
        return placed(spotId, null);
    }

    /**
     * Like {@link #placed(int)}, with the type state the placement changed. Stored with the spot, before
     * {@code startAction}, which then sees it.
     *
     * @param typeState {@code null} or blank keeps the stored one
     */
    public static OfflineSessionPlacement placed(int spotId, @Nullable String typeState) {
        String value = typeState == null || typeState.trim().isEmpty() ? null : typeState;
        return new OfflineSessionPlacement(true, spotId, value, null, null, false);
    }

    /**
     * @param refusal what a start reports to the player
     * @param detail log text, never shown to a player
     */
    public static OfflineSessionPlacement refused(AppSessionRefusalReason refusal, @Nullable String detail) {
        return new OfflineSessionPlacement(false, 0, null, Objects.requireNonNull(refusal, "refusal"), detail, false);
    }

    /**
     * Like {@link #refused}, for a cause expected to pass within seconds (another operation holds the character). A
     * start reports it as is; a restore tries the session again later, a bounded number of times.
     */
    public static OfflineSessionPlacement refusedForNow(AppSessionRefusalReason refusal, @Nullable String detail) {
        return new OfflineSessionPlacement(false, 0, null, Objects.requireNonNull(refusal, "refusal"), detail, true);
    }

    public boolean isPlaced() {
        return placed;
    }

    /** Meaningful only when placed. */
    public int getSpotId() {
        return spotId;
    }

    /** Set only when placed, and {@code null} there when the stored type state stays. */
    public @Nullable String getTypeState() {
        return typeState;
    }

    public boolean isTransientRefusal() {
        return transientRefusal;
    }

    /** Set exactly when not placed. */
    public @Nullable AppSessionRefusalReason getRefusal() {
        return refusal;
    }

    public @Nullable String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfflineSessionPlacement)) return false;
        OfflineSessionPlacement that = (OfflineSessionPlacement) o;
        return placed == that.placed
                && spotId == that.spotId
                && transientRefusal == that.transientRefusal
                && Objects.equals(typeState, that.typeState)
                && refusal == that.refusal
                && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placed, spotId, typeState, refusal, detail, transientRefusal);
    }

    @Override
    public String toString() {
        return placed
                ? "OfflineSessionPlacement[placed spotId=" + spotId + (typeState != null ? ", typeState set" : "") + "]"
                : "OfflineSessionPlacement[refused " + refusal + (transientRefusal ? " for now" : "") + ", detail="
                        + detail + "]";
    }
}
