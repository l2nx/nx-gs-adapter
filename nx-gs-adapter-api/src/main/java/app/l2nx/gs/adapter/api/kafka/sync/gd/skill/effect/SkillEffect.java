package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect;

import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectScope;
import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectType;
import app.l2nx.gs.adapter.api.kafka.sync.gd.StatModifier;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.SkillLevel;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition.SkillConditionNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One effect a {@link SkillLevel} applies; {@code typeRaw} is the only non-null field. One engine effect object
 * may yield several records, each carrying the same {@code typeRaw} and its own canonical params (a combined exp and
 * SP grant becomes {@code GIVE_EXP} + {@code GIVE_SP}). {@link Builder#build()} rejects {@code params} on an effect
 * whose {@code type} is {@code null}, since canonical params are keyed per {@code type}; the constructor accepts it, so
 * a consumer reading a newer {@code type} as {@code null} still gets the effect.
 *
 * <p>Canonical {@code params} format, shared with {@code SkillConditionNode}: keys are lowerCamel, fixed per
 * {@code type}, and absent when they do not apply, never blank. Integers are decimal, decimals use {@code .},
 * booleans are {@code true} / {@code false}. {@code min} / {@code max} are inclusive bounds of the node's own measure
 * and {@code minLevel} / {@code maxLevel} inclusive bounds on a referenced object's level. Percents are
 * {@code <name>Percent} (0-100 unless the key says otherwise), durations carry the unit in the key ({@code ...Ms},
 * {@code ...Sec}). Id lists are {@code <thing>Ids}, comma-separated, any-of; several skills are
 * {@code id:level,id:level} (level {@code 0} = any); map values are {@code KEY:VALUE,KEY:VALUE}. Enum-valued keys
 * hold API enum constant names; engine data ids and abnormal types pass as is.</p>
 */
public final class SkillEffect {

    private final @Nullable SkillEffectType type;
    private final String typeRaw;
    private final @Nullable SkillEffectScope scope;
    private final @Nullable String scopeRaw;
    private final @Nullable Map<String, String> params;
    private final @Nullable Map<String, String> paramsRaw;
    private final @Nullable Double value;
    private final @Nullable String valueRaw;
    private final @Nullable Integer activateRatePercent;
    private final @Nullable String abnormalTypeRaw;
    private final @Nullable Integer abnormalLevel;
    private final @Nullable Integer abnormalTimeSec;
    private final @Nullable Integer tickIntervalMs;
    private final @Nullable SkillConditionNode conditionTree;
    private final @Nullable Boolean derived;
    private final @Nullable List<StatModifier> statModifiers;

    public SkillEffect(
            @Nullable SkillEffectType type,
            String typeRaw,
            @Nullable SkillEffectScope scope,
            @Nullable String scopeRaw,
            @Nullable Map<String, String> params,
            @Nullable Map<String, String> paramsRaw,
            @Nullable Double value,
            @Nullable String valueRaw,
            @Nullable Integer activateRatePercent,
            @Nullable String abnormalTypeRaw,
            @Nullable Integer abnormalLevel,
            @Nullable Integer abnormalTimeSec,
            @Nullable Integer tickIntervalMs,
            @Nullable SkillConditionNode conditionTree,
            @Nullable Boolean derived,
            @Nullable List<StatModifier> statModifiers) {
        this.type = type;
        this.typeRaw = Objects.requireNonNull(typeRaw, "typeRaw");
        this.scope = scope;
        this.scopeRaw = scopeRaw;
        this.params = params == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(params));
        this.paramsRaw =
                paramsRaw == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(paramsRaw));
        this.value = value;
        this.valueRaw = valueRaw;
        this.activateRatePercent = activateRatePercent;
        this.abnormalTypeRaw = abnormalTypeRaw;
        this.abnormalLevel = abnormalLevel;
        this.abnormalTimeSec = abnormalTimeSec;
        this.tickIntervalMs = tickIntervalMs;
        this.conditionTree = conditionTree;
        this.derived = derived;
        this.statModifiers =
                statModifiers == null ? null : Collections.unmodifiableList(new ArrayList<StatModifier>(statModifiers));
    }

    public @Nullable SkillEffectType getType() {
        return type;
    }

    /** Engine effect token; for a {@linkplain #getDerived() derived} effect, the source token it was derived from. */
    public String getTypeRaw() {
        return typeRaw;
    }

    /** When and to whom the effect applies; effects of a passive skill are {@code PASSIVE} on every build. */
    public @Nullable SkillEffectScope getScope() {
        return scope;
    }

    /** {@code null} on builds without an effect-scope token, where the host derives {@code scope}. */
    public @Nullable String getScopeRaw() {
        return scopeRaw;
    }

    /**
     * Canonical parameters keyed per {@code type} (format in the class doc); {@code null} when {@code type} is {@code null}.
     */
    public @Nullable Map<String, String> getParams() {
        return params;
    }

    public @Nullable Map<String, String> getParamsRaw() {
        return paramsRaw;
    }

    /**
     * Main magnitude in the unit fixed per {@code type}; {@code null} when the type has no magnitude or the
     * engine computes it at runtime.
     */
    public @Nullable Double getValue() {
        return value;
    }

    /** Engine expression when the magnitude is not a constant ({@code value} is then {@code null}). */
    public @Nullable String getValueRaw() {
        return valueRaw;
    }

    /** Per-effect base land rate; {@code null} when {@link SkillLevel#getActivateRatePercent()} applies. */
    public @Nullable Integer getActivateRatePercent() {
        return activateRatePercent;
    }

    /** Buff-slot stacking type (client data per chronicle); {@code null} when the effect occupies no buff slot. */
    public @Nullable String getAbnormalTypeRaw() {
        return abnormalTypeRaw;
    }

    /** Higher overwrites lower within {@code abnormalTypeRaw}. */
    public @Nullable Integer getAbnormalLevel() {
        return abnormalLevel;
    }

    /**
     * The effect's own total duration when it differs from {@link SkillLevel#getAbnormalTimeSec()};
     * {@code null} when the level value applies.
     */
    public @Nullable Integer getAbnormalTimeSec() {
        return abnormalTimeSec;
    }

    /**
     * Server-effective period of a periodic effect, server configuration applied; {@code null} when not
     * periodic. Tick count = duration / interval.
     */
    public @Nullable Integer getTickIntervalMs() {
        return tickIntervalMs;
    }

    /**
     * Attach condition of the whole effect; {@code null} = unconditional. Per-modifier conditions live on
     * {@link StatModifier#getConditionTree()}.
     */
    public @Nullable SkillConditionNode getConditionTree() {
        return conditionTree;
    }

    /**
     * {@code true} when the host synthesized the effect with no engine effect object behind it (from a
     * skill-type token or lethal-strike parameters); {@code null} otherwise.
     */
    public @Nullable Boolean getDerived() {
        return derived;
    }

    /**
     * Stat modifiers the effect applies while active: the whole behaviour of a {@code STAT_MODIFIER} effect, or side
     * modifiers of another type (a stun that also slows); {@code null} when none.
     */
    public @Nullable List<StatModifier> getStatModifiers() {
        return statModifiers;
    }

    public Builder toBuilder() {
        return new Builder()
                .type(type)
                .typeRaw(typeRaw)
                .scope(scope)
                .scopeRaw(scopeRaw)
                .params(params)
                .paramsRaw(paramsRaw)
                .value(value)
                .valueRaw(valueRaw)
                .activateRatePercent(activateRatePercent)
                .abnormalTypeRaw(abnormalTypeRaw)
                .abnormalLevel(abnormalLevel)
                .abnormalTimeSec(abnormalTimeSec)
                .tickIntervalMs(tickIntervalMs)
                .conditionTree(conditionTree)
                .derived(derived)
                .statModifiers(statModifiers);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillEffect)) return false;
        SkillEffect that = (SkillEffect) o;
        return Objects.equals(type, that.type)
                && Objects.equals(typeRaw, that.typeRaw)
                && Objects.equals(scope, that.scope)
                && Objects.equals(scopeRaw, that.scopeRaw)
                && Objects.equals(params, that.params)
                && Objects.equals(paramsRaw, that.paramsRaw)
                && Objects.equals(value, that.value)
                && Objects.equals(valueRaw, that.valueRaw)
                && Objects.equals(activateRatePercent, that.activateRatePercent)
                && Objects.equals(abnormalTypeRaw, that.abnormalTypeRaw)
                && Objects.equals(abnormalLevel, that.abnormalLevel)
                && Objects.equals(abnormalTimeSec, that.abnormalTimeSec)
                && Objects.equals(tickIntervalMs, that.tickIntervalMs)
                && Objects.equals(conditionTree, that.conditionTree)
                && Objects.equals(derived, that.derived)
                && Objects.equals(statModifiers, that.statModifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                type,
                typeRaw,
                scope,
                scopeRaw,
                params,
                paramsRaw,
                value,
                valueRaw,
                activateRatePercent,
                abnormalTypeRaw,
                abnormalLevel,
                abnormalTimeSec,
                tickIntervalMs,
                conditionTree,
                derived,
                statModifiers);
    }

    @Override
    public String toString() {
        return "SkillEffect[" + "type=" + type + ", typeRaw=" + typeRaw + ", scope=" + scope + "]";
    }

    public static final class Builder {
        private @Nullable SkillEffectType type;
        private String typeRaw;
        private @Nullable SkillEffectScope scope;
        private @Nullable String scopeRaw;
        private @Nullable Map<String, String> params;
        private @Nullable Map<String, String> paramsRaw;
        private @Nullable Double value;
        private @Nullable String valueRaw;
        private @Nullable Integer activateRatePercent;
        private @Nullable String abnormalTypeRaw;
        private @Nullable Integer abnormalLevel;
        private @Nullable Integer abnormalTimeSec;
        private @Nullable Integer tickIntervalMs;
        private @Nullable SkillConditionNode conditionTree;
        private @Nullable Boolean derived;
        private @Nullable List<StatModifier> statModifiers;

        public Builder type(@Nullable SkillEffectType type) {
            this.type = type;
            return this;
        }

        public Builder typeRaw(String typeRaw) {
            this.typeRaw = typeRaw;
            return this;
        }

        public Builder scope(@Nullable SkillEffectScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder scopeRaw(@Nullable String scopeRaw) {
            this.scopeRaw = scopeRaw;
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

        public Builder value(@Nullable Double value) {
            this.value = value;
            return this;
        }

        public Builder valueRaw(@Nullable String valueRaw) {
            this.valueRaw = valueRaw;
            return this;
        }

        public Builder activateRatePercent(@Nullable Integer activateRatePercent) {
            this.activateRatePercent = activateRatePercent;
            return this;
        }

        public Builder abnormalTypeRaw(@Nullable String abnormalTypeRaw) {
            this.abnormalTypeRaw = abnormalTypeRaw;
            return this;
        }

        public Builder abnormalLevel(@Nullable Integer abnormalLevel) {
            this.abnormalLevel = abnormalLevel;
            return this;
        }

        public Builder abnormalTimeSec(@Nullable Integer abnormalTimeSec) {
            this.abnormalTimeSec = abnormalTimeSec;
            return this;
        }

        public Builder tickIntervalMs(@Nullable Integer tickIntervalMs) {
            this.tickIntervalMs = tickIntervalMs;
            return this;
        }

        public Builder conditionTree(@Nullable SkillConditionNode conditionTree) {
            this.conditionTree = conditionTree;
            return this;
        }

        public Builder derived(@Nullable Boolean derived) {
            this.derived = derived;
            return this;
        }

        public Builder statModifiers(@Nullable List<StatModifier> statModifiers) {
            this.statModifiers = statModifiers;
            return this;
        }

        public SkillEffect build() {
            if (params != null && type == null) {
                throw new IllegalArgumentException("params requires a mapped type");
            }
            return new SkillEffect(
                    type,
                    typeRaw,
                    scope,
                    scopeRaw,
                    params,
                    paramsRaw,
                    value,
                    valueRaw,
                    activateRatePercent,
                    abnormalTypeRaw,
                    abnormalLevel,
                    abnormalTimeSec,
                    tickIntervalMs,
                    conditionTree,
                    derived,
                    statModifiers);
        }
    }
}
