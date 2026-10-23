package app.l2nx.gs.db.migrator.liquibase5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

@Tag("integration")
abstract class AbstractLiquibase5SchemaMigratorIT {

    private static final String RECEIPTS = "l2nx_command_receipts";
    private static final String SESSIONS = "l2nx_offline_sessions";
    private static final String CHANGELOG = "l2nx_databasechangelog";
    private static final String LOCK = "l2nx_databasechangeloglock";

    private static final String HOST_RECEIPTS_DDL = "CREATE TABLE l2nx_command_receipts ("
            + " correlation_id VARCHAR(36) NOT NULL PRIMARY KEY,"
            + " command VARCHAR(64) NOT NULL,"
            + " buyer_char_id INT NULL,"
            + " seller_char_id INT NULL,"
            + " status VARCHAR(16) NOT NULL,"
            + " charged_adena BIGINT NULL,"
            + " mail_id INT NULL,"
            + " claim_json TEXT NULL,"
            + " result_json TEXT NULL,"
            + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " completed_at TIMESTAMP NULL DEFAULT NULL)";

    protected abstract JdbcDatabaseContainer<?> container();

    protected boolean mysqlFamily() {
        return false;
    }

    private int changesetCount() {
        return mysqlFamily() ? 8 : 7;
    }

    protected TestConnectionSource source() {
        JdbcDatabaseContainer<?> c = container();
        return new TestConnectionSource(c.getJdbcUrl(), c.getUsername(), c.getPassword());
    }

    @BeforeEach
    void dropAdapterTables() throws SQLException {
        try (Connection c = source().getConnection();
                Statement st = c.createStatement()) {
            for (String table : new String[] {SESSIONS, RECEIPTS, CHANGELOG, LOCK}) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
        }
    }

    @Nested
    class Apply {

        @Test
        void migrate_shouldCreateEveryAdapterTable_whenDatabaseIsEmpty() throws Exception {
            new Liquibase5SchemaMigrator().migrate(source());

            Map<String, String> actual = changelog();
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("0001-command-receipts-table", "EXECUTED");
            expected.put("0002-command-receipts-steps-done-at", "EXECUTED");
            expected.put("0003-command-receipts-open-steps-index", "EXECUTED");
            expected.put("0004-offline-sessions-table", "EXECUTED");
            if (mysqlFamily()) {
                expected.put("0007-command-receipts-utc-datetime", "EXECUTED");
            }
            expected.put("0008-offline-sessions-return-threshold", "EXECUTED");
            expected.put("0009-offline-sessions-master-account-activity-started-index", "EXECUTED");
            expected.put("0010-offline-sessions-character-activity-started-index", "EXECUTED");
            assertEquals(expected, actual);
            assertTrue(columnExists(RECEIPTS, "steps_done_at"));
        }

        @Test
        void migrate_shouldChangeNothing_whenRunOnCurrentSchema() throws Exception {
            new Liquibase5SchemaMigrator().migrate(source());
            Map<String, String> expected = changelog();

            new Liquibase5SchemaMigrator().migrate(source());

            assertEquals(expected, changelog());
            assertEquals(changesetCount(), count("SELECT COUNT(*) FROM " + CHANGELOG));
        }

        @Test
        void migrate_shouldUseOwnChangelogTables_whenHostConfiguresLiquibaseViaSystemProperties() throws Exception {
            System.setProperty("liquibase.databaseChangelogTableName", "host_changelog");
            System.setProperty("liquibase.databaseChangelogLockTableName", "host_changelog_lock");
            try {
                new Liquibase5SchemaMigrator().migrate(source());
            } finally {
                System.clearProperty("liquibase.databaseChangelogTableName");
                System.clearProperty("liquibase.databaseChangelogLockTableName");
            }

            assertEquals(changesetCount(), count("SELECT COUNT(*) FROM " + CHANGELOG));
            assertFalse(tableExists("host_changelog"));
        }

        @Test
        void migrate_shouldReturnConnectionAsLent_whenPoolHandsOutReadOnlyConnection() throws Exception {
            TestConnectionSource source = source();
            source.lendReadOnly = true;

            new Liquibase5SchemaMigrator().migrate(source);

            assertTrue(source.lastConnection.isClosed());
            assertTrue(source.lastAutoCommitOnClose);
        }
    }

    @Nested
    class OfflineSessions {

        @BeforeEach
        void migrate() throws Exception {
            new Liquibase5SchemaMigrator().migrate(source());
        }

        @Test
        void insert_shouldAllowManyClosedSessions_whenActiveColumnsAreNull() throws Exception {
            insertSession(7, null, null);
            insertSession(7, null, null);

            assertEquals(2, count("SELECT COUNT(*) FROM " + SESSIONS));
        }

        @Test
        void insert_shouldRejectSecondActiveSession_whenCharacterAlreadyHasOne() throws Exception {
            insertSession(7, 7, "giran_harbor:1");

            assertThrows(SQLException.class, () -> insertSession(7, 7, "giran_harbor:2"));
        }

        @Test
        void insert_shouldRejectSecondCharacter_whenSpotIsTaken() throws Exception {
            insertSession(7, 7, "giran_harbor:1");

            assertThrows(SQLException.class, () -> insertSession(8, 8, "giran_harbor:1"));
        }

        private void insertSession(int characterId, Integer activeCharacterId, String activeSpotKey)
                throws SQLException {
            String sql = "INSERT INTO " + SESSIONS
                    + " (id, idempotency_key, character_id, active_character_id, activity, account_name, spot_pool,"
                    + " spot_id,"
                    + " active_spot_key,"
                    + " origin_x, origin_y,"
                    + " origin_z,"
                    + " origin_heading,"
                    + " started_at)"
                    + " VALUES"
                    + " (" + uuid() + ", " + uuid() + ", " + characterId + ", " + activeCharacterId
                    + ", 'OFFLINE_FISHING', 'acc', 'giran_harbor', 1, "
                    + (activeSpotKey == null ? "NULL" : "'" + activeSpotKey + "'")
                    + ", 1, 2, 3, 0, CURRENT_TIMESTAMP)";
            execute(sql);
        }
    }

    @Nested
    class Adoption {

        @Test
        void migrate_shouldAdoptHostReceiptsTable_whenItAlreadyHasStepsDoneAt() throws Exception {
            execute(HOST_RECEIPTS_DDL);
            execute("ALTER TABLE " + RECEIPTS + " ADD COLUMN steps_done_at TIMESTAMP NULL DEFAULT NULL");
            execute("CREATE INDEX idx_l2nx_receipts_command_status_steps ON " + RECEIPTS
                    + " (command, status, steps_done_at)");
            insertReceipt();

            new Liquibase5SchemaMigrator().migrate(source());

            Map<String, String> actual = changelog();
            assertEquals("MARK_RAN", actual.get("0001-command-receipts-table"));
            assertEquals("MARK_RAN", actual.get("0002-command-receipts-steps-done-at"));
            assertEquals("MARK_RAN", actual.get("0003-command-receipts-open-steps-index"));
            assertEquals(1, count("SELECT COUNT(*) FROM " + RECEIPTS + " WHERE correlation_id = 'kept'"));
        }

        @Test
        void migrate_shouldAddStepsDoneAt_whenHostReceiptsTableLacksIt() throws Exception {
            execute(HOST_RECEIPTS_DDL);
            insertReceipt();

            new Liquibase5SchemaMigrator().migrate(source());

            Map<String, String> actual = changelog();
            assertEquals("MARK_RAN", actual.get("0001-command-receipts-table"));
            assertEquals("EXECUTED", actual.get("0002-command-receipts-steps-done-at"));
            assertEquals("EXECUTED", actual.get("0003-command-receipts-open-steps-index"));
            assertTrue(columnExists(RECEIPTS, "steps_done_at"));
            assertEquals(1, count("SELECT COUNT(*) FROM " + RECEIPTS + " WHERE correlation_id = 'kept'"));
        }

        private void insertReceipt() throws SQLException {
            execute("INSERT INTO " + RECEIPTS
                    + " (correlation_id, command, status) VALUES ('kept', 'SEND_MAIL', 'OK')");
        }
    }

    @Nested
    class UtcDatetime {

        @Test
        void migrate_shouldConvertHostTimestampsToUtcDatetime_whenSessionZoneIsNotUtc() throws Exception {
            assumeTrue(mysqlFamily(), "TIMESTAMP session-zone conversion exists only on MariaDB/MySQL");
            long insertedAtEpochSec;
            try (Connection c = source().getConnection();
                    Statement st = c.createStatement()) {
                st.execute("SET time_zone = '+03:00'");
                st.execute(HOST_RECEIPTS_DDL);
                st.execute("ALTER TABLE " + RECEIPTS + " ADD COLUMN steps_done_at TIMESTAMP NULL DEFAULT NULL");
                st.execute("CREATE INDEX idx_l2nx_receipts_command_status_steps ON " + RECEIPTS
                        + " (command, status, steps_done_at)");
                st.execute("INSERT INTO " + RECEIPTS + " (correlation_id, command, status, completed_at, steps_done_at)"
                        + " VALUES ('kept', 'SEND_MAIL', 'OK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                try (ResultSet rs = st.executeQuery("SELECT UNIX_TIMESTAMP()")) {
                    rs.next();
                    insertedAtEpochSec = rs.getLong(1);
                }
            }

            new Liquibase5SchemaMigrator().migrate(source());

            assertEquals("EXECUTED", changelog().get("0007-command-receipts-utc-datetime"));
            for (String column : new String[] {"created_at", "completed_at", "steps_done_at"}) {
                assertEquals("datetime", dataType(column), column);
                long storedAsUtc = LocalDateTime.parse(text("SELECT DATE_FORMAT(" + column
                                + ", '%Y-%m-%dT%H:%i:%s') FROM " + RECEIPTS + " WHERE correlation_id = 'kept'"))
                        .toEpochSecond(ZoneOffset.UTC);
                assertTrue(Math.abs(storedAsUtc - insertedAtEpochSec) <= 1, column + " holds UTC wall time");
            }
            assertEquals(
                    1,
                    count("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema ="
                            + " DATABASE() AND index_name = 'idx_l2nx_receipts_command_status_steps'"));
        }

        @Test
        void migrate_shouldRestoreTheSessionZone_afterConverting() throws Exception {
            assumeTrue(mysqlFamily(), "TIMESTAMP session-zone conversion exists only on MariaDB/MySQL");
            execute(HOST_RECEIPTS_DDL);
            execute("ALTER TABLE " + RECEIPTS + " ADD COLUMN steps_done_at TIMESTAMP NULL DEFAULT NULL");
            TestConnectionSource source = source();
            source.sessionTimeZone = "+03:00";

            new Liquibase5SchemaMigrator().migrate(source);

            assertEquals("EXECUTED", changelog().get("0007-command-receipts-utc-datetime"));
            assertEquals("+03:00", source.lastTimeZoneOnClose);
        }

        @Test
        void migrate_shouldMatchFreshColumns_whenConvertingHostTable() throws Exception {
            assumeTrue(mysqlFamily(), "TIMESTAMP session-zone conversion exists only on MariaDB/MySQL");
            new Liquibase5SchemaMigrator().migrate(source());
            String fresh = columnShapes();
            dropAdapterTables();
            execute(HOST_RECEIPTS_DDL);
            execute("ALTER TABLE " + RECEIPTS + " ADD COLUMN steps_done_at TIMESTAMP NULL DEFAULT NULL");

            new Liquibase5SchemaMigrator().migrate(source());

            assertEquals(fresh, columnShapes());
        }

        private String dataType(String column) throws SQLException {
            return text("SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE()"
                    + " AND table_name = '" + RECEIPTS + "' AND column_name = '" + column + "'");
        }

        private String columnShapes() throws SQLException {
            StringBuilder out = new StringBuilder();
            for (String column : new String[] {"created_at", "completed_at", "steps_done_at"}) {
                out.append(text("SELECT CONCAT(column_type, ' ', is_nullable, ' ', COALESCE(column_default, 'none'))"
                                + " FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '"
                                + RECEIPTS + "' AND column_name = '" + column + "'"))
                        .append('|');
            }
            return out.toString();
        }
    }

    @Nested
    class Failures {

        @Test
        void migrate_shouldReportHolderAndKeepLock_whenLockIsStaleAndChangesArePending() throws Exception {
            new Liquibase5SchemaMigrator().migrate(source());
            // An up-to-date schema never takes the lock, so a stale lock only matters with a change pending.
            execute("DROP TABLE " + SESSIONS);
            execute("DELETE FROM " + CHANGELOG + " WHERE id = '0004-offline-sessions-table'");
            execute("UPDATE " + LOCK + " SET locked = TRUE, lockgranted = CURRENT_TIMESTAMP,"
                    + " lockedby = 'dead-host (10.0.0.1)' WHERE id = 1");

            SchemaMigrationException actual = assertThrows(
                    SchemaMigrationException.class, () -> new Liquibase5SchemaMigrator(0L, 1L).migrate(source()));

            assertTrue(actual.getMessage().contains("dead-host (10.0.0.1)"), actual.getMessage());
            assertTrue(actual.getMessage().contains(LOCK), actual.getMessage());
            assertEquals(1, count("SELECT COUNT(*) FROM " + LOCK + " WHERE lockedby = 'dead-host (10.0.0.1)'"));
        }
    }

    protected void execute(String sql) throws SQLException {
        try (Connection c = source().getConnection();
                Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    protected int count(String sql) throws SQLException {
        try (Connection c = source().getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    protected String text(String sql) throws SQLException {
        try (Connection c = source().getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Map<String, String> changelog() throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        try (Connection c = source().getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, exectype FROM " + CHANGELOG + " ORDER BY orderexecuted")) {
            while (rs.next()) {
                rows.put(rs.getString(1), rs.getString(2));
            }
        }
        return rows;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Connection c = source().getConnection();
                Statement st = c.createStatement()) {
            st.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1 = 0")
                    .close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean tableExists(String table) throws SQLException {
        return columnExists(table, "*");
    }

    private static String uuid() {
        return "'" + UUID.randomUUID() + "'";
    }
}
