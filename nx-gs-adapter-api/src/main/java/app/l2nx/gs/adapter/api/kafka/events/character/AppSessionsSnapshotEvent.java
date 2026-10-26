package app.l2nx.gs.adapter.api.kafka.events.character;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ids of every open app session of this server, running or awaiting restore. A full set, not a diff; empty is valid.
 * {@code takenAt} is read before the ids, so a session opened at or after it may be missing: a consumer never closes
 * one on that basis. Gson and Jackson may bypass the constructor, so a consumer re-checks what it depends on.
 */
public final class AppSessionsSnapshotEvent {

    private final UUID eventId;
    private final Instant takenAt;
    private final List<UUID> sessionIds;

    public AppSessionsSnapshotEvent(UUID eventId, Instant takenAt, @Nullable List<UUID> sessionIds) {
        this.eventId = Objects.requireNonNull(eventId, "AppSessionsSnapshotEvent.eventId is required");
        this.takenAt = Objects.requireNonNull(takenAt, "AppSessionsSnapshotEvent.takenAt is required");
        this.sessionIds = freeze(sessionIds);
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    /** Never {@code null}: a {@code null} constructor argument becomes an empty list. Unmodifiable. */
    public List<UUID> getSessionIds() {
        return sessionIds;
    }

    public Builder toBuilder() {
        return new Builder().eventId(eventId).takenAt(takenAt).sessionIds(sessionIds);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<UUID> freeze(@Nullable List<UUID> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> copy = new ArrayList<UUID>(src.size());
        for (UUID id : src) {
            copy.add(Objects.requireNonNull(id, "AppSessionsSnapshotEvent.sessionIds must not contain null"));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSessionsSnapshotEvent)) return false;
        AppSessionsSnapshotEvent that = (AppSessionsSnapshotEvent) o;
        return eventId.equals(that.eventId) && takenAt.equals(that.takenAt) && sessionIds.equals(that.sessionIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, takenAt, sessionIds);
    }

    @Override
    public String toString() {
        return "AppSessionsSnapshotEvent[eventId=" + eventId + ", takenAt=" + takenAt + ", sessionIds=" + sessionIds
                + "]";
    }

    public static final class Builder {
        private @Nullable UUID eventId;
        private @Nullable Instant takenAt;
        private @Nullable List<UUID> sessionIds;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder takenAt(Instant takenAt) {
            this.takenAt = takenAt;
            return this;
        }

        public Builder sessionIds(@Nullable List<UUID> sessionIds) {
            this.sessionIds = sessionIds;
            return this;
        }

        public AppSessionsSnapshotEvent build() {
            return new AppSessionsSnapshotEvent(eventId, takenAt, sessionIds);
        }
    }
}
