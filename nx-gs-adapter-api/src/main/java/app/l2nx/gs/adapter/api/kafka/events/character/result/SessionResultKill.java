package app.l2nx.gs.adapter.api.kafka.events.character.result;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Kills of one victim during a session: an NPC template or a character, exactly one of the two. */
public final class SessionResultKill {

    private final @Nullable Long sourceNpcId;
    private final @Nullable Long sourceCharacterId;
    private final long count;

    public SessionResultKill(@Nullable Long sourceNpcId, @Nullable Long sourceCharacterId, long count) {
        if ((sourceNpcId == null) == (sourceCharacterId == null)) {
            throw new IllegalArgumentException(
                    "SessionResultKill needs exactly one of sourceNpcId and sourceCharacterId");
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("SessionResultKill.count must be positive, got " + count);
        }
        this.sourceNpcId = sourceNpcId;
        this.sourceCharacterId = sourceCharacterId;
        this.count = count;
    }

    /** NPC template id. */
    public @Nullable Long getSourceNpcId() {
        return sourceNpcId;
    }

    public @Nullable Long getSourceCharacterId() {
        return sourceCharacterId;
    }

    public long getCount() {
        return count;
    }

    public Builder toBuilder() {
        return new Builder()
                .sourceNpcId(sourceNpcId)
                .sourceCharacterId(sourceCharacterId)
                .count(count);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResultKill)) return false;
        SessionResultKill that = (SessionResultKill) o;
        return count == that.count
                && Objects.equals(sourceNpcId, that.sourceNpcId)
                && Objects.equals(sourceCharacterId, that.sourceCharacterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNpcId, sourceCharacterId, count);
    }

    @Override
    public String toString() {
        return "SessionResultKill[sourceNpcId=" + sourceNpcId
                + ", sourceCharacterId=" + sourceCharacterId
                + ", count=" + count + "]";
    }

    public static final class Builder {
        private @Nullable Long sourceNpcId;
        private @Nullable Long sourceCharacterId;
        private long count;

        public Builder sourceNpcId(@Nullable Long sourceNpcId) {
            this.sourceNpcId = sourceNpcId;
            return this;
        }

        public Builder sourceCharacterId(@Nullable Long sourceCharacterId) {
            this.sourceCharacterId = sourceCharacterId;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public SessionResultKill build() {
            return new SessionResultKill(sourceNpcId, sourceCharacterId, count);
        }
    }
}
