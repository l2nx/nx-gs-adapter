package app.l2nx.gs.db.sync.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.db.sync.engine.jdbc.StatementRegistry;
import app.l2nx.gs.db.sync.engine.persist.NoopSnapshotPersistence;
import app.l2nx.gs.db.sync.engine.phase.Phase1Hasher;
import app.l2nx.gs.db.sync.engine.phase.Phase2Fetcher;
import app.l2nx.gs.db.sync.engine.publish.SyncEventPublisher;
import app.l2nx.gs.db.sync.engine.window.WindowPlanner;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StatementCancelTest {

    @Test
    void cancelCurrent_shouldNoOp_whenNoStatementRegistered() {
        StatementRegistry registry = new StatementRegistry();
        registry.cancelCurrent();
    }

    @Test
    void cancelCurrent_shouldCallStatementCancel_whenRegistered() throws SQLException {
        StatementRegistry registry = new StatementRegistry();
        Statement statement = mock(Statement.class);
        registry.set(statement);

        registry.cancelCurrent();

        verify(statement).cancel();
    }

    @Test
    void cancelCurrent_shouldSwallowThrowables_fromDriverCancel() throws SQLException {
        StatementRegistry registry = new StatementRegistry();
        Statement statement = mock(Statement.class);
        doThrow(new SQLException("cancel unsupported")).when(statement).cancel();
        registry.set(statement);

        registry.cancelCurrent();
        verify(statement).cancel();
    }

    @Test
    void clear_shouldUnregisterStatement_soSubsequentCancelIsNoOp() throws SQLException {
        StatementRegistry registry = new StatementRegistry();
        Statement statement = mock(Statement.class);
        registry.set(statement);
        registry.clear();

        registry.cancelCurrent();
        verify(statement, never()).cancel();
    }

    @Test
    void cdcEngineStop_shouldCancelInFlightStatements() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenAnswer(inv -> {
            executing.countDown();
            awaitIgnoringInterrupts(cancelled);
            throw new SQLException("statement cancelled");
        });
        doAnswer(inv -> {
                    cancelled.countDown();
                    return null;
                })
                .when(statement)
                .cancel();
        CdcEngine engine = buildEngine(statement);
        engine.start();
        try {
            engine.requestPkRepublishNoEvent("clan", LongSets.singleton(1L));
            assertTrue(executing.await(10, TimeUnit.SECONDS), "the cycle must reach its query");

            engine.stop();

            verify(statement).cancel();
        } finally {
            cancelled.countDown();
            engine.stop();
        }
    }

    /** Like a driver blocked on a socket read: the pool interrupt does not end the query. */
    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static CdcEngine buildEngine(PreparedStatement statement) throws SQLException {
        Connection connection = mock(Connection.class, RETURNS_DEEP_STUBS);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        JdbcConnectionSource source = mock(JdbcConnectionSource.class);
        when(source.getConnection()).thenReturn(connection);
        return new CdcEngine(
                "test",
                Collections.singletonList(TestMappings.clanOnly()),
                source,
                new SnapshotStore(),
                NoopSnapshotPersistence.INSTANCE,
                new EngineConfig(3600, 500_000, 5, 2),
                entity -> "test.gs.sync.clans",
                new SyncEventPublisher((topic, key, value, callback) -> {}),
                new EntityStatsTracker(),
                new WindowPlanner(),
                new Phase1Hasher(),
                new Phase2Fetcher(),
                k -> null,
                RecordingNxEvents.noop());
    }
}
