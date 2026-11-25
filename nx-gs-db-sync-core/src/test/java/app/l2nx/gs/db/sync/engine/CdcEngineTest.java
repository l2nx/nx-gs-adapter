package app.l2nx.gs.db.sync.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import app.l2nx.gs.adapter.api.kafka.ops.model.EntityState;
import app.l2nx.gs.adapter.api.kafka.ops.model.EntityStats;
import app.l2nx.gs.adapter.api.kafka.sync.db.clan.ClanDbDto;
import app.l2nx.gs.adapter.api.spi.model.EntityMapping;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.db.sync.engine.persist.NoopSnapshotPersistence;
import app.l2nx.gs.db.sync.engine.persist.SnapshotPersistence;
import app.l2nx.gs.db.sync.engine.phase.Phase1Hasher;
import app.l2nx.gs.db.sync.engine.phase.Phase2Fetcher;
import app.l2nx.gs.db.sync.engine.publish.KafkaSender;
import app.l2nx.gs.db.sync.engine.publish.SyncEventPublisher;
import app.l2nx.gs.db.sync.engine.publish.TopicResolver;
import app.l2nx.gs.db.sync.engine.window.WindowPlanner;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CdcEngineTest {

    private CdcEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
    }

    @Test
    void start_shouldSeedDegradedStats_whenEntityHasNoTopic() {
        EntityStatsTracker tracker = new EntityStatsTracker();
        TopicResolver resolver = entity -> null;
        engine = new CdcEngine(
                "acme",
                Collections.singletonList(clanMapping()),
                mock(JdbcConnectionSource.class),
                new SnapshotStore(),
                NoopSnapshotPersistence.INSTANCE,
                new EngineConfig(60, 500_000, 10, 5),
                resolver,
                new SyncEventPublisher(neverCalledSender()),
                tracker,
                new WindowPlanner(),
                new Phase1Hasher(),
                new Phase2Fetcher(),
                k -> null,
                RecordingNxEvents.noop());

        // Stop immediately so the periodic scheduler doesn't tick under test.
        engine.start();
        engine.stop();

        List<EntityStats> snapshot = tracker.currentStatuses();
        assertEquals(1, snapshot.size());
        assertEquals("clan", snapshot.get(0).getName());
        assertEquals(EntityState.DEGRADED, snapshot.get(0).getState());
    }

    @Test
    void start_shouldBeIdempotent() {
        RecordingPersistence recorder = new RecordingPersistence();
        EntityStatsTracker tracker = new EntityStatsTracker();
        engine = new CdcEngine(
                "acme",
                Collections.singletonList(clanMapping()),
                mock(JdbcConnectionSource.class),
                new SnapshotStore(),
                recorder,
                new EngineConfig(3600, 500_000, 10, 5),
                entity -> null,
                new SyncEventPublisher(neverCalledSender()),
                tracker,
                new WindowPlanner(),
                new Phase1Hasher(),
                new Phase2Fetcher(),
                k -> null,
                RecordingNxEvents.noop());

        engine.start();
        engine.start();
        engine.stop();

        assertEquals(1, recorder.loadCount, "the second start must not load again");
        assertEquals(1, tracker.currentStatuses().size());
    }

    @Test
    void lifecycle_shouldLoadOnStartAndFlushPlusCloseOnStop() {
        RecordingPersistence recorder = new RecordingPersistence();
        SnapshotStore snapshot = new SnapshotStore();

        engine = new CdcEngine(
                "acme",
                Collections.singletonList(clanMapping()),
                mock(JdbcConnectionSource.class),
                snapshot,
                recorder,
                new EngineConfig(3600, 500_000, 10, 5),
                entity -> null,
                new SyncEventPublisher(neverCalledSender()),
                new EntityStatsTracker(),
                new WindowPlanner(),
                new Phase1Hasher(),
                new Phase2Fetcher(),
                k -> null,
                RecordingNxEvents.noop());

        engine.start();
        assertEquals(1, recorder.loadCount, "load() must run exactly once on start");
        assertEquals(0, recorder.flushCount, "no flush during start");

        engine.stop();
        assertEquals(1, recorder.flushCount, "flushAll() must run on stop");
        assertEquals(1, recorder.closeCount, "close() must run on stop");
        assertEquals(
                1L,
                recorder.flushSeenSizeBeforeClear,
                "flushAll must observe load()-seeded content (runs before snapshot.clearAll)");
        assertEquals(0, snapshot.sizeOf("clan"), "snapshot must be cleared on stop, AFTER flushAll");
    }

    @Test
    void start_shouldSwallowPersistenceLoadFailure_andStillRun() {
        SnapshotPersistence boom = new SnapshotPersistence() {
            @Override
            public void load(SnapshotStore t) {
                throw new RuntimeException("boom from load");
            }

            @Override
            public void checkpoint(String e, SnapshotStore s) {}

            @Override
            public void flushAll(SnapshotStore s) {}

            @Override
            public void close() {}
        };

        EntityStatsTracker tracker = new EntityStatsTracker();
        engine = new CdcEngine(
                "acme",
                Collections.singletonList(clanMapping()),
                mock(JdbcConnectionSource.class),
                new SnapshotStore(),
                boom,
                new EngineConfig(3600, 500_000, 10, 5),
                entity -> null,
                new SyncEventPublisher(neverCalledSender()),
                tracker,
                new WindowPlanner(),
                new Phase1Hasher(),
                new Phase2Fetcher(),
                k -> null,
                RecordingNxEvents.noop());

        engine.start();
        engine.stop();
    }

    private static EntityMapping<ClanDbDto> clanMapping() {
        return TestMappings.clanOnly();
    }

    private static final class RecordingPersistence implements SnapshotPersistence {
        int loadCount;
        int flushCount;
        int closeCount;
        long flushSeenSizeBeforeClear = -1L;

        @Override
        public void load(SnapshotStore target) {
            loadCount++;
            // Seed something so we verify flushAll runs before clearAll().
            target.putCrc("clan", 1L, 999);
        }

        @Override
        public void checkpoint(String entityName, SnapshotStore source) {}

        @Override
        public void flushAll(SnapshotStore source) {
            flushCount++;
            flushSeenSizeBeforeClear = source.sizeOf("clan");
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static KafkaSender neverCalledSender() {
        return (topic, key, value, callback) -> {
            throw new AssertionError("KafkaSender must not be invoked when entity has no topic");
        };
    }
}
