package app.l2nx.gs.runtime.sync.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.l2nx.gs.adapter.api.kafka.sync.db.SyncEvent;
import app.l2nx.gs.adapter.api.spi.model.RuntimeEntityMapping;
import app.l2nx.gs.adapter.api.spi.model.RuntimeRow;
import app.l2nx.gs.runtime.sync.engine.publish.KafkaSender;
import app.l2nx.gs.runtime.sync.engine.publish.SyncEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.RecordBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Each event's timestamp is the instant the tick began reading the snapshot, not the publish instant. The clock moves
 * while the snapshot is read and on every send, so a stamp taken anywhere after the read start shows up as a mismatch.
 */
class EntityTickLoopReadInstantTest {

    private static final Instant FIRST_TICK = Instant.parse("2026-11-17T10:00:00.000Z");
    private static final Instant SECOND_TICK = Instant.parse("2026-11-17T10:05:00.000Z");
    private static final Duration SNAPSHOT_READ = Duration.ofSeconds(1);
    private static final Duration PER_SEND = Duration.ofSeconds(1);

    private final SteppedClock clock = new SteppedClock();
    private final StubMapping mapping = new StubMapping();
    private final RecordingSender sender = new RecordingSender();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Nested
    class Tick {

        @Test
        void tick_shouldStampCreatedWithTheSnapshotReadStart_notThePublishInstant() {
            mapping.hashesByPk.put(1L, 100L);
            mapping.hashesByPk.put(2L, 200L);
            EntityTickLoop loop = loop();
            clock.now = FIRST_TICK;

            loop.tick();

            assertEquals(2, sender.events.size());
            for (SyncEvent<?> event : sender.events) {
                assertEquals("CREATED", event.getOp());
                assertEquals(FIRST_TICK.toEpochMilli(), event.getTimestampEpochMs());
            }
            assertEquals(FIRST_TICK.plus(SNAPSHOT_READ), sender.sentAt.get(0), "the publish ran after the read began");
        }

        @Test
        void tick_shouldStampUpdatedWithItsOwnTicksReadStart() {
            mapping.hashesByPk.put(1L, 100L);
            EntityTickLoop loop = loop();
            clock.now = FIRST_TICK;
            loop.tick();
            sender.events.clear();
            sender.sentAt.clear();

            mapping.hashesByPk.put(1L, 101L);
            clock.now = SECOND_TICK;
            loop.tick();

            assertEquals(1, sender.events.size());
            assertEquals("UPDATED", sender.events.get(0).getOp());
            assertEquals(SECOND_TICK.toEpochMilli(), sender.events.get(0).getTimestampEpochMs());
        }

        @Test
        void tick_shouldStampAReplayedPublishWithTheReplayingTicksReadStart() {
            mapping.hashesByPk.put(1L, 100L);
            EntityTickLoop loop = loop();
            sender.failNext = true;
            clock.now = FIRST_TICK;
            loop.tick();
            sender.events.clear();
            sender.sentAt.clear();

            clock.now = SECOND_TICK;
            loop.tick();

            assertEquals(1, sender.events.size());
            assertEquals("CREATED", sender.events.get(0).getOp());
            assertEquals(SECOND_TICK.toEpochMilli(), sender.events.get(0).getTimestampEpochMs());
        }
    }

    private EntityTickLoop loop() {
        return new EntityTickLoop(
                mapping,
                e -> "topic.character",
                new SyncEventPublisher(sender),
                new EntityStatsTracker(),
                new EngineConfig(10, 1),
                scheduler,
                clock);
    }

    private final class StubMapping implements RuntimeEntityMapping<String> {
        final Map<Long, Long> hashesByPk = new LinkedHashMap<Long, Long>();

        @Override
        public String entityName() {
            return "character";
        }

        @Override
        public Class<String> dtoType() {
            return String.class;
        }

        @Override
        public Iterable<RuntimeRow<String>> snapshot() {
            List<RuntimeRow<String>> rows = new ArrayList<RuntimeRow<String>>();
            for (Long pk : hashesByPk.keySet()) {
                rows.add(new RuntimeRow<String>(pk, "dto-" + pk));
            }
            clock.advance(SNAPSHOT_READ);
            return rows;
        }

        @Override
        public long hash(String dto) {
            return hashesByPk.get(Long.parseLong(dto.substring("dto-".length())));
        }
    }

    private final class RecordingSender implements KafkaSender {
        final List<SyncEvent<?>> events = new ArrayList<SyncEvent<?>>();
        final List<Instant> sentAt = new ArrayList<Instant>();
        boolean failNext;

        @Override
        public void send(String topic, byte[] key, Object value, Callback callback) {
            events.add((SyncEvent<?>) value);
            sentAt.add(clock.now);
            clock.advance(PER_SEND);
            if (failNext) {
                failNext = false;
                callback.onCompletion(null, new RuntimeException("publish failed"));
                return;
            }
            callback.onCompletion(
                    new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, RecordBatch.NO_TIMESTAMP, 0, 0), null);
        }
    }

    private static final class SteppedClock extends Clock {
        volatile Instant now;

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
