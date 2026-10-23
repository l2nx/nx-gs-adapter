package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import java.time.Instant;
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
    List<OfflineSession> findLatestStarted(UUID masterAccountId, ActivityType activity, int limit);

    /** A unique-constraint conflict is classified in the result, never thrown. */
    InsertResult insert(OfflineSession session);

    void delete(UUID id);

    /** {@code typeState} {@code null} keeps the stored one. */
    PlaceResult place(UUID id, String spotPool, int spotId, @Nullable Instant startedAt, @Nullable String typeState);

    /** Never lowers a stored value and never touches a closed row. */
    void flushActiveMs(Map<UUID, Long> activeMsById);

    /** A closed row is left as it is. */
    void close(UUID id, long activeMs, Instant endedAt, LogoutReason reason);

    boolean updateTypeState(UUID id, @Nullable String typeState);
}
