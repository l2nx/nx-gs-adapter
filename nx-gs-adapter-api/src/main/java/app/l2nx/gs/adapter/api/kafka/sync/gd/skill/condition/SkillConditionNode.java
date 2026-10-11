package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition;

import app.l2nx.gs.adapter.api.domain.skill.condition.SkillConditionLogic;
import app.l2nx.gs.adapter.api.domain.skill.condition.SkillConditionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Node of a skill condition tree. A container ({@code logic} non-null) combines at least one child and carries
 * no predicate; a leaf ({@code logic} {@code null}) is one engine predicate with a non-null {@code typeRaw} and no
 * children. A container carries no {@code params} or {@code paramsRaw}, and a leaf carries {@code params} only with a
 * non-null {@code type}. {@code systemMessageId} may sit on any node: the message shown when that subtree fails. The constructor rejects any other shape.
 *
 * <p>Any node may be {@code negated}: every engine negation and engine boolean flag folds into it, so canonical
 * predicates are stated affirmatively. One engine condition may yield several leaves sharing {@code typeRaw}.</p>
 */
public final class SkillConditionNode {

    private final @Nullable SkillConditionLogic logic;
    private final @Nullable SkillConditionType type;
    private final @Nullable String typeRaw;
    private final boolean negated;
    private final @Nullable Map<String, String> params;
    private final @Nullable Map<String, String> paramsRaw;
    private final @Nullable Integer systemMessageId;
    private final @Nullable List<SkillConditionNode> children;

    public SkillConditionNode(
            @Nullable SkillConditionLogic logic,
            @Nullable SkillConditionType type,
            @Nullable String typeRaw,
            boolean negated,
            @Nullable Map<String, String> params,
            @Nullable Map<String, String> paramsRaw,
            @Nullable Integer systemMessageId,
            @Nullable List<SkillConditionNode> children) {
        if (logic != null) {
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException("container node needs at least one child");
            }
            if (typeRaw != null || type != null) {
                throw new IllegalArgumentException("container node must not carry a predicate");
            }
            if (params != null || paramsRaw != null) {
                throw new IllegalArgumentException("container node must not carry params");
            }
        } else {
            if (typeRaw == null) {
                throw new IllegalArgumentException("leaf node needs typeRaw");
            }
            if (children != null) {
                throw new IllegalArgumentException("leaf node must not carry children");
            }
            if (params != null && type == null) {
                throw new IllegalArgumentException("leaf params require a mapped type");
            }
        }
        this.logic = logic;
        this.type = type;
        this.typeRaw = typeRaw;
        this.negated = negated;
        this.params = params == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(params));
        this.paramsRaw =
                paramsRaw == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(paramsRaw));
        this.systemMessageId = systemMessageId;
        this.children =
                children == null ? null : Collections.unmodifiableList(new ArrayList<SkillConditionNode>(children));
    }

    public @Nullable SkillConditionLogic getLogic() {
        return logic;
    }

    /** Canonical predicate of a leaf; {@code null} on containers and when unmapped. */
    public @Nullable SkillConditionType getType() {
        return type;
    }

    public @Nullable String getTypeRaw() {
        return typeRaw;
    }

    public boolean isNegated() {
        return negated;
    }

    /** Canonical parameters keyed per {@code type}, formatted as {@code SkillEffect.params}. */
    public @Nullable Map<String, String> getParams() {
        return params;
    }

    /** Engine attributes of the leaf, keys and values untouched. */
    public @Nullable Map<String, String> getParamsRaw() {
        return paramsRaw;
    }

    public @Nullable Integer getSystemMessageId() {
        return systemMessageId;
    }

    public @Nullable List<SkillConditionNode> getChildren() {
        return children;
    }

    public Builder toBuilder() {
        return new Builder()
                .logic(logic)
                .type(type)
                .typeRaw(typeRaw)
                .negated(negated)
                .params(params)
                .paramsRaw(paramsRaw)
                .systemMessageId(systemMessageId)
                .children(children);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillConditionNode)) return false;
        SkillConditionNode that = (SkillConditionNode) o;
        return Objects.equals(logic, that.logic)
                && Objects.equals(type, that.type)
                && Objects.equals(typeRaw, that.typeRaw)
                && negated == that.negated
                && Objects.equals(params, that.params)
                && Objects.equals(paramsRaw, that.paramsRaw)
                && Objects.equals(systemMessageId, that.systemMessageId)
                && Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logic, type, typeRaw, negated, params, paramsRaw, systemMessageId, children);
    }

    @Override
    public String toString() {
        return "SkillConditionNode[" + "logic=" + logic + ", type=" + type + ", typeRaw=" + typeRaw + ", negated="
                + negated + ", children=" + (children == null ? 0 : children.size()) + "]";
    }

    public static final class Builder {
        private @Nullable SkillConditionLogic logic;
        private @Nullable SkillConditionType type;
        private @Nullable String typeRaw;
        private boolean negated;
        private @Nullable Map<String, String> params;
        private @Nullable Map<String, String> paramsRaw;
        private @Nullable Integer systemMessageId;
        private @Nullable List<SkillConditionNode> children;

        public Builder logic(@Nullable SkillConditionLogic logic) {
            this.logic = logic;
            return this;
        }

        public Builder type(@Nullable SkillConditionType type) {
            this.type = type;
            return this;
        }

        public Builder typeRaw(@Nullable String typeRaw) {
            this.typeRaw = typeRaw;
            return this;
        }

        public Builder negated(boolean negated) {
            this.negated = negated;
            return this;
        }

        public Builder params(@Nullable Map<String, String> params) {
            this.params = params;
            return this;
        }

        public Builder paramsRaw(@Nullable Map<String, String> paramsRaw) {
            this.paramsRaw = paramsRaw;
            return this;
        }

        public Builder systemMessageId(@Nullable Integer systemMessageId) {
            this.systemMessageId = systemMessageId;
            return this;
        }

        public Builder children(@Nullable List<SkillConditionNode> children) {
            this.children = children;
            return this;
        }

        public SkillConditionNode build() {
            return new SkillConditionNode(logic, type, typeRaw, negated, params, paramsRaw, systemMessageId, children);
        }
    }
}
