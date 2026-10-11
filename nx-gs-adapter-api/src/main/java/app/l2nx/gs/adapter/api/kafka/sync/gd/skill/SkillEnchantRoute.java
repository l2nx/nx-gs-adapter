package app.l2nx.gs.adapter.api.kafka.sync.gd.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One enchant-route variant of a {@link Skill}: an enchanted level beyond the base ladder. {@code baseLevel},
 * {@code route} and {@code enchantLevel} are the identity; providers MUST emit the skill's max base level as
 * {@code baseLevel}, since enchanting is only available once the base ladder is maxed. The route carries only its
 * identity and enchant costs; everything per level is the enchanted {@link #getNode()}, whose level must equal
 * {@code baseLevel} (the constructor rejects any other).
 */
public final class SkillEnchantRoute {

    private final int baseLevel;
    private final int route;
    private final int enchantLevel;
    private final @Nullable Long enchantAdena;
    private final @Nullable Long enchantExp;
    private final @Nullable Long enchantSp;
    private final @Nullable Map<Integer, Integer> enchantChanceByCharLevelPercent;
    private final SkillLevel node;

    public SkillEnchantRoute(
            int baseLevel,
            int route,
            int enchantLevel,
            @Nullable Long enchantAdena,
            @Nullable Long enchantExp,
            @Nullable Long enchantSp,
            @Nullable Map<Integer, Integer> enchantChanceByCharLevelPercent,
            SkillLevel node) {
        Objects.requireNonNull(node, "node");
        if (node.getLevel() != baseLevel) {
            throw new IllegalArgumentException("node level " + node.getLevel() + " must equal baseLevel " + baseLevel);
        }
        this.baseLevel = baseLevel;
        this.route = route;
        this.enchantLevel = enchantLevel;
        this.enchantAdena = enchantAdena;
        this.enchantExp = enchantExp;
        this.enchantSp = enchantSp;
        this.enchantChanceByCharLevelPercent = enchantChanceByCharLevelPercent == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>(enchantChanceByCharLevelPercent));
        this.node = node;
    }

    public int getBaseLevel() {
        return baseLevel;
    }

    public int getRoute() {
        return route;
    }

    public int getEnchantLevel() {
        return enchantLevel;
    }

    public @Nullable Long getEnchantAdena() {
        return enchantAdena;
    }

    public @Nullable Long getEnchantExp() {
        return enchantExp;
    }

    public @Nullable Long getEnchantSp() {
        return enchantSp;
    }

    /**
     * Keyed by the enchanting character's level as the game defines it (hosts convert their own indexing);
     * levels without a defined chance are absent.
     */
    public @Nullable Map<Integer, Integer> getEnchantChanceByCharLevelPercent() {
        return enchantChanceByCharLevelPercent;
    }

    public SkillLevel getNode() {
        return node;
    }

    public Builder toBuilder() {
        return new Builder()
                .baseLevel(baseLevel)
                .route(route)
                .enchantLevel(enchantLevel)
                .enchantAdena(enchantAdena)
                .enchantExp(enchantExp)
                .enchantSp(enchantSp)
                .enchantChanceByCharLevelPercent(enchantChanceByCharLevelPercent)
                .node(node);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillEnchantRoute)) return false;
        SkillEnchantRoute that = (SkillEnchantRoute) o;
        return baseLevel == that.baseLevel
                && route == that.route
                && enchantLevel == that.enchantLevel
                && Objects.equals(enchantAdena, that.enchantAdena)
                && Objects.equals(enchantExp, that.enchantExp)
                && Objects.equals(enchantSp, that.enchantSp)
                && Objects.equals(enchantChanceByCharLevelPercent, that.enchantChanceByCharLevelPercent)
                && Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                baseLevel,
                route,
                enchantLevel,
                enchantAdena,
                enchantExp,
                enchantSp,
                enchantChanceByCharLevelPercent,
                node);
    }

    @Override
    public String toString() {
        return "SkillEnchantRoute[" + "baseLevel=" + baseLevel + ", route=" + route + ", enchantLevel=" + enchantLevel
                + "]";
    }

    public static final class Builder {
        private int baseLevel;
        private int route;
        private int enchantLevel;
        private @Nullable Long enchantAdena;
        private @Nullable Long enchantExp;
        private @Nullable Long enchantSp;
        private @Nullable Map<Integer, Integer> enchantChanceByCharLevelPercent;
        private SkillLevel node;

        public Builder baseLevel(int baseLevel) {
            this.baseLevel = baseLevel;
            return this;
        }

        public Builder route(int route) {
            this.route = route;
            return this;
        }

        public Builder enchantLevel(int enchantLevel) {
            this.enchantLevel = enchantLevel;
            return this;
        }

        public Builder enchantAdena(@Nullable Long enchantAdena) {
            this.enchantAdena = enchantAdena;
            return this;
        }

        public Builder enchantExp(@Nullable Long enchantExp) {
            this.enchantExp = enchantExp;
            return this;
        }

        public Builder enchantSp(@Nullable Long enchantSp) {
            this.enchantSp = enchantSp;
            return this;
        }

        public Builder enchantChanceByCharLevelPercent(
                @Nullable Map<Integer, Integer> enchantChanceByCharLevelPercent) {
            this.enchantChanceByCharLevelPercent = enchantChanceByCharLevelPercent;
            return this;
        }

        public Builder node(SkillLevel node) {
            this.node = node;
            return this;
        }

        public SkillEnchantRoute build() {
            return new SkillEnchantRoute(
                    baseLevel,
                    route,
                    enchantLevel,
                    enchantAdena,
                    enchantExp,
                    enchantSp,
                    enchantChanceByCharLevelPercent,
                    node);
        }
    }
}
