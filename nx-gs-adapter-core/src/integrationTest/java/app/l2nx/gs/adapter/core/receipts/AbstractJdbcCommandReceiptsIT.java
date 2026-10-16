package app.l2nx.gs.adapter.core.receipts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.l2nx.gs.adapter.api.spi.model.receipt.ClaimOutcome;
import app.l2nx.gs.adapter.api.spi.model.receipt.CompletedReceipt;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptRow;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptStatus;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.db.migrator.liquibase5.Liquibase5SchemaMigrator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

@Tag("integration")
abstract class AbstractJdbcCommandReceiptsIT {

    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");
    private static final String CMD = "SEND_MAIL";
    private static TimeZone savedZone;

    private final MutableClock clock = new MutableClock(T0);
    private DriverSource source;
    private JdbcCommandReceipts receipts;

    protected abstract JdbcDatabaseContainer<?> container();

    protected boolean mysqlFamily() {
        return false;
    }

    /** A zone far from UTC proves the stored instants do not depend on the JVM default. */
    @BeforeAll
    static void farFromUtc() {
        savedZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(savedZone);
    }

    @BeforeEach
    void migrateAndClear() throws Exception {
        JdbcDatabaseContainer<?> c = container();
        source = new DriverSource(c.getJdbcUrl(), c.getUsername(), c.getPassword());
        new Liquibase5SchemaMigrator().migrate(source);
        try (Connection con = source.getConnection();
                Statement st = con.createStatement()) {
            st.execute("DELETE FROM " + JdbcCommandReceipts.TABLE);
        }
        receipts = new JdbcCommandReceipts(() -> source, clock);
    }

    private void claimed(String key) throws SQLException {
        assertEquals(ClaimOutcome.claimed(), receipts.claim(key, CMD, ReceiptClaim.ofClaimJson("{\"v\":0}")));
    }

    private String text(String sql, String key) throws SQLException {
        try (Connection con = source.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Nested
    class Claim {

        @Test
        void claim_shouldStoreEveryClaimField() throws SQLException {
            assertEquals(ClaimOutcome.claimed(), receipts.claim("k1", CMD, ReceiptClaim.purchase(10, 20, 30)));

            ReceiptRow expected = ReceiptRow.builder()
                    .key("k1")
                    .command(CMD)
                    .status(ReceiptStatus.STARTED)
                    .buyerCharacterId(10)
                    .sellerCharacterId(20)
                    .mailId(30)
                    .build();
            assertEquals(Collections.singletonList(expected), receipts.nonTerminal(CMD));
        }

        @Test
        void claim_shouldRefuseAsInFlight_whenTheKeyIsOpen() throws SQLException {
            claimed("k1");

            assertEquals(ClaimOutcome.inFlight(), receipts.claim("k1", CMD, ReceiptClaim.ofClaimJson("{}")));
        }

        @Test
        void claim_shouldReplayTheStoredResult_whenTheKeyCompleted() throws SQLException {
            claimed("k1");
            assertTrue(receipts.markOkOwnTx("k1", 5L, "{\"mailId\":7}"));

            assertEquals(
                    ClaimOutcome.replayOk("{\"mailId\":7}"), receipts.claim("k1", CMD, ReceiptClaim.ofClaimJson("{}")));
        }

        @Test
        void claim_shouldReopenAndReset_whenTheKeyFailed() throws SQLException {
            receipts.claim("k1", CMD, ReceiptClaim.purchase(10, 20, 30));
            assertTrue(receipts.markChargedOwnTx("k1", 99L));
            assertTrue(receipts.markFailedOwnTx("k1"));
            clock.advance(Duration.ofHours(1));

            assertEquals(ClaimOutcome.claimed(), receipts.claim("k1", CMD, ReceiptClaim.ofClaimJson("{\"v\":2}")));

            ReceiptRow actual = receipts.nonTerminal(CMD).get(0);
            assertEquals(ReceiptStatus.STARTED, actual.getStatus());
            assertNull(actual.getChargedAdena());
            assertNull(actual.getMailId());
            assertEquals("{\"v\":2}", actual.getClaimJson());
            assertEquals(10, actual.getBuyerCharacterId());
            assertTrue(receipts.startedOlderThan(CMD, Duration.ofMinutes(59)).isEmpty(), "created_at was reset");
        }

        @Test
        void lookup_shouldLetTheCommandRun_whenMissingOrFailed() throws SQLException {
            assertNull(receipts.lookup("missing"));
            claimed("k1");
            receipts.failStale("k1");

            assertNull(receipts.lookup("k1"));
        }

        @Test
        void lookup_shouldReportInFlight_whenOpen() throws SQLException {
            claimed("k1");

            assertEquals(ClaimOutcome.inFlight(), receipts.lookup("k1"));
        }
    }

    @Nested
    class CallerTransaction {

        @Test
        void markCharged_shouldRollBackWithTheCaller() throws SQLException {
            claimed("k1");
            try (Connection con = source.getConnection()) {
                con.setAutoCommit(false);
                receipts.markCharged(con, "k1", 50L);
                con.rollback();
            }

            assertEquals(ReceiptStatus.STARTED, receipts.status("k1"));
        }

        @Test
        void markCharged_shouldCommitWithTheCaller() throws SQLException {
            claimed("k1");
            try (Connection con = source.getConnection()) {
                con.setAutoCommit(false);
                receipts.markCharged(con, "k1", 50L);
                con.commit();
            }

            assertEquals(ReceiptStatus.CHARGED, receipts.status("k1"));
            assertEquals(50L, receipts.nonTerminal(CMD).get(0).getChargedAdena());
        }

        @Test
        void markCharged_shouldThrow_whenTheRowIsMissing() throws SQLException {
            try (Connection con = source.getConnection()) {
                assertThrows(SQLException.class, () -> receipts.markCharged(con, "missing", 1L));
            }
        }

        @Test
        void markOkIfOpen_shouldRollBackWithTheCaller() throws SQLException {
            claimed("k1");
            try (Connection con = source.getConnection()) {
                con.setAutoCommit(false);
                receipts.markOkIfOpen(con, "k1", 7L, "{\"r\":1}", "{\"c\":1}");
                con.rollback();
            }

            assertEquals(ReceiptStatus.STARTED, receipts.status("k1"));
            assertEquals("{\"v\":0}", receipts.claimJson("k1"));
        }

        @Test
        void markOkIfOpen_shouldSettleWithResultAndClaim() throws SQLException {
            claimed("k1");
            try (Connection con = source.getConnection()) {
                receipts.markOkIfOpen(con, "k1", 7L, "{\"r\":1}", "{\"c\":1}");
            }

            assertEquals(ClaimOutcome.replayOk("{\"r\":1}"), receipts.lookup("k1"));
            assertEquals("{\"c\":1}", receipts.claimJson("k1"));
        }

        @Test
        void markOkIfOpen_shouldThrow_whenAlreadySettled() throws SQLException {
            claimed("k1");
            receipts.markOkOwnTx("k1", 1L, "{}");
            try (Connection con = source.getConnection()) {
                assertThrows(SQLException.class, () -> receipts.markOkIfOpen(con, "k1", 1L, "{}"));
            }
        }

        @Test
        void markOkIfStarted_shouldMoveOnlyAStartedReceipt() throws SQLException {
            claimed("k1");
            claimed("k2");
            receipts.markChargedOwnTx("k2", 1L);
            try (Connection con = source.getConnection()) {
                assertTrue(receipts.markOkIfStarted(con, "k1", "{\"r\":1}"));
                assertFalse(receipts.markOkIfStarted(con, "k2", "{\"r\":2}"));
            }

            assertEquals(ReceiptStatus.OK, receipts.status("k1"));
            assertEquals(ReceiptStatus.CHARGED, receipts.status("k2"));
        }

        @Test
        void markFailedInTx_shouldReleaseTheKeyAndClearTheClaim() throws SQLException {
            receipts.claim("k1", CMD, ReceiptClaim.purchase(1, 2, 3));
            try (Connection con = source.getConnection()) {
                receipts.markFailedInTx(con, "k1");
            }

            assertEquals(ReceiptStatus.FAILED, receipts.status("k1"));
            assertNull(text("SELECT mail_id FROM " + JdbcCommandReceipts.TABLE + " WHERE correlation_id=?", "k1"));
            assertNull(receipts.claimJson("k1"));
        }

        @Test
        void clearMailId_shouldKeepTheStatus_andRollBackWithTheCaller() throws SQLException {
            receipts.claim("k1", CMD, ReceiptClaim.purchase(1, 2, 3));
            receipts.markChargedOwnTx("k1", 10L);
            try (Connection con = source.getConnection()) {
                con.setAutoCommit(false);
                receipts.clearMailId(con, "k1");
                con.rollback();
            }
            assertEquals("3", text("SELECT mail_id FROM " + JdbcCommandReceipts.TABLE + " WHERE correlation_id=?", "k1"));

            try (Connection con = source.getConnection()) {
                receipts.clearMailId(con, "k1");
            }

            assertEquals(ReceiptStatus.CHARGED, receipts.status("k1"));
            assertNull(text("SELECT mail_id FROM " + JdbcCommandReceipts.TABLE + " WHERE correlation_id=?", "k1"));
        }

        @Test
        void markFailed_shouldReportTheMiss_whenAlreadySettled() throws SQLException {
            claimed("k1");
            receipts.markOkOwnTx("k1", 1L, "{}");
            try (Connection con = source.getConnection()) {
                assertFalse(receipts.markFailed(con, "k1"));
            }
            assertEquals(ReceiptStatus.OK, receipts.status("k1"));
        }
    }

    @Nested
    class OwnTransaction {

        @Test
        void markChargedOwnTx_shouldMoveOnlyFromStarted_whenStoringTheClaim() throws SQLException {
            claimed("k1");

            assertTrue(receipts.markChargedOwnTx("k1", 40L, "{\"v\":1}"));
            assertFalse(receipts.markChargedOwnTx("k1", 41L, "{\"v\":2}"));

            assertEquals("{\"v\":1}", receipts.claimJson("k1"));
            assertEquals(40L, receipts.nonTerminal(CMD).get(0).getChargedAdena());
        }

        @Test
        void markChargedOwnTx_shouldReportAMiss_whenTheRowIsMissing() {
            assertFalse(receipts.markChargedOwnTx("missing", 1L));
        }

        @Test
        void markRefundedOwnTx_shouldReturnACharge_toStartedWithZero() throws SQLException {
            claimed("k1");
            assertFalse(receipts.markRefundedOwnTx("k1", "{\"refund\":1}"));
            receipts.markChargedOwnTx("k1", 40L, "{\"v\":1}");

            assertTrue(receipts.markRefundedOwnTx("k1", "{\"refund\":1}"));

            ReceiptRow actual = receipts.nonTerminal(CMD).get(0);
            assertEquals(ReceiptStatus.STARTED, actual.getStatus());
            assertEquals(0L, actual.getChargedAdena());
            assertEquals("{\"refund\":1}", actual.getClaimJson());
        }

        @Test
        void amendOpenClaimOwnTx_shouldApplyOnlyOverTheExpectedClaim() throws SQLException {
            claimed("k1");

            assertFalse(receipts.amendOpenClaimOwnTx("k1", "{\"stale\":1}", "{\"v\":1}"));
            assertTrue(receipts.amendOpenClaimOwnTx("k1", "{\"v\":0}", "{\"v\":1}"));

            assertEquals("{\"v\":1}", receipts.claimJson("k1"));
        }

        @Test
        void amendCompletedClaimOwnTx_shouldApplyOnlyToAnOkReceipt() throws SQLException {
            claimed("k1");
            assertFalse(receipts.amendCompletedClaimOwnTx("k1", "{\"v\":0}", "{\"v\":1}"));
            receipts.markOkOwnTx("k1", 1L, "{}");

            assertTrue(receipts.amendCompletedClaimOwnTx("k1", "{\"v\":0}", "{\"v\":1}"));
            assertEquals("{\"v\":1}", receipts.claimJson("k1"));
        }

        @Test
        void markStepsDoneOwnTx_shouldMarkAnOkReceiptOnce() throws SQLException {
            claimed("k1");
            assertFalse(receipts.markStepsDoneOwnTx("k1"));
            receipts.markOkOwnTx("k1", 1L, "{}");

            assertTrue(receipts.markStepsDoneOwnTx("k1"));
            assertFalse(receipts.markStepsDoneOwnTx("k1"));
            assertTrue(receipts.okWithOpenSteps(CMD).isEmpty());
        }

        @Test
        void failStale_shouldReleaseOnlyAStartedReceipt() throws SQLException {
            claimed("k1");
            claimed("k2");
            receipts.markChargedOwnTx("k2", 1L);

            assertTrue(receipts.failStale("k1"));
            assertFalse(receipts.failStale("k2"));
            assertEquals(ReceiptStatus.FAILED, receipts.status("k1"));
        }

        @Test
        void insertCompleted_shouldStoreAnOkReceipt_andRefuseADuplicate() throws SQLException {
            receipts.insertCompleted("k1", CMD, 77, "{\"debt\":1}");

            assertEquals(ReceiptStatus.OK, receipts.status("k1"));
            assertEquals(
                    "77", text("SELECT mail_id FROM " + JdbcCommandReceipts.TABLE + " WHERE correlation_id=?", "k1"));
            assertThrows(SQLException.class, () -> receipts.insertCompleted("k1", CMD, 78, "{}"));
        }

        @Test
        void ownTx_shouldReturnTheConnectionWithItsLentModes() throws SQLException {
            claimed("k1");
            source.lendReadOnly = true;

            assertTrue(receipts.markOkOwnTx("k1", 1L, "{}"));

            assertTrue(source.lastReturnedReadOnly);
            assertTrue(source.lastReturnedAutoCommit);
        }
    }

    @Nested
    class Queries {

        @Test
        void nonTerminalCommands_shouldListCommandsWithOpenReceipts() throws SQLException {
            claimed("k1");
            receipts.claim("k2", "OTHER", ReceiptClaim.ofClaimJson("{}"));
            receipts.claim("k3", "DONE", ReceiptClaim.ofClaimJson("{}"));
            receipts.markOkOwnTx("k3", 0L, "{}");

            List<String> actual = receipts.nonTerminalCommands();
            Collections.sort(actual);
            assertEquals(Arrays.asList("OTHER", CMD), actual);
        }

        @Test
        void nonTerminal_shouldListOpenReceiptsOldestFirst() throws SQLException {
            claimed("k2");
            clock.advance(Duration.ofSeconds(1));
            claimed("k1");
            receipts.markChargedOwnTx("k1", 3L);
            clock.advance(Duration.ofSeconds(1));
            claimed("k3");
            receipts.failStale("k3");

            assertEquals(Arrays.asList("k2", "k1"), keys(receipts.nonTerminal(CMD)));
        }

        @Test
        void startedOlderThan_shouldListOnlyStartedReceiptsPastTheAge() throws SQLException {
            claimed("old");
            claimed("charged");
            receipts.markChargedOwnTx("charged", 1L);
            clock.advance(Duration.ofMinutes(10));
            claimed("fresh");

            assertEquals(Collections.singletonList("old"), keys(receipts.startedOlderThan(CMD, Duration.ofMinutes(5))));
        }

        @Test
        void okWithOpenSteps_shouldCarryTheCompletionInstant() throws SQLException {
            claimed("k1");
            clock.advance(Duration.ofMinutes(3));
            receipts.markOkOwnTx("k1", 1L, "{}");

            List<CompletedReceipt> actual = receipts.okWithOpenSteps(CMD);

            assertEquals(1, actual.size());
            assertEquals(T0.plus(Duration.ofMinutes(3)), actual.get(0).getCompletedAt());
            assertEquals("{\"v\":0}", actual.get(0).getClaimJson());
        }

        @Test
        void status_shouldBeNull_whenMissing() throws SQLException {
            assertNull(receipts.status("missing"));
            assertNull(receipts.claimJson("missing"));
        }
    }

    @Nested
    class Prune {

        @Test
        void pruneOld_shouldDeleteOnlyOldTerminalReceipts_keepingOpenStepsOfTrackedCommands() throws SQLException {
            claimed("okOld");
            receipts.markOkOwnTx("okOld", 1L, "{}");
            receipts.markStepsDoneOwnTx("okOld");
            claimed("failedOld");
            receipts.failStale("failedOld");
            claimed("openOld");
            claimed("stepsOpenOld");
            receipts.markOkOwnTx("stepsOpenOld", 1L, "{}");
            receipts.claim("untrackedOld", "UNTRACKED", ReceiptClaim.ofClaimJson("{}"));
            receipts.markOkOwnTx("untrackedOld", 1L, "{}");
            clock.advance(Duration.ofDays(31));
            claimed("okNew");
            receipts.markOkOwnTx("okNew", 1L, "{}");

            assertEquals(3, receipts.pruneOld(Collections.singletonList(CMD)));

            assertNull(receipts.status("okOld"));
            assertNull(receipts.status("failedOld"));
            assertNull(receipts.status("untrackedOld"));
            assertEquals(ReceiptStatus.STARTED, receipts.status("openOld"));
            assertEquals(ReceiptStatus.OK, receipts.status("stepsOpenOld"));
            assertEquals(ReceiptStatus.OK, receipts.status("okNew"));
        }

        @Test
        void pruneOld_shouldDeleteOpenStepRows_whenNoCommandIsTracked() throws SQLException {
            claimed("stepsOpenOld");
            receipts.markOkOwnTx("stepsOpenOld", 1L, "{}");
            clock.advance(Duration.ofDays(31));

            assertEquals(1, receipts.pruneOld(Collections.<String>emptyList()));
        }
    }

    @Nested
    class LegacyHostTable {

        @Test
        void store_shouldReadHostWrittenTimes_asTrueInstants_afterTheUtcConversion() throws Exception {
            assumeTrue(mysqlFamily(), "TIMESTAMP session-zone conversion exists only on MariaDB/MySQL");
            long writtenAtEpochSec;
            try (Connection con = source.getConnection();
                    Statement st = con.createStatement()) {
                for (String table : new String[] {
                    "l2nx_offline_sessions", JdbcCommandReceipts.TABLE, "l2nx_databasechangelog", "l2nx_databasechangeloglock"
                }) {
                    st.execute("DROP TABLE IF EXISTS " + table);
                }
                st.execute("SET time_zone = '+03:00'");
                st.execute("CREATE TABLE " + JdbcCommandReceipts.TABLE + " (correlation_id VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + " command VARCHAR(64) NOT NULL, buyer_char_id INT NULL, seller_char_id INT NULL,"
                        + " status VARCHAR(16) NOT NULL, charged_adena BIGINT NULL, mail_id INT NULL, claim_json TEXT NULL,"
                        + " result_json TEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " completed_at TIMESTAMP NULL DEFAULT NULL, steps_done_at TIMESTAMP NULL DEFAULT NULL)");
                st.execute("INSERT INTO " + JdbcCommandReceipts.TABLE + " (correlation_id, command, status, claim_json,"
                        + " completed_at) VALUES ('done', '" + CMD + "', 'OK', '{}', CURRENT_TIMESTAMP)");
                st.execute("INSERT INTO " + JdbcCommandReceipts.TABLE
                        + " (correlation_id, command, status) VALUES ('open', '" + CMD + "', 'STARTED')");
                try (ResultSet rs = st.executeQuery("SELECT UNIX_TIMESTAMP()")) {
                    rs.next();
                    writtenAtEpochSec = rs.getLong(1);
                }
            }

            new Liquibase5SchemaMigrator().migrate(source);

            Instant completedAt = receipts.okWithOpenSteps(CMD).get(0).getCompletedAt();
            assertTrue(Math.abs(completedAt.getEpochSecond() - writtenAtEpochSec) <= 1, completedAt.toString());
            clock.set(Instant.ofEpochSecond(writtenAtEpochSec).plus(Duration.ofMinutes(4)));
            assertTrue(receipts.startedOlderThan(CMD, Duration.ofMinutes(5)).isEmpty());
            clock.set(Instant.ofEpochSecond(writtenAtEpochSec).plus(Duration.ofMinutes(6)));
            assertEquals(Collections.singletonList("open"), keys(receipts.startedOlderThan(CMD, Duration.ofMinutes(5))));
        }
    }

    private static List<String> keys(List<ReceiptRow> rows) {
        return rows.stream().map(ReceiptRow::getKey).collect(Collectors.toList());
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        void set(Instant instant) {
            now = instant;
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

    static final class DriverSource implements JdbcConnectionSource {
        private final String url;
        private final String user;
        private final String password;
        volatile boolean lendReadOnly;
        volatile boolean lastReturnedReadOnly;
        volatile boolean lastReturnedAutoCommit;

        DriverSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public String name() {
            return "it";
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection raw = DriverManager.getConnection(url, user, password);
            raw.setReadOnly(lendReadOnly);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        if ("close".equals(method.getName()) && !raw.isClosed()) {
                            lastReturnedReadOnly = raw.isReadOnly();
                            lastReturnedAutoCommit = raw.getAutoCommit();
                        }
                        try {
                            return method.invoke(raw, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }
}
