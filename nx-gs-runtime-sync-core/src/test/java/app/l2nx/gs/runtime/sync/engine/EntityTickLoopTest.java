package app.l2nx.gs.runtime.sync.engine;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.ops.model.EntityState;
import app.l2nx.gs.adapter.api.kafka.sync.db.SyncEvent;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
import app.l2nx.gs.adapter.api.spi.model.RuntimeEntityMapping;
import app.l2nx.gs.adapter.api.spi.model.RuntimeRow;
import app.l2nx.gs.runtime.sync.engine.publish.KafkaSender;
import app.l2nx.gs.runtime.sync.engine.publish.SyncEventPublisher;
import app.l2nx.gs.runtime.sync.engine.publish.TopicResolver;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.RecordBatch;
import org.junit.jupiter.api.Test;

class EntityTickLoopTest {

    @Test
    void tick_shouldEmitCreatedForNewPks_andUpdatedForChangedHashes() {
        Map<Long, Long> hashes = new HashMap<Long, Long>();
        hashes.put(1L, 100L);
        hashes.put(2L, 200L);
        StubMapping mapping = new StubMapping(hashes);
        CapturingSender sender = new CapturingSender();
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    mapping,
                    e -> "topic.character",
                    new SyncEventPublisher(sender),
                    tracker,
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());

            loop.tick();

            assertEquals(2, sender.captured.size());
            assertOpsContains(sender.captured, "CREATED", 2);
            assertEquals(EntityState.HEALTHY, tracker.currentStatuses().get(0).getState());
            assertEquals(0L, tracker.timedOutAcks(mapping.entityName()));

            sender.captured.clear();

            loop.tick();
            assertEquals(0, sender.captured.size());

            mapping.hashesByPk.put(1L, 999L);
            loop.tick();
            assertEquals(1, sender.captured.size());
            assertOpsContains(sender.captured, "UPDATED", 1);
            assertEquals(1L, sender.captured.get(0).pk);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldEmitUpdated_whenOnlyStateStampChanges() {
        Map<Long, Long> hashes = new HashMap<Long, Long>();
        hashes.put(1L, 100L);
        StubMapping mapping = new StubMapping(hashes);
        CapturingSender sender = new CapturingSender();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    mapping,
                    e -> "topic.character",
                    new SyncEventPublisher(sender),
                    new EntityStatsTracker(),
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());
            loop.tick();
            sender.captured.clear();

            loop.tick();
            assertEquals(0, sender.captured.size());

            mapping.stampsByPk.put(1L, 7L);
            loop.tick();
            assertOpsContains(sender.captured, "UPDATED", 1);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldDropGonePks_withoutEmittingTombstone() {
        Map<Long, Long> hashes = new HashMap<Long, Long>();
        hashes.put(1L, 100L);
        hashes.put(2L, 200L);
        StubMapping mapping = new StubMapping(hashes);
        CapturingSender sender = new CapturingSender();
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    mapping,
                    e -> "topic.character",
                    new SyncEventPublisher(sender),
                    tracker,
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());
            loop.tick();
            sender.captured.clear();

            mapping.hashesByPk.remove(2L);
            loop.tick();

            assertEquals(0, sender.captured.size());

            mapping.hashesByPk.put(2L, 200L);
            loop.tick();

            assertEquals(1, sender.captured.size());
            assertOpsContains(sender.captured, "CREATED", 1);
            assertEquals(2L, sender.captured.get(0).pk);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldDegrade_whenTopicMissing() {
        StubMapping mapping = new StubMapping(Collections.singletonMap(1L, 100L));
        CapturingSender sender = new CapturingSender();
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    mapping,
                    e -> null,
                    new SyncEventPublisher(sender),
                    tracker,
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());

            loop.tick();

            assertEquals(0, sender.captured.size());
            assertEquals(1, tracker.currentStatuses().size());
            assertEquals(EntityState.DEGRADED, tracker.currentStatuses().get(0).getState());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldDegrade_whenSnapshotThrows() {
        RuntimeEntityMapping<String> throwing = new RuntimeEntityMapping<String>() {
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
                throw new RuntimeException("boom");
            }

            @Override
            public long hash(String dto) {
                return 0L;
            }
        };
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    throwing,
                    e -> "topic.character",
                    new SyncEventPublisher((t, k, v, cb) -> {}),
                    tracker,
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());

            loop.tick();

            assertEquals(EntityState.DEGRADED, tracker.currentStatuses().get(0).getState());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldDegrade_whenSnapshotIterationThrowsCme() {
        RuntimeEntityMapping<String> cmeMapping = new RuntimeEntityMapping<String>() {
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
                return () -> new Iterator<RuntimeRow<String>>() {
                    private boolean served;

                    @Override
                    public boolean hasNext() {
                        return !served;
                    }

                    @Override
                    public RuntimeRow<String> next() {
                        served = true;
                        throw new java.util.ConcurrentModificationException("live view mutated");
                    }
                };
            }

            @Override
            public long hash(String dto) {
                return 0L;
            }
        };
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    cmeMapping,
                    e -> "topic.character",
                    new SyncEventPublisher((t, k, v, cb) -> {}),
                    tracker,
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());

            loop.tick();

            assertEquals(EntityState.DEGRADED, tracker.currentStatuses().get(0).getState());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldRecordDegraded_whenPublishFutureFails() {
        StubMapping mapping = new StubMapping(Collections.singletonMap(1L, 100L));
        KafkaSender failing =
                (topic, key, value, callback) -> callback.onCompletion(null, new RuntimeException("publish boom"));
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    mapping,
                    e -> "topic.character",
                    new SyncEventPublisher(failing),
                    tracker,
                    new EngineConfig(10, 1),
                    scheduler,
                    Clock.systemUTC());

            loop.tick();

            assertEquals(EntityState.DEGRADED, tracker.currentStatuses().get(0).getState());
            assertEquals(1L, tracker.failedAcks(mapping.entityName()));
            assertEquals(0L, tracker.timedOutAcks(mapping.entityName()));
            assertTrue(
                    loop.currentSnapshotKeysForTesting().isEmpty(),
                    "failed-publish PK must not advance into the next snapshot");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void tick_shouldClassifyZeroHashAsUnchanged() {
        Map<Long, Long> hashes = new HashMap<Long, Long>();
        hashes.put(42L, 0L);
        StubMapping mapping = new StubMapping(hashes);
        CapturingSender sender = new CapturingSender();
        EntityStatsTracker tracker = new EntityStatsTracker();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            EntityTickLoop loop = new EntityTickLoop(
                    mapping,
                    e -> "topic.character",
                    new SyncEventPublisher(sender),
                    tracker,
                    new EngineConfig(10, 5),
                    scheduler,
                    Clock.systemUTC());

            loop.tick();
            assertEquals(1, sender.captured.size());
            assertOpsContains(sender.captured, "CREATED", 1);

            sender.captured.clear();
            loop.tick();
            assertEquals(0, sender.captured.size(), "hash=0 must round-trip as unchanged — no spurious UPDATED");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void resolveTopicFromContext_shouldReadRuntimeNamespace() {
        SyncTopics topics = SyncTopics.builder()
                .db(Collections.singletonMap("clan", "acme.gs.sync.db.clan"))
                .runtime(Collections.singletonMap("character", "acme.gs.sync.runtime.character"))
                .build();
        ConnectContext ctx = ConnectContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantSlug("acme")
                .serverId(UUID.randomUUID())
                .serverSlug("primary")
                .serverName("Acme Primary")
                .adapterVersion("0.1.0")
                .syncTopics(topics)
                .build();

        TopicResolver resolver = TopicResolver.fromContext(ctx);

        assertEquals("acme.gs.sync.runtime.character", resolver.resolveTopic("character"));
        assertNull(resolver.resolveTopic("clan"));
    }

    private static void assertOpsContains(List<CapturedSend> captured, String op, int expectedCount) {
        int count = 0;
        for (CapturedSend c : captured) {
            if (op.equals(c.op)) count++;
        }
        assertEquals(expectedCount, count, "expected " + expectedCount + " " + op + " events");
    }

    private static final class StubMapping implements RuntimeEntityMapping<String> {
        private final Map<Long, Long> hashesByPk;
        private final Map<Long, Long> stampsByPk = new HashMap<Long, Long>();

        StubMapping(Map<Long, Long> hashesByPk) {
            this.hashesByPk = new HashMap<Long, Long>(hashesByPk);
        }

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
            for (Map.Entry<Long, Long> e : hashesByPk.entrySet()) {
                Long stamp = stampsByPk.get(e.getKey());
                rows.add(new RuntimeRow<String>(e.getKey(), "dto-" + e.getKey(), stamp == null ? 0L : stamp));
            }
            return rows;
        }

        @Override
        public long hash(String dto) {
            long pk = Long.parseLong(dto.substring("dto-".length()));
            Long h = hashesByPk.get(pk);
            return h == null ? 0L : h;
        }
    }

    private static final class CapturedSend {
        final String topic;
        final long pk;
        final String op;

        CapturedSend(String topic, long pk, String op) {
            this.topic = topic;
            this.pk = pk;
            this.op = op;
        }
    }

    private static final class CapturingSender implements KafkaSender {
        final List<CapturedSend> captured = new ArrayList<CapturedSend>();

        @Override
        public void send(String topic, byte[] key, Object value, Callback callback) {
            SyncEvent<?> event = (SyncEvent<?>) value;
            captured.add(new CapturedSend(topic, event.getPk(), event.getOp()));
            callback.onCompletion(
                    new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, RecordBatch.NO_TIMESTAMP, 0, 0), null);
        }
    }
}
