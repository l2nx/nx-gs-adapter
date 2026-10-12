package app.l2nx.gs.adapter.api.kafka.commands.mail;

import java.util.Objects;

/**
 * One attachment line of a character-sent mail: the sender's inventory instance id (NOT a catalog template id) and how
 * many of the stack to move. Adena is never listed here, only via {@link SendPlayerMailCommand#getAdena()}. Gson
 * bypasses the constructor, so the handler must emit {@code VALIDATION_FAILED} on missing or non-positive values.
 */
public final class PlayerMailItem {

    private final Long itemId;
    private final Long count;

    public PlayerMailItem(Long itemId, Long count) {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (count == null) {
            throw new IllegalArgumentException("count is required");
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive (got " + count + ")");
        }
        this.itemId = itemId;
        this.count = count;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getCount() {
        return count;
    }

    public Builder toBuilder() {
        return new Builder().itemId(itemId).count(count);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerMailItem)) return false;
        PlayerMailItem that = (PlayerMailItem) o;
        return Objects.equals(itemId, that.itemId) && Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, count);
    }

    @Override
    public String toString() {
        return "PlayerMailItem[itemId=" + itemId + ", count=" + count + "]";
    }

    public static final class Builder {
        private Long itemId;
        private Long count;

        public Builder itemId(Long itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder count(Long count) {
            this.count = count;
            return this;
        }

        public PlayerMailItem build() {
            return new PlayerMailItem(itemId, count);
        }
    }
}
