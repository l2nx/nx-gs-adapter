package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class DroppedLine {

    /**
     * Name of the problem extension carrying the {@code List<DroppedLine>} of a
     * {@link PrivateStoreReason#LINES_REJECTED} refusal.
     */
    public static final String EXTENSION = "dropped";

    private final int lineIndex;
    private final @Nullable Integer itemId;
    private final String reason;

    public DroppedLine(int lineIndex, @Nullable Integer itemId, String reason) {
        if (lineIndex < 0) {
            throw new IllegalArgumentException("lineIndex must be non-negative (got " + lineIndex + ")");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("reason is required and must not be blank");
        }
        this.lineIndex = lineIndex;
        this.itemId = itemId;
        this.reason = reason;
    }

    /** 0-based index into the command's {@code lines}. */
    public int getLineIndex() {
        return lineIndex;
    }

    /** Inventory instance object-id of a sell line; {@code null} for a buy line. */
    public @Nullable Integer getItemId() {
        return itemId;
    }

    /** Open token, known values in {@link DroppedLineReason}; consumers must tolerate unknown ones. */
    public String getReason() {
        return reason;
    }

    public Builder toBuilder() {
        return new Builder().lineIndex(lineIndex).itemId(itemId).reason(reason);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DroppedLine)) return false;
        DroppedLine that = (DroppedLine) o;
        return lineIndex == that.lineIndex
                && Objects.equals(itemId, that.itemId)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineIndex, itemId, reason);
    }

    @Override
    public String toString() {
        return "DroppedLine[lineIndex=" + lineIndex + ", itemId=" + itemId + ", reason=" + reason + "]";
    }

    public static final class Builder {
        private @Nullable Integer lineIndex;
        private @Nullable Integer itemId;
        private @Nullable String reason;

        public Builder lineIndex(int lineIndex) {
            this.lineIndex = lineIndex;
            return this;
        }

        public Builder itemId(@Nullable Integer itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public DroppedLine build() {
            if (lineIndex == null) {
                throw new IllegalArgumentException("lineIndex is required");
            }
            return new DroppedLine(lineIndex, itemId, reason);
        }
    }
}
