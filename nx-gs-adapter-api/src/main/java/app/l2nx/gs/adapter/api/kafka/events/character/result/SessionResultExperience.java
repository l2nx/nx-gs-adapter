package app.l2nx.gs.adapter.api.kafka.events.character.result;

import app.l2nx.gs.adapter.api.domain.character.ExperienceSource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Experience and SP of one {@code (source, sourceNpcId)} of a session; both values are signed. */
public final class SessionResultExperience {

    private final ExperienceSource source;
    private final @Nullable Long sourceNpcId;
    private final long exp;
    private final long sp;

    public SessionResultExperience(ExperienceSource source, @Nullable Long sourceNpcId, long exp, long sp) {
        this.source = Objects.requireNonNull(source, "SessionResultExperience.source is required");
        this.sourceNpcId = sourceNpcId;
        this.exp = exp;
        this.sp = sp;
    }

    public ExperienceSource getSource() {
        return source;
    }

    public @Nullable Long getSourceNpcId() {
        return sourceNpcId;
    }

    public long getExp() {
        return exp;
    }

    public long getSp() {
        return sp;
    }

    public Builder toBuilder() {
        return new Builder().source(source).sourceNpcId(sourceNpcId).exp(exp).sp(sp);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResultExperience)) return false;
        SessionResultExperience that = (SessionResultExperience) o;
        return exp == that.exp
                && sp == that.sp
                && source == that.source
                && Objects.equals(sourceNpcId, that.sourceNpcId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, sourceNpcId, exp, sp);
    }

    @Override
    public String toString() {
        return "SessionResultExperience[source=" + source
                + ", sourceNpcId=" + sourceNpcId
                + ", exp=" + exp
                + ", sp=" + sp + "]";
    }

    public static final class Builder {
        private @Nullable ExperienceSource source;
        private @Nullable Long sourceNpcId;
        private long exp;
        private long sp;

        public Builder source(ExperienceSource source) {
            this.source = source;
            return this;
        }

        public Builder sourceNpcId(@Nullable Long sourceNpcId) {
            this.sourceNpcId = sourceNpcId;
            return this;
        }

        public Builder exp(long exp) {
            this.exp = exp;
            return this;
        }

        public Builder sp(long sp) {
            this.sp = sp;
            return this;
        }

        public SessionResultExperience build() {
            return new SessionResultExperience(source, sourceNpcId, exp, sp);
        }
    }
}
