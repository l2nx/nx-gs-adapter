package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.events.character.AppSessionsSnapshotEvent;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.commons.UUIDv7;
import app.l2nx.gs.commons.concurrent.SafeRunnable;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** A snapshot due while no platform connection is bound is skipped, not buffered: the next one is a minute away. */
final class AppSessionsSnapshotPublisher {

    private static final NxLog log = NxLogFactory.getLogger(AppSessionsSnapshotPublisher.class);

    static final long INTERVAL_MS = 60_000L;

    private final Supplier<List<UUID>> openSessionIds;
    private final AppSessionClock clock;
    private volatile @Nullable NxEvents events;

    AppSessionsSnapshotPublisher(Supplier<List<UUID>> openSessionIds, AppSessionClock clock) {
        this.openSessionIds = openSessionIds;
        this.clock = clock;
    }

    void bind(NxEvents events) {
        this.events = events;
    }

    void unbind() {
        this.events = null;
    }

    /** The first snapshot goes out at once. */
    void start(ScheduledExecutorService scheduler) {
        try {
            scheduler.scheduleWithFixedDelay(
                    SafeRunnable.wrap(this::publish, log), 0L, INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shuttingDown) {
            log.debug("App sessions: snapshot not scheduled, the scheduler is shutting down");
        }
    }

    void publish() {
        NxEvents bound = events;
        if (bound == null) {
            log.debug("App sessions: open-sessions snapshot skipped, no platform connection");
            return;
        }
        // before the ids, in the table's second precision: a session missing from them has startedAt >= takenAt
        Instant takenAt = clock.now().truncatedTo(ChronoUnit.SECONDS);
        List<UUID> ids = openSessionIds.get();
        bound.publish(AppSessionsSnapshotEvent.builder()
                .eventId(UUIDv7.generate(takenAt))
                .takenAt(takenAt)
                .sessionIds(ids)
                .build());
    }
}
