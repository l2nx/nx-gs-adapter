package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Blocks on the database; throws {@link OfflineSessionStoreException} on failure. Times are UTC. */
interface OfflineSessionStore {

    enum InsertResult {
        INSERTED,
        DUPLICATE_IDEMPOTENCY_KEY,
        CHARACTER_ACTIVE,
        SPOT_TAKEN
    }

    enum PlaceResult {
        PLACED,
        SPOT_TAKEN,
        NOT_OPEN
    }

    /** Oldest first; rows this adapter version cannot read are skipped. */
    List<OfflineSession> loadOpen();

    int deleteClosedBefore(Instant cutoff);

    Optional<OfflineSession> findByIdempotencyKey(UUID idempotencyKey);

    Optional<OfflineSession> findOpenByCharacterId(int characterId);

    /**
     * At most {@code limit} sessions of the master account and activity, open or closed, latest {@code started_at}
     * first, ties by the greater id.
     */
    List<OfflineSession> findLatestStartedByMasterAccount(UUID masterAccountId, ActivityType activity, int limit);

    /** As {@link #findLatestStartedByMasterAccount(UUID, ActivityType, int)}, for the sessions of one character. */
    List<OfflineSession> findLatestStartedByCharacter(int characterId, ActivityType activity, int limit);

    /** A unique-constraint conflict is classified in the result, never thrown. */
    InsertResult insert(OfflineSession session);

    /**
     * The conflict {@link #insert} would hit now, without writing: {@link InsertResult#CHARACTER_ACTIVE} before
     * {@link InsertResult#SPOT_TAKEN}, else {@link InsertResult#INSERTED}. Rows this adapter version cannot read count.
     */
    InsertResult probeInsert(int characterId, String spotPool, int spotId);

    void delete(UUID id);

    /** {@code typeState} {@code null} keeps the stored one. */
    PlaceResult place(UUID id, String spotPool, int spotId, @Nullable Instant startedAt, @Nullable String typeState);

    /** Never lowers a stored value and never touches a closed row. */
    void flushActiveMs(Map<UUID, Long> activeMsById);

    /** A closed row is left as it is. */
    void close(UUID id, long activeMs, Instant endedAt, LogoutReason reason);

    /**
     * The next at most {@code limit} closed sessions whose end Kafka has not acknowledged, in {@code (ended_at, id)}
     * order after {@code after} ({@code null}: from the start). Unreadable rows move the cursor but are left out of the
     * rows, so they never hide the rows behind them.
     */
    UnpublishedEnds findUnpublishedEnds(@Nullable EndCursor after, int limit);

    /**
     * Records Kafka's acknowledgement of these ends in one write; open rows and ones already marked are left as they
     * are.
     */
    void markEndsPublished(Collection<UUID> ids, Instant publishedAt);

    boolean updateTypeState(UUID id, @Nullable String typeState);

    /** A closed row's place in the {@code (ended_at, id)} order of {@link #findUnpublishedEnds}. */
    final class EndCursor {
        final Instant endedAt;
        final UUID id;

        EndCursor(Instant endedAt, UUID id) {
            this.endedAt = endedAt;
            this.id = id;
        }
    }

    final class UnpublishedEnds {
        final List<OfflineSession> rows;
        /** The last row of the page, a skipped one included; {@code null} for an empty page. */
        final @Nullable EndCursor last;
        /** The page was shorter than its limit: nothing follows it. */
        final boolean exhausted;

        UnpublishedEnds(List<OfflineSession> rows, @Nullable EndCursor last, boolean exhausted) {
            this.rows = rows;
            this.last = last;
            this.exhausted = exhausted;
        }
    }
}
