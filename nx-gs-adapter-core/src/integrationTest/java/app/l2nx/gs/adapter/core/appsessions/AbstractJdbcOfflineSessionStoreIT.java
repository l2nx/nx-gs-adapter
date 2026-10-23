package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.core.appsessions.OfflineSessionStore.InsertResult;
import app.l2nx.gs.adapter.core.appsessions.OfflineSessionStore.PlaceResult;
import app.l2nx.gs.db.migrator.liquibase5.Liquibase5SchemaMigrator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

@Tag("integration")
abstract class AbstractJdbcOfflineSessionStoreIT {

    private static final Instant STARTED = Instant.parse("2026-10-15T12:00:00Z");
    private static TimeZone savedZone;

    private LentConnectionSource source;
    private JdbcOfflineSessionStore store;

    protected abstract JdbcDatabaseContainer<?> container();

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
        source = new LentConnectionSource(c.getJdbcUrl(), c.getUsername(), c.getPassword());
        new Liquibase5SchemaMigrator().migrate(source);
        execute("DELETE FROM " + JdbcOfflineSessionStore.TABLE);
        store = new JdbcOfflineSessionStore(source);
    }

    private static OfflineSession.Builder session(int characterId, int spotId) {
        return OfflineSession.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .characterId(characterId)
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("acc" + characterId)
                .spotPool("harbor")
                .spotId(spotId)
                .originX(-83_000)
                .originY(150_000)
                .originZ(-3_000)
                .originHeading(32_768)
                .startedAt(STARTED);
    }

    @Nested
    class Insert {

        @Test
        void insert_shouldRoundTripEveryColumn() {
            OfflineSession expected = session(7, 1)
                    .masterAccountId(UUID.randomUUID())
                    .hwid("HW-0001")
                    .initiatorIp("2001:db8::1")
                    .plannedMs(3_600_000L)
                    .returnThresholdMs(1_800_000L)
                    .typeState("{\"rod\":101,\"bait\":202}")
                    .build();

            assertEquals(InsertResult.INSERTED, store.insert(expected));

            assertEquals(
                    expected,
                    store.findByIdempotencyKey(expected.getIdempotencyKey()).get());
        }

        @Test
        void insert_shouldRoundTripNulls() {
            OfflineSession expected = session(7, 1).build();

            store.insert(expected);

            OfflineSession actual = store.findOpenByCharacterId(7).get();
            assertEquals(expected, actual);
            assertNull(actual.getMasterAccountId());
            assertNull(actual.getPlannedMs());
            assertNull(actual.getReturnThresholdMs());
            assertNull(actual.getTypeState());
        }

        @Test
        void read_shouldTurnStoredIdentifierPlaceholdersIntoNull() {
            store.insert(session(7, 1).hwid("N/A").initiatorIp("0.0.0.0").build());

            OfflineSession actual = store.findOpenByCharacterId(7).get();

            assertNull(actual.getHwid());
            assertNull(actual.getInitiatorIp());
        }

        @Test
        void insert_shouldStoreUtcWallTime_whenJvmZoneIsNotUtc() throws SQLException {
            store.insert(session(7, 1).build());

            assertTrue(text("SELECT started_at FROM " + JdbcOfflineSessionStore.TABLE)
                    .startsWith("2026-10-15 12:00:00"));
        }

        @Test
        void insert_shouldReportDuplicateKey_whenIdempotencyKeyWasUsed() {
            OfflineSession first = session(7, 1).build();
            store.insert(first);

            InsertResult actual = store.insert(
                    session(8, 2).idempotencyKey(first.getIdempotencyKey()).build());

            assertEquals(InsertResult.DUPLICATE_IDEMPOTENCY_KEY, actual);
        }

        @Test
        void insert_shouldReportCharacterActive_whenCharacterHasAnOpenSession() {
            store.insert(session(7, 1).build());

            assertEquals(
                    InsertResult.CHARACTER_ACTIVE, store.insert(session(7, 2).build()));
        }

        @Test
        void insert_shouldReportSpotTaken_whenAnotherOpenSessionHoldsTheSpot() {
            store.insert(session(7, 1).build());

            assertEquals(InsertResult.SPOT_TAKEN, store.insert(session(8, 1).build()));
        }

        @Test
        void insert_shouldSucceed_whenCharacterAndSpotWereFreedByAClose() {
            OfflineSession first = session(7, 1).build();
            store.insert(first);
            store.close(first.getId(), 1_000L, STARTED.plusSeconds(60), LogoutReason.APP_STOPPED);

            assertEquals(InsertResult.INSERTED, store.insert(session(7, 1).build()));
        }
    }

    @Nested
    class Place {

        @Test
        void place_shouldMoveSpotRecordTheStartAndStoreTheTypeState() {
            OfflineSession row = session(7, 1).typeState("{\"a\":1}").build();
            store.insert(row);
            Instant realStart = STARTED.plusSeconds(3);

            assertEquals(PlaceResult.PLACED, store.place(row.getId(), "harbor", 4, realStart, "{\"a\":2}"));

            OfflineSession actual = store.findOpenByCharacterId(7).get();
            assertEquals(4, actual.getSpotId());
            assertEquals(realStart, actual.getStartedAt());
            assertEquals("{\"a\":2}", actual.getTypeState());
            assertEquals(InsertResult.INSERTED, store.insert(session(8, 1).build()));
        }

        @Test
        void place_shouldKeepTheTypeState_whenNoneIsGiven() {
            OfflineSession row = session(7, 1).typeState("{\"a\":1}").build();
            store.insert(row);

            assertEquals(PlaceResult.PLACED, store.place(row.getId(), "harbor", 4, null, null));

            assertEquals("{\"a\":1}", store.findOpenByCharacterId(7).get().getTypeState());
        }

        @Test
        void place_shouldReturnSpotTaken_whenTheSpotIsHeld() {
            OfflineSession row = session(7, 1).build();
            store.insert(row);
            store.insert(session(8, 4).build());

            assertEquals(PlaceResult.SPOT_TAKEN, store.place(row.getId(), "harbor", 4, null, null));
            assertEquals(1, store.findOpenByCharacterId(7).get().getSpotId());
        }

        @Test
        void place_shouldReturnNotOpen_whenTheSessionIsClosed() {
            OfflineSession row = session(7, 1).build();
            store.insert(row);
            store.close(row.getId(), 0L, STARTED.plusSeconds(60), LogoutReason.APP_STOPPED);

            assertEquals(PlaceResult.NOT_OPEN, store.place(row.getId(), "harbor", 4, null, null));
        }
    }

    @Nested
    class ActiveTime {

        @Test
        void flushActiveMs_shouldNeverLowerAStoredValue() {
            OfflineSession row = session(7, 1).build();
            store.insert(row);
            store.flushActiveMs(Collections.singletonMap(row.getId(), 120_000L));

            store.flushActiveMs(Collections.singletonMap(row.getId(), 60_000L));

            assertEquals(120_000L, store.findOpenByCharacterId(7).get().getActiveMs());
        }

        @Test
        void flushActiveMs_shouldLeaveAClosedRow() {
            OfflineSession row = session(7, 1).build();
            store.insert(row);
            store.close(row.getId(), 60_000L, STARTED.plusSeconds(60), LogoutReason.APP_TIME_UP);

            store.flushActiveMs(Collections.singletonMap(row.getId(), 90_000L));

            assertEquals(
                    60_000L,
                    store.findByIdempotencyKey(row.getIdempotencyKey()).get().getActiveMs());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void close_shouldStoreEndAndFreeCharacterAndSpot() throws SQLException {
            OfflineSession row = session(7, 1).build();
            store.insert(row);
            Instant ended = STARTED.plusSeconds(1_800);

            store.close(row.getId(), 1_800_000L, ended, LogoutReason.APP_LOGIN);

            OfflineSession actual =
                    store.findByIdempotencyKey(row.getIdempotencyKey()).get();
            assertEquals(ended, actual.getEndedAt());
            assertEquals(LogoutReason.APP_LOGIN, actual.getEndReason());
            assertEquals(1_800_000L, actual.getActiveMs());
            assertFalse(store.findOpenByCharacterId(7).isPresent());
            assertEquals(
                    "0",
                    text("SELECT COUNT(*) FROM " + JdbcOfflineSessionStore.TABLE
                            + " WHERE active_character_id IS NOT NULL OR active_spot_key IS NOT NULL"));
        }

        @Test
        void loadOpen_shouldReturnOnlyOpenSessions() {
            OfflineSession open = session(7, 1).build();
            OfflineSession closed = session(8, 2).build();
            store.insert(open);
            store.insert(closed);
            store.close(closed.getId(), 0L, STARTED, LogoutReason.APP_STOPPED);

            List<OfflineSession> actual = store.loadOpen();

            assertEquals(Collections.singletonList(open), actual);
        }

        @Test
        void deleteClosedBefore_shouldDeleteOnlyOldClosedSessions() {
            OfflineSession old = session(7, 1).build();
            OfflineSession recent = session(8, 2).build();
            OfflineSession open = session(9, 3).build();
            store.insert(old);
            store.insert(recent);
            store.insert(open);
            store.close(old.getId(), 0L, STARTED.minusSeconds(40L * 86_400L), LogoutReason.APP_STOPPED);
            store.close(recent.getId(), 0L, STARTED.minusSeconds(86_400L), LogoutReason.APP_STOPPED);

            int actual = store.deleteClosedBefore(STARTED.minusSeconds(30L * 86_400L));

            assertEquals(1, actual);
            assertFalse(store.findByIdempotencyKey(old.getIdempotencyKey()).isPresent());
            assertTrue(store.findByIdempotencyKey(recent.getIdempotencyKey()).isPresent());
            assertTrue(store.findByIdempotencyKey(open.getIdempotencyKey()).isPresent());
        }

        @Test
        void updateTypeState_shouldStoreOnlyWhileOpen() {
            OfflineSession row = session(7, 1).build();
            store.insert(row);

            assertTrue(store.updateTypeState(row.getId(), "{\"fee\":3}"));
            assertEquals("{\"fee\":3}", store.findOpenByCharacterId(7).get().getTypeState());
            store.close(row.getId(), 0L, STARTED, LogoutReason.APP_STOPPED);
            assertFalse(store.updateTypeState(row.getId(), "{}"));
        }

        @Test
        void delete_shouldRemoveARolledBackStart() {
            OfflineSession row = session(7, 1).build();
            store.insert(row);

            store.delete(row.getId());

            assertFalse(store.findByIdempotencyKey(row.getIdempotencyKey()).isPresent());
        }
    }

    @Nested
    class FindLatestStartedByMasterAccount {

        private final UUID master = UUID.fromString("0192f000-0000-7000-8000-000000000001");

        private OfflineSession started(int characterId, UUID id, Instant startedAt) {
            return session(characterId, characterId)
                    .id(id)
                    .masterAccountId(master)
                    .startedAt(startedAt)
                    .build();
        }

        @Test
        void findLatestStartedByMasterAccount_shouldOrderByStartThenGreaterId_openOrClosed() {
            OfflineSession older = started(1, UUID.fromString("0192f000-0000-7000-8000-0000000000ff"), STARTED);
            OfflineSession tieLow =
                    started(2, UUID.fromString("0192f000-0000-7000-8000-000000000001"), STARTED.plusSeconds(60));
            OfflineSession tieHigh =
                    started(3, UUID.fromString("0192f000-0000-7000-8000-000000000002"), STARTED.plusSeconds(60));
            store.insert(older);
            store.insert(tieLow);
            store.insert(tieHigh);
            store.close(tieHigh.getId(), 0L, STARTED.plusSeconds(120), LogoutReason.APP_STOPPED);

            List<OfflineSession> actual = store.findLatestStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING, 3);

            assertEquals(tieHigh.getId(), actual.get(0).getId());
            assertEquals(tieLow.getId(), actual.get(1).getId());
            assertEquals(older.getId(), actual.get(2).getId());
        }

        @Test
        void findLatestStartedByMasterAccount_shouldReturnAtMostTheLimit() {
            store.insert(started(1, UUID.randomUUID(), STARTED));
            store.insert(started(2, UUID.randomUUID(), STARTED.plusSeconds(60)));

            List<OfflineSession> actual = store.findLatestStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING, 1);

            assertEquals(1, actual.size());
            assertEquals(STARTED.plusSeconds(60), actual.get(0).getStartedAt());
        }

        @Test
        void findLatestStartedByMasterAccount_shouldSkipOtherMasterAccountsAndActivities() {
            store.insert(session(1, 1).masterAccountId(UUID.randomUUID()).build());
            store.insert(session(2, 2).build());
            store.insert(session(3, 3)
                    .masterAccountId(master)
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .build());

            assertTrue(store.findLatestStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING, 5)
                    .isEmpty());
        }
    }

    @Nested
    class FindLatestStartedByCharacter {

        private static final int CHARACTER = 7;

        private OfflineSession closedStore(int characterId, UUID id, Instant startedAt) {
            OfflineSession row = session(characterId, characterId)
                    .id(id)
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .startedAt(startedAt)
                    .build();
            store.insert(row);
            store.close(id, 0L, startedAt.plusSeconds(30), LogoutReason.APP_STOPPED);
            return row;
        }

        @Test
        void findLatestStartedByCharacter_shouldOrderByStartThenGreaterId() {
            OfflineSession older =
                    closedStore(CHARACTER, UUID.fromString("0192f000-0000-7000-8000-0000000000ff"), STARTED);
            OfflineSession tieLow = closedStore(
                    CHARACTER, UUID.fromString("0192f000-0000-7000-8000-000000000001"), STARTED.plusSeconds(60));
            OfflineSession tieHigh = closedStore(
                    CHARACTER, UUID.fromString("0192f000-0000-7000-8000-000000000002"), STARTED.plusSeconds(60));

            List<OfflineSession> actual =
                    store.findLatestStartedByCharacter(CHARACTER, ActivityType.OFFLINE_PRIVATE_STORE, 3);

            assertEquals(tieHigh.getId(), actual.get(0).getId());
            assertEquals(tieLow.getId(), actual.get(1).getId());
            assertEquals(older.getId(), actual.get(2).getId());
        }

        @Test
        void findLatestStartedByCharacter_shouldReturnAtMostTheLimit_openOrClosed() {
            closedStore(CHARACTER, UUID.randomUUID(), STARTED);
            OfflineSession open = session(CHARACTER, CHARACTER)
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .startedAt(STARTED.plusSeconds(60))
                    .build();
            store.insert(open);

            List<OfflineSession> actual =
                    store.findLatestStartedByCharacter(CHARACTER, ActivityType.OFFLINE_PRIVATE_STORE, 1);

            assertEquals(1, actual.size());
            assertEquals(open.getId(), actual.get(0).getId());
        }

        @Test
        void findLatestStartedByCharacter_shouldSkipOtherCharactersAndActivities() {
            closedStore(CHARACTER + 1, UUID.randomUUID(), STARTED);
            store.insert(session(CHARACTER, CHARACTER).build());

            assertTrue(store.findLatestStartedByCharacter(CHARACTER, ActivityType.OFFLINE_PRIVATE_STORE, 5)
                    .isEmpty());
        }
    }

    @Nested
    class Connections {

        @Test
        void store_shouldWriteAndHandBackTheConnectionAsLent_whenPoolLendsReadOnlyWithoutAutoCommit() {
            source.lendReadOnly = true;
            source.lendAutoCommit = false;

            assertEquals(InsertResult.INSERTED, store.insert(session(7, 1).build()));

            assertTrue(source.returnedReadOnly);
            assertFalse(source.returnedAutoCommit);
            source.lendReadOnly = false;
            source.lendAutoCommit = true;
            assertTrue(store.findOpenByCharacterId(7).isPresent());
            assertTrue(source.returnedAutoCommit);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = source.raw();
                Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private String text(String sql) throws SQLException {
        try (Connection c = source.raw();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    static final class LentConnectionSource implements JdbcConnectionSource {
        private final String url;
        private final String user;
        private final String password;
        volatile boolean lendReadOnly;
        volatile boolean lendAutoCommit = true;
        volatile boolean returnedReadOnly;
        volatile boolean returnedAutoCommit;

        LentConnectionSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        Connection raw() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public String name() {
            return "it";
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection raw = raw();
            raw.setAutoCommit(lendAutoCommit);
            raw.setReadOnly(lendReadOnly);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        if ("close".equals(method.getName()) && !raw.isClosed()) {
                            returnedReadOnly = raw.isReadOnly();
                            returnedAutoCommit = raw.getAutoCommit();
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
