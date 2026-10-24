package app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption;

import app.l2nx.gs.adapter.api.kafka.sync.gd.StatModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Augmentation and enchanting share this one id space. Both lists are non-null and may be empty: an option without
 * any bonus is still a catalog entry.
 */
public final class ItemOption {

    private final int id;
    private final List<StatModifier> statModifiers;
    private final List<ItemOptionSkill> skills;

    public ItemOption(int id, List<StatModifier> statModifiers, List<ItemOptionSkill> skills) {
        this.id = id;
        this.statModifiers = Collections.unmodifiableList(
                new ArrayList<StatModifier>(Objects.requireNonNull(statModifiers, "statModifiers")));
        this.skills =
                Collections.unmodifiableList(new ArrayList<ItemOptionSkill>(Objects.requireNonNull(skills, "skills")));
    }

    public int getId() {
        return id;
    }

    public List<StatModifier> getStatModifiers() {
        return statModifiers;
    }

    public List<ItemOptionSkill> getSkills() {
        return skills;
    }

    public Builder toBuilder() {
        return new Builder().id(id).statModifiers(statModifiers).skills(skills);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemOption)) return false;
        ItemOption that = (ItemOption) o;
        return id == that.id && statModifiers.equals(that.statModifiers) && skills.equals(that.skills);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, statModifiers, skills);
    }

    @Override
    public String toString() {
        return "ItemOption[id=" + id + ", statModifiers=" + statModifiers.size() + ", skills=" + skills.size() + "]";
    }

    public static final class Builder {
        private int id;
        private List<StatModifier> statModifiers = Collections.emptyList();
        private List<ItemOptionSkill> skills = Collections.emptyList();

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder statModifiers(List<StatModifier> statModifiers) {
            this.statModifiers = statModifiers;
            return this;
        }

        public Builder skills(List<ItemOptionSkill> skills) {
            this.skills = skills;
            return this;
        }

        public ItemOption build() {
            return new ItemOption(id, statModifiers, skills);
        }
    }
}
