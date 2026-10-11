package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect;

import app.l2nx.gs.adapter.api.domain.stat.Stat;
import app.l2nx.gs.adapter.api.domain.stat.StatOperation;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition.SkillConditionNode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One stat modification of a skill level or an effect. {@code statRaw} and {@code operationRaw} are the
 * engine's own tokens, always present and never inverted.
 *
 * <p>{@code stat} / {@code operation} are canonical after the exact {@code _RES} inversion described in {@link Stat};
 * when none exists {@code stat} is {@code null}.</p>
 */
public final class SkillStatModifier {

    private final @Nullable Stat stat;
    private final String statRaw;
    private final @Nullable StatOperation operation;
    private final String operationRaw;
    private final @Nullable Double value;
    private final @Nullable String valueRaw;
    private final @Nullable Integer order;
    private final @Nullable SkillConditionNode conditionTree;

    public SkillStatModifier(
            @Nullable Stat stat,
            String statRaw,
            @Nullable StatOperation operation,
            String operationRaw,
            @Nullable Double value,
            @Nullable String valueRaw,
            @Nullable Integer order,
            @Nullable SkillConditionNode conditionTree) {
        this.stat = stat;
        this.statRaw = Objects.requireNonNull(statRaw, "statRaw");
        this.operation = operation;
        this.operationRaw = Objects.requireNonNull(operationRaw, "operationRaw");
        this.value = value;
        this.valueRaw = valueRaw;
        this.order = order;
        this.conditionTree = conditionTree;
    }

    public @Nullable Stat getStat() {
        return stat;
    }

    public String getStatRaw() {
        return statRaw;
    }

    public @Nullable StatOperation getOperation() {
        return operation;
    }

    public String getOperationRaw() {
        return operationRaw;
    }

    /** Constant operand; {@code null} when the engine computes it at runtime. */
    public @Nullable Double getValue() {
        return value;
    }

    /** Engine expression when the operand is not a constant. */
    public @Nullable String getValueRaw() {
        return valueRaw;
    }

    /** Ascending application order among modifiers of the same stat. */
    public @Nullable Integer getOrder() {
        return order;
    }

    /** The modifier applies only while the tree holds; {@code null} = always. */
    public @Nullable SkillConditionNode getConditionTree() {
        return conditionTree;
    }

    public Builder toBuilder() {
        return new Builder()
                .stat(stat)
                .statRaw(statRaw)
                .operation(operation)
                .operationRaw(operationRaw)
                .value(value)
                .valueRaw(valueRaw)
                .order(order)
                .conditionTree(conditionTree);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillStatModifier)) return false;
        SkillStatModifier that = (SkillStatModifier) o;
        return Objects.equals(stat, that.stat)
                && Objects.equals(statRaw, that.statRaw)
                && Objects.equals(operation, that.operation)
                && Objects.equals(operationRaw, that.operationRaw)
                && Objects.equals(value, that.value)
                && Objects.equals(valueRaw, that.valueRaw)
                && Objects.equals(order, that.order)
                && Objects.equals(conditionTree, that.conditionTree);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stat, statRaw, operation, operationRaw, value, valueRaw, order, conditionTree);
    }

    @Override
    public String toString() {
        return "SkillStatModifier[" + "statRaw=" + statRaw + ", operationRaw=" + operationRaw + ", value=" + value
                + "]";
    }

    public static final class Builder {
        private @Nullable Stat stat;
        private String statRaw;
        private @Nullable StatOperation operation;
        private String operationRaw;
        private @Nullable Double value;
        private @Nullable String valueRaw;
        private @Nullable Integer order;
        private @Nullable SkillConditionNode conditionTree;

        public Builder stat(@Nullable Stat stat) {
            this.stat = stat;
            return this;
        }

        public Builder statRaw(String statRaw) {
            this.statRaw = statRaw;
            return this;
        }

        public Builder operation(@Nullable StatOperation operation) {
            this.operation = operation;
            return this;
        }

        public Builder operationRaw(String operationRaw) {
            this.operationRaw = operationRaw;
            return this;
        }

        public Builder value(@Nullable Double value) {
            this.value = value;
            return this;
        }

        public Builder valueRaw(@Nullable String valueRaw) {
            this.valueRaw = valueRaw;
            return this;
        }

        public Builder order(@Nullable Integer order) {
            this.order = order;
            return this;
        }

        public Builder conditionTree(@Nullable SkillConditionNode conditionTree) {
            this.conditionTree = conditionTree;
            return this;
        }

        public SkillStatModifier build() {
            return new SkillStatModifier(stat, statRaw, operation, operationRaw, value, valueRaw, order, conditionTree);
        }
    }
}
