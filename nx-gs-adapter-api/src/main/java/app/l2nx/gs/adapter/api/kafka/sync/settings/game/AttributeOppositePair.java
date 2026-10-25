package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import app.l2nx.gs.adapter.api.domain.Attribute;
import java.util.Objects;

public final class AttributeOppositePair {

    private final Attribute attribute;
    private final Attribute opposite;

    public AttributeOppositePair(Attribute attribute, Attribute opposite) {
        this.attribute = Objects.requireNonNull(attribute, "AttributeOppositePair.attribute is required");
        this.opposite = Objects.requireNonNull(opposite, "AttributeOppositePair.opposite is required");
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public Attribute getOpposite() {
        return opposite;
    }

    public Builder toBuilder() {
        return new Builder().attribute(attribute).opposite(opposite);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeOppositePair)) return false;
        AttributeOppositePair that = (AttributeOppositePair) o;
        return attribute == that.attribute && opposite == that.opposite;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, opposite);
    }

    @Override
    public String toString() {
        return "AttributeOppositePair[attribute=" + attribute + ", opposite=" + opposite + "]";
    }

    public static final class Builder {
        private Attribute attribute;
        private Attribute opposite;

        public Builder attribute(Attribute attribute) {
            this.attribute = attribute;
            return this;
        }

        public Builder opposite(Attribute opposite) {
            this.opposite = opposite;
            return this;
        }

        public AttributeOppositePair build() {
            return new AttributeOppositePair(attribute, opposite);
        }
    }
}
