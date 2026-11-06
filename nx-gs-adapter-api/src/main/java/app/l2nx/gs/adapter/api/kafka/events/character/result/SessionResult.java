package app.l2nx.gs.adapter.api.kafka.events.character.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a session changed, carried by its end event. A consumer derives statistics from it; no separate stream exists.
 *
 * <p>List getters never return {@code null}: a section absent on the wire reads as empty. Gson bypasses the constructor,
 * so a consumer re-checks what it depends on.</p>
 */
public final class SessionResult {

    /**
     * Ceiling of the serialized block in UTF-8 bytes, before compression; leaves room for the rest of the event inside
     * the producer's default 1 MiB limit. A host folds {@link #getUniques()} into {@link #getItems()} past it, else the
     * adapter drops the block and the end goes out with {@code result = null}.
     */
    public static final int MAX_JSON_BYTES = 800_000;

    private final boolean partial;
    private final long overflow;
    private final List<SessionResultItem> items;
    private final List<SessionResultUnique> uniques;
    private final List<SessionResultExperience> experience;
    private final List<SessionResultKill> kills;
    private final List<SessionResultDeath> deaths;
    private final List<SessionResultUntracked> untracked;

    public SessionResult(
            boolean partial,
            long overflow,
            @Nullable List<SessionResultItem> items,
            @Nullable List<SessionResultUnique> uniques,
            @Nullable List<SessionResultExperience> experience,
            @Nullable List<SessionResultKill> kills,
            @Nullable List<SessionResultDeath> deaths,
            @Nullable List<SessionResultUntracked> untracked) {
        if (overflow < 0L) {
            throw new IllegalArgumentException("SessionResult.overflow must not be negative, got " + overflow);
        }
        this.partial = partial;
        this.overflow = overflow;
        this.items = copy(items);
        this.uniques = copy(uniques);
        this.experience = copy(experience);
        this.kills = copy(kills);
        this.deaths = copy(deaths);
        this.untracked = copy(untracked);
    }

    private static <T> List<T> copy(@Nullable List<T> rows) {
        return rows == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<T>(rows));
    }

    private static <T> List<T> orEmpty(@Nullable List<T> rows) {
        return rows == null ? Collections.<T>emptyList() : rows;
    }

    /**
     * {@code true} when a host crash cost part of the session; set by the engine, always {@code false} on a client
     * session.
     */
    public boolean isPartial() {
        return partial;
    }

    /** Rows folded away, past the host's row ceiling or past the byte budget; {@code 0} means nothing was folded. */
    public long getOverflow() {
        return overflow;
    }

    /** Stackable items, one row per {@code (itemTemplateId, source, sourceNpcId, sourceCharacterId, sourceRefId)}. */
    public List<SessionResultItem> getItems() {
        return orEmpty(items);
    }

    public List<SessionResultUnique> getUniques() {
        return orEmpty(uniques);
    }

    /** Experience and SP, one row per {@code (source, sourceNpcId)}. */
    public List<SessionResultExperience> getExperience() {
        return orEmpty(experience);
    }

    public List<SessionResultKill> getKills() {
        return orEmpty(kills);
    }

    public List<SessionResultDeath> getDeaths() {
        return orEmpty(deaths);
    }

    public List<SessionResultUntracked> getUntracked() {
        return orEmpty(untracked);
    }

    public Builder toBuilder() {
        return new Builder()
                .partial(partial)
                .overflow(overflow)
                .items(getItems())
                .uniques(getUniques())
                .experience(getExperience())
                .kills(getKills())
                .deaths(getDeaths())
                .untracked(getUntracked());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResult)) return false;
        SessionResult that = (SessionResult) o;
        return partial == that.partial
                && overflow == that.overflow
                && getItems().equals(that.getItems())
                && getUniques().equals(that.getUniques())
                && getExperience().equals(that.getExperience())
                && getKills().equals(that.getKills())
                && getDeaths().equals(that.getDeaths())
                && getUntracked().equals(that.getUntracked());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                partial, overflow, getItems(), getUniques(), getExperience(), getKills(), getDeaths(), getUntracked());
    }

    /** Sizes only: a block may hold thousands of rows and the event around it gets logged. */
    @Override
    public String toString() {
        return "SessionResult[partial=" + partial
                + ", overflow=" + overflow
                + ", items=" + getItems().size()
                + ", uniques=" + getUniques().size()
                + ", experience=" + getExperience().size()
                + ", kills=" + getKills().size()
                + ", deaths=" + getDeaths().size()
                + ", untracked=" + getUntracked().size() + "]";
    }

    public static final class Builder {
        private boolean partial;
        private long overflow;
        private @Nullable List<SessionResultItem> items;
        private @Nullable List<SessionResultUnique> uniques;
        private @Nullable List<SessionResultExperience> experience;
        private @Nullable List<SessionResultKill> kills;
        private @Nullable List<SessionResultDeath> deaths;
        private @Nullable List<SessionResultUntracked> untracked;

        public Builder partial(boolean partial) {
            this.partial = partial;
            return this;
        }

        public Builder overflow(long overflow) {
            this.overflow = overflow;
            return this;
        }

        public Builder items(@Nullable List<SessionResultItem> items) {
            this.items = items;
            return this;
        }

        public Builder uniques(@Nullable List<SessionResultUnique> uniques) {
            this.uniques = uniques;
            return this;
        }

        public Builder experience(@Nullable List<SessionResultExperience> experience) {
            this.experience = experience;
            return this;
        }

        public Builder kills(@Nullable List<SessionResultKill> kills) {
            this.kills = kills;
            return this;
        }

        public Builder deaths(@Nullable List<SessionResultDeath> deaths) {
            this.deaths = deaths;
            return this;
        }

        public Builder untracked(@Nullable List<SessionResultUntracked> untracked) {
            this.untracked = untracked;
            return this;
        }

        public SessionResult build() {
            return new SessionResult(partial, overflow, items, uniques, experience, kills, deaths, untracked);
        }
    }
}
