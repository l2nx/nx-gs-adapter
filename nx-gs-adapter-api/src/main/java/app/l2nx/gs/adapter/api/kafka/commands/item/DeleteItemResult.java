package app.l2nx.gs.adapter.api.kafka.commands.item;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Objects;

/**
 * Echoes what was actually deleted; may be less than requested if the live stack shrank before execution.
 * {@code item} is the item before the operation.
 */
public final class DeleteItemResult {

    private final ItemSnapshot item;
    private final Long countDeleted;
    private final boolean fullyDeleted;

    public DeleteItemResult(ItemSnapshot item, Long countDeleted, boolean fullyDeleted) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
        if (countDeleted == null) {
            throw new IllegalArgumentException("countDeleted is required");
        }
        if (countDeleted < 0L) {
            throw new IllegalArgumentException("countDeleted must be non-negative (got " + countDeleted + ")");
        }
        this.item = item;
        this.countDeleted = countDeleted;
        this.fullyDeleted = fullyDeleted;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    /** May be less than the requested count: the handler clamps to the live stack. */
    public Long getCountDeleted() {
        return countDeleted;
    }

    /** {@code true} when the instance is gone, {@code false} when it was only decremented. */
    public boolean isFullyDeleted() {
        return fullyDeleted;
    }

    public Builder toBuilder() {
        return new Builder().item(item).countDeleted(countDeleted).fullyDeleted(fullyDeleted);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeleteItemResult)) return false;
        DeleteItemResult that = (DeleteItemResult) o;
        return fullyDeleted == that.fullyDeleted
                && Objects.equals(item, that.item)
                && Objects.equals(countDeleted, that.countDeleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, countDeleted, fullyDeleted);
    }

    @Override
    public String toString() {
        return "DeleteItemResult[item=" + item
                + ", countDeleted=" + countDeleted
                + ", fullyDeleted=" + fullyDeleted + "]";
    }

    public static final class Builder {
        private ItemSnapshot item;
        private Long countDeleted;
        private boolean fullyDeleted;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder countDeleted(Long countDeleted) {
            this.countDeleted = countDeleted;
            return this;
        }

        public Builder fullyDeleted(boolean fullyDeleted) {
            this.fullyDeleted = fullyDeleted;
            return this;
        }

        public DeleteItemResult build() {
            return new DeleteItemResult(item, countDeleted, fullyDeleted);
        }
    }
}
