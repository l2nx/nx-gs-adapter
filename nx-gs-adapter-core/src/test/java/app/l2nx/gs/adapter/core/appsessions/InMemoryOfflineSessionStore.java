package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class InMemoryOfflineSessionStore implements OfflineSessionStore {

    final Map<UUID, OfflineSession> rows = new LinkedHashMap<UUID, OfflineSession>();
    int flushes;
    int failCloses;
    int failDeletes;

    synchronized void seed(OfflineSession row) {
        rows.put(row.getId(), row);
    }

    synchronized OfflineSession row(UUID id) {
        return rows.get(id);
    }

    @Override
    public synchronized List<OfflineSession> loadOpen() {
        List<OfflineSession> open = new ArrayList<OfflineSession>();
        for (OfflineSession row : rows.values()) {
            if (row.isOpen()) {
                open.add(row);
            }
        }
        open.sort(Comparator.comparing(OfflineSession::getStartedAt));
        return open;
    }

    @Override
    public synchronized int deleteClosedBefore(Instant cutoff) {
        int deleted = 0;
        for (Iterator<OfflineSession> it = rows.values().iterator(); it.hasNext(); ) {
            OfflineSession row = it.next();
            if (!row.isOpen() && row.getEndedAt().isBefore(cutoff)) {
                it.remove();
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public synchronized Optional<OfflineSession> findByIdempotencyKey(UUID idempotencyKey) {
        for (OfflineSession row : rows.values()) {
            if (row.getIdempotencyKey().equals(idempotencyKey)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized Optional<OfflineSession> findOpenByCharacterId(int characterId) {
        for (OfflineSession row : rows.values()) {
            if (row.isOpen() && row.getCharacterId() == characterId) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized InsertResult insert(OfflineSession session) {
        if (findByIdempotencyKey(session.getIdempotencyKey()).isPresent()) {
            return InsertResult.DUPLICATE_IDEMPOTENCY_KEY;
        }
        if (findOpenByCharacterId(session.getCharacterId()).isPresent()) {
            return InsertResult.CHARACTER_ACTIVE;
        }
        if (spotHeld(session.spotKey(), null)) {
            return InsertResult.SPOT_TAKEN;
        }
        rows.put(session.getId(), session);
        return InsertResult.INSERTED;
    }

    @Override
    public synchronized void delete(UUID id) {
        if (failDeletes > 0) {
            failDeletes--;
            throw new OfflineSessionStoreException("simulated delete failure", new RuntimeException());
        }
        rows.remove(id);
    }

    @Override
    public synchronized PlaceResult place(
            UUID id, String spotPool, int spotId, @Nullable Instant startedAt, @Nullable String typeState) {
        OfflineSession row = rows.get(id);
        if (row == null || !row.isOpen()) {
            return PlaceResult.NOT_OPEN;
        }
        if (spotHeld(spotPool + ":" + spotId, id)) {
            return PlaceResult.SPOT_TAKEN;
        }
        OfflineSession.Builder moved = row.toBuilder().spotId(spotId);
        if (startedAt != null) {
            moved.startedAt(startedAt);
        }
        if (typeState != null) {
            moved.typeState(typeState);
        }
        rows.put(id, moved.build());
        return PlaceResult.PLACED;
    }

    @Override
    public synchronized void flushActiveMs(Map<UUID, Long> activeMsById) {
        flushes++;
        for (Map.Entry<UUID, Long> e : activeMsById.entrySet()) {
            OfflineSession row = rows.get(e.getKey());
            if (row != null && row.isOpen() && row.getActiveMs() <= e.getValue()) {
                rows.put(row.getId(), row.toBuilder().activeMs(e.getValue()).build());
            }
        }
    }

    @Override
    public synchronized void close(UUID id, long activeMs, Instant endedAt, LogoutReason reason) {
        if (failCloses > 0) {
            failCloses--;
            throw new OfflineSessionStoreException("simulated close failure", new RuntimeException());
        }
        OfflineSession row = rows.get(id);
        if (row != null && row.isOpen()) {
            rows.put(
                    id,
                    row.toBuilder()
                            .activeMs(activeMs)
                            .endedAt(endedAt)
                            .endReason(reason)
                            .build());
        }
    }

    @Override
    public synchronized boolean updateTypeState(UUID id, @Nullable String typeState) {
        OfflineSession row = rows.get(id);
        if (row == null || !row.isOpen()) {
            return false;
        }
        rows.put(id, row.toBuilder().typeState(typeState).build());
        return true;
    }

    private boolean spotHeld(String spotKey, @Nullable UUID except) {
        for (OfflineSession row : rows.values()) {
            if (row.isOpen() && row.spotKey().equals(spotKey) && !row.getId().equals(except)) {
                return true;
            }
        }
        return false;
    }
}
