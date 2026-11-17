package app.l2nx.gs.db.sync.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.gs.adapter.api.kafka.sync.db.SyncEvent;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.db.sync.engine.phase.Phase1Hasher;
import app.l2nx.gs.db.sync.engine.phase.Phase2Fetcher;
import app.l2nx.gs.db.sync.engine.publish.SyncEventPublisher;
import app.l2nx.gs.db.sync.engine.window.WindowPlanner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Each event's timestamp is the instant its read began, not the publish instant. The clock moves only when a scripted
 * query completes, so each instant names the step that ran before it.
 */
class EntitySyncTaskReadInstantTest {

    private static final RecordMetadata META = new RecordMetadata(new TopicPartition("t", 0), 0L, 0, 0L, 0, 0);
    private static final Instant CYCLE_START = Instant.parse("2026-11-17T10:00:00.000Z");
    private static final Instant FIRST_SCAN_DONE = Instant.parse("2026-11-17T10:00:01.000Z");
    private static final Instant SECOND_SCAN_DONE = Instant.parse("2026-11-17T10:00:02.000Z");
    private static final Instant FETCH_DONE = Instant.parse("2026-11-17T10:00:03.000Z");

    private final SteppedClock clock = new SteppedClock();
    private final SnapshotStore snapshot = new SnapshotStore();
    private final Deque<List<Map<Object, Object>>> scanResults = new ArrayDeque<List<Map<Object, Object>>>();
    private final Deque<Instant> scanDoneAt = new ArrayDeque<Instant>();
    private final List<SyncEvent<?>> published = new CopyOnWriteArrayList<SyncEvent<?>>();
    private final Map<SyncEvent<?>, Instant> sentAt = new ConcurrentHashMap<SyncEvent<?>, Instant>();
    private List<Map<Object, Object>> fetchResult = Collections.emptyList();

    @BeforeEach
    void setUp() {
        clock.now = CYCLE_START;
    }

    @Nested
    class RunCycle {

        @Test
        void runCycle_shouldStampCreatedAndUpdatedWithTheirFetchStart_notThePublishInstant() throws Exception {
            snapshot.putCrc("clan", 1L, 100);
            scan(FIRST_SCAN_DONE, hashRow(1L, 101), hashRow(3L, 300));
            fetchResult = Arrays.asList(clanRow(1L, "Updated"), clanRow(3L, "Created"));

            task(1L, 3L, 500_000).runCycle();

            assertEquals(2, published.size());
            assertStamped(eventOf("UPDATED", 1L), FIRST_SCAN_DONE, FETCH_DONE);
            assertStamped(eventOf("CREATED", 3L), FIRST_SCAN_DONE, FETCH_DONE);
        }

        @Test
        void runCycle_shouldStampDeletedWithItsWindowScanStart() throws Exception {
            snapshot.putCrc("clan", 2L, 200);
            snapshot.putCrc("clan", 4L, 400);
            scan(FIRST_SCAN_DONE);
            scan(SECOND_SCAN_DONE);

            task(1L, 4L, 2).runCycle();

            assertEquals(2, published.size());
            assertStamped(eventOf("DELETED", 2L), CYCLE_START, FIRST_SCAN_DONE);
            assertStamped(eventOf("DELETED", 4L), FIRST_SCAN_DONE, SECOND_SCAN_DONE);
        }
    }

    @SafeVarargs
    private final void scan(Instant doneAt, Map<Object, Object>... rows) {
        scanResults.add(Arrays.asList(rows));
        scanDoneAt.add(doneAt);
    }

    private static Map<Object, Object> hashRow(long pk, long crc) {
        Map<Object, Object> row = new HashMap<Object, Object>();
        row.put(1, pk);
        row.put(2, crc);
        return row;
    }

    private static Map<Object, Object> clanRow(long pk, String name) {
        Map<Object, Object> row = new HashMap<Object, Object>();
        row.put("clan_id", pk);
        row.put("clan_name", name);
        row.put("clan_level", 1);
        return row;
    }

    private void assertStamped(SyncEvent<?> event, Instant readStarted, Instant sent) {
        assertEquals(sent, sentAt.get(event), "send instant of " + event);
        assertEquals(readStarted.toEpochMilli(), event.getTimestampEpochMs(), "read instant of " + event);
    }

    private SyncEvent<?> eventOf(String op, long pk) {
        for (SyncEvent<?> event : published) {
            if (event.getOp().equals(op) && event.getPk() == pk) {
                return event;
            }
        }
        return fail("no " + op + " event for pk " + pk + " in " + published);
    }

    private EntitySyncTask task(long minPk, long maxPk, int rowsPerWindow) throws SQLException {
        JdbcConnectionSource source = mock(JdbcConnectionSource.class);
        Connection conn = connection(minPk, maxPk);
        when(source.getConnection()).thenReturn(conn);
        return new EntitySyncTask(
                TestMappings.clanOnly(),
                source,
                snapshot,
                new WindowPlanner(),
                new Phase1Hasher(),
                new Phase2Fetcher(),
                new SyncEventPublisher((topic, key, value, callback) -> {
                    published.add((SyncEvent<?>) value);
                    sentAt.put((SyncEvent<?>) value, clock.now);
                    callback.onCompletion(META, null);
                }),
                entity -> "test.gs.sync.clans",
                new EngineConfig(60, rowsPerWindow, 5, 1),
                clock);
    }

    private Connection connection(long minPk, long maxPk) throws SQLException {
        Connection conn = mock(Connection.class, RETURNS_DEEP_STUBS);
        when(conn.getAutoCommit()).thenReturn(true);
        Statement statement = mock(Statement.class);
        Map<Object, Object> range = new HashMap<Object, Object>();
        range.put(1, minPk);
        range.put(2, maxPk);
        when(statement.executeQuery(anyString())).thenAnswer(inv -> resultSet(Collections.singletonList(range)));
        when(conn.createStatement()).thenReturn(statement);
        when(conn.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            PreparedStatement ps = mock(PreparedStatement.class);
            if (sql.contains("CRC32")) {
                when(ps.executeQuery()).thenAnswer(q -> {
                    clock.now = scanDoneAt.removeFirst();
                    return resultSet(scanResults.removeFirst());
                });
            } else {
                when(ps.executeQuery()).thenAnswer(q -> {
                    clock.now = FETCH_DONE;
                    return resultSet(fetchResult);
                });
            }
            return ps;
        });
        return conn;
    }

    private static ResultSet resultSet(List<Map<Object, Object>> rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        int[] cursor = {-1};
        when(rs.next()).thenAnswer(inv -> ++cursor[0] < rows.size());
        when(rs.getLong(anyInt())).thenAnswer(inv -> number(rows.get(cursor[0]).get(inv.getArgument(0))));
        when(rs.getLong(anyString()))
                .thenAnswer(inv -> number(rows.get(cursor[0]).get(inv.getArgument(0))));
        when(rs.getInt(anyString()))
                .thenAnswer(inv -> (int) number(rows.get(cursor[0]).get(inv.getArgument(0))));
        when(rs.getString(anyString())).thenAnswer(inv -> rows.get(cursor[0]).get(inv.getArgument(0)));
        return rs;
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static final class SteppedClock extends Clock {
        volatile Instant now;

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
