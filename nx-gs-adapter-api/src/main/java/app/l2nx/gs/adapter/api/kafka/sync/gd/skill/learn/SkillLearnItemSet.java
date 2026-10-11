package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * All-of set of items consumed to learn a {@link SkillLearn} entry; {@code items} is non-null with at least one
 * entry.
 */
public final class SkillLearnItemSet {

    private final List<SkillLearnItem> items;

    public SkillLearnItemSet(List<SkillLearnItem> items) {
        if (items != null && items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        this.items =
                Collections.unmodifiableList(new ArrayList<SkillLearnItem>(Objects.requireNonNull(items, "items")));
    }

    public List<SkillLearnItem> getItems() {
        return items;
    }

    public Builder toBuilder() {
        return new Builder().items(items);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillLearnItemSet)) return false;
        SkillLearnItemSet that = (SkillLearnItemSet) o;
        return Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items);
    }

    @Override
    public String toString() {
        return "SkillLearnItemSet[" + "items=" + items.size() + "]";
    }

    public static final class Builder {
        private List<SkillLearnItem> items;

        public Builder items(List<SkillLearnItem> items) {
            this.items = items;
            return this;
        }

        public SkillLearnItemSet build() {
            return new SkillLearnItemSet(items);
        }
    }
}
