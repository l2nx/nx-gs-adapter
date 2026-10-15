package app.l2nx.gs.adapter.api.spi.model;

import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Answer of {@code OfflineSessionHost.placeInWorld}: the spot the character now stands on, or why it was not placed. */
public final class OfflineSessionPlacement {

    private final boolean placed;
    private final int spotId;
    private final @Nullable AppSessionRefusalReason refusal;
    private final @Nullable String detail;

    private OfflineSessionPlacement(
            boolean placed, int spotId, @Nullable AppSessionRefusalReason refusal, @Nullable String detail) {
        this.placed = placed;
        this.spotId = spotId;
        this.refusal = refusal;
        this.detail = detail;
    }

    /** The character stands on {@code spotId} of the session's pool, the session's own spot or a free one. */
    public static OfflineSessionPlacement placed(int spotId) {
        return new OfflineSessionPlacement(true, spotId, null, null);
    }

    /**
     * The character was not placed and is not in the world.
     *
     * @param refusal what a start reports to the player
     * @param detail log text, never shown to a player
     */
    public static OfflineSessionPlacement refused(AppSessionRefusalReason refusal, @Nullable String detail) {
        return new OfflineSessionPlacement(false, 0, Objects.requireNonNull(refusal, "refusal"), detail);
    }

    public boolean isPlaced() {
        return placed;
    }

    /** Meaningful only when placed. */
    public int getSpotId() {
        return spotId;
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
                && refusal == that.refusal
                && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placed, spotId, refusal, detail);
    }

    @Override
    public String toString() {
        return placed
                ? "OfflineSessionPlacement[placed spotId=" + spotId + "]"
                : "OfflineSessionPlacement[refused " + refusal + ", detail=" + detail + "]";
    }
}
