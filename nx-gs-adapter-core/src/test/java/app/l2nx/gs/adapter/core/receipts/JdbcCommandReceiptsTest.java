package app.l2nx.gs.adapter.core.receipts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.gs.adapter.api.spi.capability.NxCommandReceipts;
import app.l2nx.gs.adapter.api.spi.model.receipt.ClaimOutcome;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class JdbcCommandReceiptsTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final Connection con = mock(Connection.class);
    private final PreparedStatement ps = mock(PreparedStatement.class);
    private final JdbcConnectionSource source = mock(JdbcConnectionSource.class);
    private final JdbcCommandReceipts receipts =
            new JdbcCommandReceipts(() -> source, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() throws SQLException {
        when(source.getConnection()).thenReturn(con);
        when(con.prepareStatement(anyString())).thenReturn(ps);
    }

    @Nested
    class OwnTx {

        @ParameterizedTest(name = "{0} rows -> {1}")
        @CsvSource({"1, true", "0, false", "2, false"})
        void markChargedOwnTx_shouldReportWhetherExactlyOneRowMoved(int rows, boolean expected) throws SQLException {
            when(ps.executeUpdate()).thenReturn(rows);

            assertEquals(expected, receipts.markChargedOwnTx("key", 100L, "{}"));
            verify(con).close();
            verify(ps).close();
        }

        @Test
        void markRefundedOwnTx_shouldReportAMiss_whenTheDatabaseFails() throws SQLException {
            when(ps.executeUpdate()).thenThrow(new SQLException("down"));

            assertFalse(receipts.markRefundedOwnTx("key", "{}"));
            verify(con).rollback();
            verify(con).close();
        }

        @Test
        void markFailedOwnTx_shouldReportAMiss_whenTheRowIsAlreadySettled() throws SQLException {
            when(ps.executeUpdate()).thenReturn(0);

            assertFalse(receipts.markFailed(con, "key"));
            assertFalse(receipts.markFailedOwnTx("key"));
        }

        @Test
        void markChargedOwnTx_shouldReportAMiss_whenNoConnectionOpens() throws SQLException {
            when(source.getConnection()).thenThrow(new SQLException("pool exhausted"));

            assertFalse(receipts.markChargedOwnTx("key", 100L));
        }

        @Test
        void markStepsDoneOwnTx_shouldCommitAndRestoreTheLentModes() throws SQLException {
            when(con.getAutoCommit()).thenReturn(true);
            when(con.isReadOnly()).thenReturn(true);
            when(ps.executeUpdate()).thenReturn(1);

            assertTrue(receipts.markStepsDoneOwnTx("key"));

            verify(con).setReadOnly(false);
            verify(con).setAutoCommit(false);
            verify(con).commit();
            verify(con).setAutoCommit(true);
            verify(con).setReadOnly(true);
        }
    }

    @Nested
    class Handback {

        @Test
        void markStepsDoneOwnTx_shouldReportSuccess_whenRestoringModesFailsAfterCommit() throws SQLException {
            when(con.getAutoCommit()).thenReturn(true);
            when(ps.executeUpdate()).thenReturn(1);
            doThrow(new SQLException("restore")).when(con).setAutoCommit(true);

            assertTrue(receipts.markStepsDoneOwnTx("key"));
            verify(con).commit();
            verify(con).close();
        }

        @Test
        void failStale_shouldReturnTheCommittedResult_whenClosingFails() throws SQLException {
            when(ps.executeUpdate()).thenReturn(1);
            doThrow(new SQLException("close")).when(con).close();

            assertTrue(receipts.failStale("key"));
        }

        @Test
        void failStale_shouldThrowTheOriginalFailure_withTheRestoreFailureSuppressed() throws SQLException {
            when(con.getAutoCommit()).thenReturn(true);
            SQLException original = new SQLException("lock timeout");
            when(ps.executeUpdate()).thenThrow(original);
            doThrow(new SQLException("restore")).when(con).setAutoCommit(true);

            SQLException actual = assertThrows(SQLException.class, () -> receipts.failStale("key"));

            assertSame(original, actual);
            assertEquals("restore", actual.getSuppressed()[0].getMessage());
            verify(con).rollback();
            verify(con).close();
        }
    }

    @Nested
    class InTx {

        @Test
        void markOkIfOpen_shouldThrow_whenTheCasMisses() throws SQLException {
            when(ps.executeUpdate()).thenReturn(0);

            SQLException actual =
                    assertThrows(SQLException.class, () -> receipts.markOkIfOpen(con, "key", 1L, "{}", "{}"));

            assertTrue(actual.getMessage().contains("key"), actual.getMessage());
        }

        @Test
        void markOkIfOpen_shouldBindTheClaim_whenStoringIt() throws SQLException {
            when(ps.executeUpdate()).thenReturn(1);

            receipts.markOkIfOpen(con, "key", 7L, "{\"r\":1}", "{\"c\":1}");

            verify(ps).setLong(1, 7L);
            verify(ps).setString(2, "{\"r\":1}");
            verify(ps).setString(3, "{\"c\":1}");
            verify(ps).setTimestamp(eq(4), eq(Timestamp.from(NOW)), any(Calendar.class));
            verify(ps).setString(5, "key");
        }

        @Test
        void markOkIfStarted_shouldReportTheMiss_withoutThrowing() throws SQLException {
            when(ps.executeUpdate()).thenReturn(0);

            assertFalse(receipts.markOkIfStarted(con, "key", "{}"));
        }

        @Test
        void markCharged_shouldNeverTouchTheCallersTransaction() throws SQLException {
            when(ps.executeUpdate()).thenReturn(1);

            receipts.markCharged(con, "key", 5L);

            verify(con, never()).commit();
            verify(con, never()).rollback();
            verify(con, never()).close();
            verify(con, never()).setAutoCommit(anyBoolean());
            verify(source, never()).getConnection();
        }
    }

    @Nested
    class Claim {

        private final PreparedStatement reopen = mock(PreparedStatement.class);
        private final PreparedStatement insert = mock(PreparedStatement.class);
        private final PreparedStatement read = mock(PreparedStatement.class);
        private final ResultSet row = mock(ResultSet.class);

        @BeforeEach
        void statements() throws SQLException {
            when(con.prepareStatement(startsWith("UPDATE"))).thenReturn(reopen);
            when(con.prepareStatement(startsWith("INSERT"))).thenReturn(insert);
            when(con.prepareStatement(startsWith("SELECT"))).thenReturn(read);
            when(read.executeQuery()).thenReturn(row);
        }

        private ClaimOutcome claim() throws SQLException {
            return receipts.claim("key", "CMD", ReceiptClaim.ofClaimJson("{\"v\":1}"));
        }

        private void duplicateOver(String status) throws SQLException {
            when(reopen.executeUpdate()).thenReturn(0);
            when(insert.executeUpdate()).thenThrow(new SQLException("dup", "23505"));
            when(row.next()).thenReturn(true);
            when(row.getString(1)).thenReturn(status);
            when(row.getString(2)).thenReturn("{\"r\":1}");
        }

        @Test
        void claim_shouldReopenWithoutInserting_whenTheKeyFailed() throws SQLException {
            when(reopen.executeUpdate()).thenReturn(1);

            assertEquals(ClaimOutcome.claimed(), claim());

            verify(con).prepareStatement(contains("status='FAILED'"));
            verify(reopen).setString(2, "{\"v\":1}");
            verify(reopen).setTimestamp(eq(3), eq(Timestamp.from(NOW)), any(Calendar.class));
            verify(reopen).setString(4, "key");
            verify(con, never()).prepareStatement(startsWith("INSERT"));
            verify(con).commit();
            verify(con, never()).rollback();
        }

        @Test
        void claim_shouldInsertInATransactionOfItsOwn_whenNoFailedRowWasReopened() throws SQLException {
            when(reopen.executeUpdate()).thenReturn(0);
            when(insert.executeUpdate()).thenReturn(1);

            assertEquals(ClaimOutcome.claimed(), claim());

            InOrder order = inOrder(source, con, reopen, insert);
            order.verify(source).getConnection();
            order.verify(reopen).executeUpdate();
            order.verify(con).commit();
            order.verify(con).close();
            order.verify(source).getConnection();
            order.verify(insert).executeUpdate();
            order.verify(con).commit();
            order.verify(con).close();
            verify(con, never()).rollback();
        }

        private void secondReopenMissesOver(@Nullable String statusAfter) throws SQLException {
            duplicateOver("FAILED");
            when(reopen.executeUpdate()).thenReturn(0, 0);
            when(row.next()).thenReturn(true, statusAfter != null);
            when(row.getString(1)).thenReturn("FAILED", statusAfter);
            when(row.getString(2)).thenReturn(null, "{\"r\":2}");
        }

        @Test
        void claim_shouldReopenInATransactionOfItsOwn_whenTheInsertLostTheRaceToAClaimThatFailedSince()
                throws SQLException {
            duplicateOver("FAILED");
            when(reopen.executeUpdate()).thenReturn(0, 1);

            assertEquals(ClaimOutcome.claimed(), claim());

            InOrder order = inOrder(source, con, reopen, insert, read);
            order.verify(reopen).executeUpdate();
            order.verify(insert).executeUpdate();
            order.verify(con).rollback();
            order.verify(con).close();
            order.verify(source).getConnection();
            order.verify(read).executeQuery();
            order.verify(con).commit();
            order.verify(con).close();
            order.verify(source).getConnection();
            order.verify(reopen).executeUpdate();
            order.verify(con).commit();
            verify(reopen, times(2)).executeUpdate();
            verify(reopen, times(2)).setString(2, "{\"v\":1}");
            verify(reopen, times(2)).setString(4, "key");
        }

        @Test
        void claim_shouldReplayTheStoredResult_whenAnotherClaimReopenedTheFailedRowFirstAndCompleted()
                throws SQLException {
            secondReopenMissesOver("OK");

            assertEquals(ClaimOutcome.replayOk("{\"r\":2}"), claim());

            verify(read, times(2)).executeQuery();
            verify(source, times(5)).getConnection();
        }

        @ParameterizedTest(name = "now {0}")
        @ValueSource(strings = {"STARTED", "CHARGED", "FAILED"})
        @NullSource
        void claim_shouldRefuseAsInFlight_whenAnotherClaimReopenedTheFailedRowFirst(@Nullable String statusAfter)
                throws SQLException {
            secondReopenMissesOver(statusAfter);

            assertEquals(ClaimOutcome.inFlight(), claim());

            verify(reopen, times(2)).executeUpdate();
            verify(read, times(2)).executeQuery();
        }

        @ParameterizedTest(name = "stored {0}")
        @ValueSource(strings = {"STARTED", "CHARGED", "GARBAGE"})
        void claim_shouldReadBackWithoutReopening_whenTheInsertHitsAConcurrentClaim(String status) throws SQLException {
            duplicateOver(status);

            assertEquals(ClaimOutcome.inFlight(), claim());

            InOrder order = inOrder(source, con, reopen, insert, read);
            order.verify(reopen).executeUpdate();
            order.verify(insert).executeUpdate();
            order.verify(con).rollback();
            order.verify(source).getConnection();
            order.verify(read).executeQuery();
            verify(reopen).executeUpdate();
            verify(source, times(3)).getConnection();
        }

        @Test
        void claim_shouldReplayTheStoredResult_whenTheInsertHitsACompletedClaim() throws SQLException {
            duplicateOver("OK");

            assertEquals(ClaimOutcome.replayOk("{\"r\":1}"), claim());
        }

        @Test
        void claim_shouldRefuseAsInFlight_whenTheDuplicateRowCannotBeRead() throws SQLException {
            when(reopen.executeUpdate()).thenReturn(0);
            when(insert.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException("dup"));

            assertEquals(ClaimOutcome.inFlight(), claim());
        }

        @Test
        void claim_shouldRethrow_whenTheInsertFailsForAnotherReason() throws SQLException {
            when(reopen.executeUpdate()).thenReturn(0);
            when(insert.executeUpdate()).thenThrow(new SQLException("disk full", "53100"));

            assertThrows(SQLException.class, this::claim);
            verify(read, never()).executeQuery();
        }

        @Test
        void claim_shouldRethrowWithoutInserting_whenTheReopenFails() throws SQLException {
            when(reopen.executeUpdate()).thenThrow(new SQLException("deadlock", "40001"));

            assertThrows(SQLException.class, this::claim);
            verify(con, never()).prepareStatement(startsWith("INSERT"));
            verify(con).rollback();
        }

        @ParameterizedTest(name = "SQLState {0}")
        @ValueSource(strings = {"23000", "23505"})
        void isIntegrityViolation_shouldAcceptSqlStateClass23(String state) {
            assertTrue(JdbcCommandReceipts.isIntegrityViolation(new SQLException("dup", state)));
        }
    }

    @Nested
    class OutcomeForExisting {

        @Test
        void outcomeForExisting_shouldReplayWithStoredJson_whenStatusOk() {
            ClaimOutcome actual = JdbcCommandReceipts.outcomeForExisting("OK", "{\"mailId\":7}");

            assertEquals(ClaimOutcome.Kind.REPLAY_OK, actual.getKind());
            assertEquals("{\"mailId\":7}", actual.getResultJson());
        }

        @ParameterizedTest
        @ValueSource(strings = {"STARTED", "CHARGED", "GARBAGE"})
        @NullSource
        void outcomeForExisting_shouldRefuseAsInFlight_whenStatusIsNotOk(String status) {
            ClaimOutcome actual = JdbcCommandReceipts.outcomeForExisting(status, "{\"mailId\":7}");

            assertEquals(ClaimOutcome.Kind.IN_FLIGHT, actual.getKind());
            assertNull(actual.getResultJson());
        }
    }

    @Nested
    class OutcomeForLookup {

        @Test
        void outcomeForLookup_shouldLetTheCommandRun_whenReceiptFailed() {
            assertNull(JdbcCommandReceipts.outcomeForLookup("FAILED", null));
        }

        @Test
        void outcomeForLookup_shouldReplay_whenReceiptOk() {
            ClaimOutcome actual = JdbcCommandReceipts.outcomeForLookup("OK", "{\"mailId\":7}");

            assertEquals(ClaimOutcome.replayOk("{\"mailId\":7}"), actual);
        }

        @ParameterizedTest
        @ValueSource(strings = {"STARTED", "CHARGED"})
        void outcomeForLookup_shouldReportInFlight_whenReceiptOpen(String status) {
            assertEquals(ClaimOutcome.inFlight(), JdbcCommandReceipts.outcomeForLookup(status, null));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Readiness {

        private final NxCommandReceipts unmigrated = new JdbcCommandReceipts(
                () -> {
                    throw new IllegalStateException("not migrated");
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        Stream<Arguments> everyOperation() {
            NxCommandReceipts r = unmigrated;
            List<Executable> calls = Arrays.asList(
                    () -> r.claim("k", "C", ReceiptClaim.ofClaimJson("{}")),
                    () -> r.lookup("k"),
                    () -> r.markCharged(con, "k", 1L),
                    () -> r.markChargedOwnTx("k", 1L),
                    () -> r.markChargedOwnTx("k", 1L, "{}"),
                    () -> r.markRefundedOwnTx("k", "{}"),
                    () -> r.amendOpenClaimOwnTx("k", "{}", "{}"),
                    () -> r.amendCompletedClaimOwnTx("k", "{}", "{}"),
                    () -> r.markStepsDoneOwnTx("k"),
                    () -> r.markOk(con, "k", 1L, "{}"),
                    () -> r.markOkOwnTx("k", 1L, "{}"),
                    () -> r.markOkIfStarted(con, "k", "{}"),
                    () -> r.markOkIfOpen(con, "k", 1L, "{}"),
                    () -> r.markOkIfOpen(con, "k", 1L, "{}", "{}"),
                    () -> r.markFailedInTx(con, "k"),
                    () -> r.clearMailId(con, "k"),
                    () -> r.markFailed(con, "k"),
                    () -> r.markFailedOwnTx("k"),
                    () -> r.failStale("k"),
                    () -> r.insertCompleted("k", "C", 1, "{}"),
                    () -> r.pruneOld(Collections.<String>emptyList()),
                    () -> r.status("k"),
                    () -> r.claimJson("k"),
                    () -> r.nonTerminalCommands(),
                    () -> r.nonTerminal("C"),
                    () -> r.startedOlderThan("C", Duration.ofMinutes(1)),
                    () -> r.okWithOpenSteps("C"));
            Stream.Builder<Arguments> out = Stream.builder();
            for (int i = 0; i < calls.size(); i++) {
                out.add(Arguments.of(i, calls.get(i)));
            }
            return out.build();
        }

        @ParameterizedTest(name = "operation #{0}")
        @MethodSource("everyOperation")
        void everyOperation_shouldThrow_whenTheSchemaIsNotMigrated(int index, Executable call) throws SQLException {
            assertThrows(IllegalStateException.class, call);
            verify(con, never()).prepareStatement(anyString());
            verify(ps, never()).setInt(anyInt(), anyInt());
        }
    }
}
