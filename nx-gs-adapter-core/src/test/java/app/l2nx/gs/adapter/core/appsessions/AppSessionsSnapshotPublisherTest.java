package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.events.character.AppSessionsSnapshotEvent;
import app.l2nx.gs.commons.UUIDv7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AppSessionsSnapshotPublisherTest {

    private static final Instant TAKEN_AT = Instant.parse("2026-10-26T10:15:30.123Z");

    private final List<String> reads = new ArrayList<String>();
    private final List<UUID> open = Arrays.asList(UUIDv7.generate(), UUIDv7.generate());
    private final AppSessionEngineTest.RecordingEvents events = new AppSessionEngineTest.RecordingEvents();

    private final AppSessionClock clock = new AppSessionClock() {
        @Override
        public long monotonicMs() {
            return 0L;
        }

        @Override
        public Instant now() {
            reads.add("takenAt");
            return TAKEN_AT;
        }
    };

    private final AppSessionsSnapshotPublisher publisher = new AppSessionsSnapshotPublisher(
            () -> {
                reads.add("sessionIds");
                return open;
            },
            clock);

    @Nested
    class Publish {

        @Test
        void publish_shouldReadTakenAtBeforeTheSessionIds() {
            publisher.bind(events);

            publisher.publish();

            assertEquals(Arrays.asList("takenAt", "sessionIds"), reads);
        }

        @Test
        void publish_shouldCarryTheOpenSessionsAndAnEventIdOfTheSnapshotTimeInSeconds() {
            publisher.bind(events);

            publisher.publish();

            AppSessionsSnapshotEvent actual = events.snapshots.get(0);
            Instant expected = Instant.parse("2026-10-26T10:15:30Z");
            assertEquals(open, actual.getSessionIds());
            assertEquals(expected, actual.getTakenAt());
            assertEquals(expected, UUIDv7.extractCreatedAt(actual.getEventId()));
        }

        @Test
        void publish_shouldSkipTheSnapshot_whenNoConnectionIsBound() {
            publisher.publish();
            publisher.bind(events);
            publisher.unbind();
            publisher.publish();

            assertTrue(events.snapshots.isEmpty());
            assertTrue(reads.isEmpty());
        }
    }
}
