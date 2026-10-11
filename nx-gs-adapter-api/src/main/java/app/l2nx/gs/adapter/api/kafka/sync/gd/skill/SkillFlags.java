package app.l2nx.gs.adapter.api.kafka.sync.gd.skill;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Boolean classification flags of a {@link Skill}, level-invariant (read from the canonical level). All fields
 * are tri-state {@link Nullable Boolean}: {@code true} / {@code false} / unknown.
 */
public final class SkillFlags {

    private final @Nullable Boolean debuff;
    private final @Nullable Boolean offensive;
    private final @Nullable Boolean staticReuse;
    private final @Nullable Boolean blockedInOlympiad;
    private final @Nullable Boolean overHit;
    private final @Nullable Boolean ignoreShield;
    private final @Nullable Boolean nextActionAttack;
    private final @Nullable Boolean dispellable;
    private final @Nullable Boolean reflectable;
    private final @Nullable Boolean stayAfterDeath;
    private final @Nullable Boolean triggered;

    public SkillFlags(
            @Nullable Boolean debuff,
            @Nullable Boolean offensive,
            @Nullable Boolean staticReuse,
            @Nullable Boolean blockedInOlympiad,
            @Nullable Boolean overHit,
            @Nullable Boolean ignoreShield,
            @Nullable Boolean nextActionAttack,
            @Nullable Boolean dispellable,
            @Nullable Boolean reflectable,
            @Nullable Boolean stayAfterDeath,
            @Nullable Boolean triggered) {
        this.debuff = debuff;
        this.offensive = offensive;
        this.staticReuse = staticReuse;
        this.blockedInOlympiad = blockedInOlympiad;
        this.overHit = overHit;
        this.ignoreShield = ignoreShield;
        this.nextActionAttack = nextActionAttack;
        this.dispellable = dispellable;
        this.reflectable = reflectable;
        this.stayAfterDeath = stayAfterDeath;
        this.triggered = triggered;
    }

    public @Nullable Boolean getDebuff() {
        return debuff;
    }

    public @Nullable Boolean getOffensive() {
        return offensive;
    }

    /** The reuse delay ignores the caster's reuse stats. */
    public @Nullable Boolean getStaticReuse() {
        return staticReuse;
    }

    public @Nullable Boolean getBlockedInOlympiad() {
        return blockedInOlympiad;
    }

    /** Excess damage carries to exp reward on the killing blow. */
    public @Nullable Boolean getOverHit() {
        return overHit;
    }

    public @Nullable Boolean getIgnoreShield() {
        return ignoreShield;
    }

    public @Nullable Boolean getNextActionAttack() {
        return nextActionAttack;
    }

    public @Nullable Boolean getDispellable() {
        return dispellable;
    }

    public @Nullable Boolean getReflectable() {
        return reflectable;
    }

    public @Nullable Boolean getStayAfterDeath() {
        return stayAfterDeath;
    }

    /** Cast only by a trigger; its effect takes the trigger buff slot. */
    public @Nullable Boolean getTriggered() {
        return triggered;
    }

    public Builder toBuilder() {
        return new Builder()
                .debuff(debuff)
                .offensive(offensive)
                .staticReuse(staticReuse)
                .blockedInOlympiad(blockedInOlympiad)
                .overHit(overHit)
                .ignoreShield(ignoreShield)
                .nextActionAttack(nextActionAttack)
                .dispellable(dispellable)
                .reflectable(reflectable)
                .stayAfterDeath(stayAfterDeath)
                .triggered(triggered);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillFlags)) return false;
        SkillFlags that = (SkillFlags) o;
        return Objects.equals(debuff, that.debuff)
                && Objects.equals(offensive, that.offensive)
                && Objects.equals(staticReuse, that.staticReuse)
                && Objects.equals(blockedInOlympiad, that.blockedInOlympiad)
                && Objects.equals(overHit, that.overHit)
                && Objects.equals(ignoreShield, that.ignoreShield)
                && Objects.equals(nextActionAttack, that.nextActionAttack)
                && Objects.equals(dispellable, that.dispellable)
                && Objects.equals(reflectable, that.reflectable)
                && Objects.equals(stayAfterDeath, that.stayAfterDeath)
                && Objects.equals(triggered, that.triggered);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                debuff,
                offensive,
                staticReuse,
                blockedInOlympiad,
                overHit,
                ignoreShield,
                nextActionAttack,
                dispellable,
                reflectable,
                stayAfterDeath,
                triggered);
    }

    @Override
    public String toString() {
        return "SkillFlags[" + "debuff=" + debuff + ", offensive=" + offensive + ", triggered=" + triggered + "]";
    }

    public static final class Builder {
        private @Nullable Boolean debuff;
        private @Nullable Boolean offensive;
        private @Nullable Boolean staticReuse;
        private @Nullable Boolean blockedInOlympiad;
        private @Nullable Boolean overHit;
        private @Nullable Boolean ignoreShield;
        private @Nullable Boolean nextActionAttack;
        private @Nullable Boolean dispellable;
        private @Nullable Boolean reflectable;
        private @Nullable Boolean stayAfterDeath;
        private @Nullable Boolean triggered;

        public Builder debuff(@Nullable Boolean debuff) {
            this.debuff = debuff;
            return this;
        }

        public Builder offensive(@Nullable Boolean offensive) {
            this.offensive = offensive;
            return this;
        }

        public Builder staticReuse(@Nullable Boolean staticReuse) {
            this.staticReuse = staticReuse;
            return this;
        }

        public Builder blockedInOlympiad(@Nullable Boolean blockedInOlympiad) {
            this.blockedInOlympiad = blockedInOlympiad;
            return this;
        }

        public Builder overHit(@Nullable Boolean overHit) {
            this.overHit = overHit;
            return this;
        }

        public Builder ignoreShield(@Nullable Boolean ignoreShield) {
            this.ignoreShield = ignoreShield;
            return this;
        }

        public Builder nextActionAttack(@Nullable Boolean nextActionAttack) {
            this.nextActionAttack = nextActionAttack;
            return this;
        }

        public Builder dispellable(@Nullable Boolean dispellable) {
            this.dispellable = dispellable;
            return this;
        }

        public Builder reflectable(@Nullable Boolean reflectable) {
            this.reflectable = reflectable;
            return this;
        }

        public Builder stayAfterDeath(@Nullable Boolean stayAfterDeath) {
            this.stayAfterDeath = stayAfterDeath;
            return this;
        }

        public Builder triggered(@Nullable Boolean triggered) {
            this.triggered = triggered;
            return this;
        }

        public SkillFlags build() {
            return new SkillFlags(
                    debuff,
                    offensive,
                    staticReuse,
                    blockedInOlympiad,
                    overHit,
                    ignoreShield,
                    nextActionAttack,
                    dispellable,
                    reflectable,
                    stayAfterDeath,
                    triggered);
        }
    }
}
