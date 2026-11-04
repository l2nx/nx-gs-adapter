package app.l2nx.gs.adapter.api.kafka.commands.item;

import app.l2nx.gs.adapter.api.domain.item.ItemLocation;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Objects;

/**
 * {@code item} is the stack after the grant: when an existing stack absorbed the grant, that stack.
 * {@code countCreated} is the actual delta (host MAY clamp on stack-size limits).
 */
public final class CreateItemResult {

    private final ItemSnapshot item;
    private final Long countCreated;
    private final ItemLocation location;

    public CreateItemResult(ItemSnapshot item, Long countCreated, ItemLocation location) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
        if (countCreated == null) {
            throw new IllegalArgumentException("countCreated is required");
        }
        if (countCreated <= 0L) {
            throw new IllegalArgumentException("countCreated must be positive (got " + countCreated + ")");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
        this.item = item;
        this.countCreated = countCreated;
        this.location = location;
    }

    public ItemSnapshot getItem() {
        return item;
    }

    public Long getCountCreated() {
        return countCreated;
    }

    public ItemLocation getLocation() {
        return location;
    }

    public Builder toBuilder() {
        return new Builder().item(item).countCreated(countCreated).location(location);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateItemResult)) return false;
        CreateItemResult that = (CreateItemResult) o;
        return Objects.equals(item, that.item)
                && Objects.equals(countCreated, that.countCreated)
                && location == that.location;
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, countCreated, location);
    }

    @Override
    public String toString() {
        return "CreateItemResult[item=" + item + ", countCreated=" + countCreated + ", location=" + location + "]";
    }

    public static final class Builder {
        private ItemSnapshot item;
        private Long countCreated;
        private ItemLocation location;

        public Builder item(ItemSnapshot item) {
            this.item = item;
            return this;
        }

        public Builder countCreated(Long countCreated) {
            this.countCreated = countCreated;
            return this;
        }

        public Builder location(ItemLocation location) {
            this.location = location;
            return this;
        }

        public CreateItemResult build() {
            return new CreateItemResult(item, countCreated, location);
        }
    }
}
