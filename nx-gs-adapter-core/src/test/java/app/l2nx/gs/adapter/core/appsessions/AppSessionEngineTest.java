package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import app.l2nx.gs.adapter.api.kafka.events.character.AppSessionsSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionCheckOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartOutcome.Status;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.session.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSessionPlacement;
import app.l2nx.gs.adapter.core.events.EventsPublisher.AcknowledgedEvents;
import app.l2nx.gs.adapter.core.events.EventsPublisher.SendCallback;
import app.l2nx.gs.commons.UUIDv7;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class AppSessionEngineTest {

    private static final long MINUTE = 60_000L;
    private static final int CHAR_ID = 268_480_001;

    private InMemoryOfflineSessionStore store;
    private FakeOfflineSessionHost host;
    private ManualScheduler time;
    private RecordingEvents events;
    private AppSessionEngine engine;
    private volatile AppRules rules =
            AppRules.builder().historyRetentionDays(30).build();
    private volatile boolean rulesFromFile = true;

    @BeforeEach
    void setUp() {
        store = new InMemoryOfflineSessionStore();
        host = new FakeOfflineSessionHost();
        time = new ManualScheduler();
        events = new RecordingEvents();
        engine = newEngine();
    }

    private AppSessionEngine newEngine() {
        AppSessionEngine created = new AppSessionEngine(
                store, host, () -> rules, () -> rulesFromFile, Runnable::run, 4, time, time, 1_000L);
        created.bindEvents(events);
        return created;
    }

    private static AppSessionStartRequest.Builder request() {
        return AppSessionStartRequest.builder()
                .idempotencyKey(UUID.randomUUID())
                .characterId(CHAR_ID)
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("player1")
                .masterAccountId(UUID.fromString("0192f000-0000-7000-8000-000000000001"))
                .hwid("HW-1")
                .initiatorIp("203.0.113.7")
                .spotPool("harbor")
                .spotId(5)
                .originX(100)
                .originY(200)
                .originZ(-300)
                .originHeading(1024)
                .plannedMs(60L * MINUTE)
                .typeState("{\"rod\":1}");
    }

    private OfflineSession started(AppSessionStartRequest request) {
        AppSessionStartOutcome outcome = engine.start(request).join();
        assertEquals(Status.STARTED, outcome.getStatus(), outcome.toString());
        return outcome.getSession();
    }

    private OfflineSession openRow(int characterId, int spotId, long activeMs) {
        return OfflineSession.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .characterId(characterId)
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("player" + characterId)
                .initiatorIp("198.51.100.9")
                .spotPool("harbor")
                .spotId(spotId)
                .originX(1)
                .originY(2)
                .originZ(3)
                .originHeading(0)
                .startedAt(time.now().minus(2, ChronoUnit.HOURS))
                .plannedMs(60L * MINUTE)
                .activeMs(activeMs)
                .build();
    }

    private void bootWith(OfflineSession... rows) {
        for (OfflineSession row : rows) {
            store.seed(row);
        }
        engine.boot();
        engine.startScheduling();
    }

    private void hostReady() {
        engine.hostReady();
        time.advance(0L);
    }

    @Nested
    class Start {

        @Test
        void start_shouldStoreRunAndPublishOnline_whenSlotIsFree() {
            AppSessionStartRequest request = request().build();

            OfflineSession actual = started(request);

            OfflineSession stored = store.row(actual.getId());
            assertNotNull(stored);
            assertTrue(stored.isOpen());
            assertEquals(request.getIdempotencyKey(), stored.getIdempotencyKey());
            assertEquals(time.now().truncatedTo(ChronoUnit.SECONDS), stored.getStartedAt());
            assertEquals(Arrays.asList("place:" + actual.getId(), "startAction:" + actual.getId()), host.calls);
            CharacterPresenceEvent event = events.single();
            assertTrue(event.isOnline());
            assertFalse(event.isResumed());
            assertNull(event.getLogoutReason());
            assertAppFields(actual, event);
        }

        @Test
        void start_shouldReturnTheSameSession_whenIdempotencyKeyRepeatsWhileOpen() {
            AppSessionStartRequest request = request().build();
            OfflineSession first = started(request);

            AppSessionStartOutcome actual = engine.start(request).join();

            assertEquals(Status.EXISTING, actual.getStatus());
            assertEquals(first.getId(), actual.getSession().getId());
            assertEquals(1, store.rows.size());
            assertEquals(1, events.events.size());
        }

        @Test
        void start_shouldReturnTheClosedSession_whenIdempotencyKeyRepeatsAfterItEnded() {
            AppSessionStartRequest request = request().build();
            OfflineSession first = started(request);
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            AppSessionStartOutcome actual = engine.start(request).join();

            assertEquals(Status.EXISTING, actual.getStatus());
            assertEquals(first.getId(), actual.getSession().getId());
            assertEquals(LogoutReason.APP_STOPPED, actual.getSession().getEndReason());
            assertEquals(1, store.rows.size());
        }

        @Test
        void start_shouldReturnCharacterBusy_whenCharacterHasAnotherOpenSession() {
            OfflineSession first = started(request().build());

            AppSessionStartOutcome actual =
                    engine.start(request().spotId(6).build()).join();

            assertEquals(Status.CHARACTER_BUSY, actual.getStatus());
            assertEquals(first.getId(), actual.getSession().getId());
        }

        @Test
        void start_shouldMapCharacterUniqueKey_whenTableHoldsAnOpenRowTheEngineDoesNotKnow() {
            OfflineSession foreign = openRow(CHAR_ID, 9, 0L);
            store.seed(foreign);

            AppSessionStartOutcome actual = engine.start(request().build()).join();

            assertEquals(Status.CHARACTER_BUSY, actual.getStatus());
            assertEquals(foreign.getId(), actual.getSession().getId());
            assertFalse(host.called("place:"));
        }

        @Test
        void start_shouldReturnSpotTaken_whenAnotherOpenSessionHoldsTheSpot() {
            started(request().characterId(1).build());

            AppSessionStartOutcome actual =
                    engine.start(request().characterId(2).build()).join();

            assertEquals(Status.SPOT_TAKEN, actual.getStatus());
            assertEquals(1, store.rows.size());
            assertFalse(engine.findActive(2).isPresent());
        }

        @Test
        void start_shouldLeaveNothing_whenHostRefusesThePlacement() {
            host.placement = s -> OfflineSessionPlacement.refused(AppSessionRefusalReason.NO_FREE_SPOT, "pool full");

            AppSessionStartOutcome actual = engine.start(request().build()).join();

            assertEquals(Status.PLACEMENT_REFUSED, actual.getStatus());
            assertEquals(AppSessionRefusalReason.NO_FREE_SPOT, actual.getRefusal());
            assertTrue(store.rows.isEmpty());
            assertTrue(events.events.isEmpty());
            assertFalse(engine.findActive(CHAR_ID).isPresent());
        }

        @Test
        void start_shouldReturnCharacterToOriginAndLeaveNothing_whenStartActionThrows() {
            host.failStartAction = true;

            AppSessionStartOutcome actual = engine.start(request().build()).join();

            assertEquals(Status.UNAVAILABLE, actual.getStatus());
            assertEquals(Arrays.asList(Boolean.TRUE), host.returnFlags);
            assertTrue(store.rows.isEmpty());
            assertTrue(events.events.isEmpty());
        }

        @Test
        void start_shouldStoreThePlacementsTypeStateBeforeTheAction_whenTheHostReturnsOne() {
            host.placement = s -> OfflineSessionPlacement.placed(s.getSpotId(), "{\"rod\":1,\"seat\":3}");

            OfflineSession actual = started(request().build());

            assertEquals("{\"rod\":1,\"seat\":3}", store.row(actual.getId()).getTypeState());
            assertEquals("{\"rod\":1,\"seat\":3}", host.startedSessions.get(0).getTypeState());
            assertEquals("{\"rod\":1,\"seat\":3}", actual.getTypeState());
        }

        @Test
        void start_shouldKeepTheRequestsTypeState_whenThePlacementReturnsNone() {
            OfflineSession actual = started(request().build());

            assertEquals("{\"rod\":1}", store.row(actual.getId()).getTypeState());
            assertEquals("{\"rod\":1}", host.startedSessions.get(0).getTypeState());
        }

        @Test
        void start_shouldReportRefusedForNowAsARefusal() {
            host.placement =
                    s -> OfflineSessionPlacement.refusedForNow(AppSessionRefusalReason.CHARACTER_ONLINE, "locked");

            AppSessionStartOutcome actual = engine.start(request().build()).join();

            assertEquals(Status.PLACEMENT_REFUSED, actual.getStatus());
            assertEquals(AppSessionRefusalReason.CHARACTER_ONLINE, actual.getRefusal());
            assertTrue(store.rows.isEmpty());
        }

        @Test
        void start_shouldStoreHostSpot_whenHostPlacesOnAnotherSpot() {
            host.placement = s -> OfflineSessionPlacement.placed(7);

            OfflineSession actual = started(request().build());

            assertEquals(7, actual.getSpotId());
            assertEquals(7, store.row(actual.getId()).getSpotId());
        }
    }

    @Nested
    class Check {

        private AppSessionCheckOutcome check(AppSessionStartRequest request) {
            return engine.check(request.getCharacterId(), request.getSpotPool(), request.getSpotId())
                    .join();
        }

        private void assertCheckMatchesStart(AppSessionStartRequest request) {
            List<OfflineSession> rowsBefore = new ArrayList<OfflineSession>(store.rows.values());
            List<String> hostCallsBefore = new ArrayList<String>(host.calls);
            int eventsBefore = events.events.size();

            AppSessionCheckOutcome checked = check(request);

            assertEquals(rowsBefore, new ArrayList<OfflineSession>(store.rows.values()));
            assertEquals(hostCallsBefore, host.calls);
            assertEquals(eventsBefore, events.events.size());

            AppSessionStartOutcome started = engine.start(request).join();

            assertEquals(started.getStatus().name(), checked.getStatus().name(), checked + " vs " + started);
            assertEquals(started.getSession(), checked.getSession());
        }

        @Test
        void check_shouldAllowAndChangeNothing_whenCharacterAndSpotAreFree() {
            AppSessionCheckOutcome actual = check(request().build());

            assertEquals(AppSessionCheckOutcome.Status.ALLOWED, actual.getStatus());
            assertNull(actual.getSession());
            assertTrue(store.rows.isEmpty());
            assertTrue(host.calls.isEmpty());
            assertTrue(events.events.isEmpty());
            assertFalse(engine.findActive(CHAR_ID).isPresent());
            assertTrue(engine.findOpen(ActivityType.OFFLINE_FISHING).isEmpty());
            assertEquals(Status.STARTED, engine.start(request().build()).join().getStatus());
        }

        @Test
        void check_shouldMatchStart_whenCharacterHasAnotherOpenSession() {
            started(request().build());

            assertCheckMatchesStart(request().spotId(6).build());
        }

        @Test
        void check_shouldMatchStart_whenTableHoldsAnOpenRowTheEngineDoesNotKnow() {
            store.seed(openRow(CHAR_ID, 9, 0L));

            assertCheckMatchesStart(request().build());
        }

        @Test
        void check_shouldMatchStart_whenAnEndOfTheCharacterIsQueuedForRetry() {
            started(request().build());
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertCheckMatchesStart(request().spotId(6).build());
        }

        @Test
        void check_shouldMatchStart_whenAnotherOpenSessionHoldsTheSpot() {
            started(request().characterId(1).build());

            assertCheckMatchesStart(request().characterId(2).build());
        }

        @Test
        void check_shouldMatchStart_whenTheEngineStops() {
            engine.shutdown();

            assertCheckMatchesStart(request().build());
        }

        @Test
        void check_shouldBeUnavailable_whenTheTableCannotBeRead() {
            store.failReads = true;

            AppSessionCheckOutcome actual = check(request().build());

            assertEquals(AppSessionCheckOutcome.Status.UNAVAILABLE, actual.getStatus());
            assertTrue(store.rows.isEmpty());
        }

        @Test
        void check_shouldAnswerFromMemoryWithoutTheTable_whenCharacterIsBusy() {
            started(request().build());

            AppSessionCheckOutcome actual = check(request().spotId(6).build());

            assertEquals(AppSessionCheckOutcome.Status.CHARACTER_BUSY, actual.getStatus());
            assertEquals(0, store.probes);
        }
    }

    @Nested
    class ActiveTime {

        @Test
        void flush_shouldStoreActiveMsEveryMinute_whenSessionRuns() {
            engine.startScheduling();
            OfflineSession session = started(request().plannedMs(null).build());

            time.advance(MINUTE);
            assertEquals(MINUTE, store.row(session.getId()).getActiveMs());

            time.advance(2 * MINUTE);
            assertEquals(3 * MINUTE, store.row(session.getId()).getActiveMs());
        }

        @Test
        void restore_shouldNotCountDowntime_whenServerWasDownBetweenRuns() {
            OfflineSession row = openRow(CHAR_ID, 5, 10 * MINUTE);
            bootWith(row);

            hostReady();
            time.advance(MINUTE);

            assertEquals(11 * MINUTE, store.row(row.getId()).getActiveMs());
            assertEquals(11 * MINUTE, engine.findActive(CHAR_ID).get().getActiveMs());
        }

        @Test
        void shutdown_shouldStoreActiveMsAndKeepSessionsOpen() {
            OfflineSession session = started(request().plannedMs(null).build());
            time.advance(90_000L);

            engine.shutdown();

            OfflineSession stored = store.row(session.getId());
            assertEquals(90_000L, stored.getActiveMs());
            assertTrue(stored.isOpen());
            assertEquals(1, events.events.size());
        }
    }

    @Nested
    class EndByTime {

        @Test
        void timer_shouldCloseWithTimeUp_whenActiveMsReachesPlannedMs() {
            OfflineSession session = started(request().plannedMs(30 * MINUTE).build());

            time.advance(30 * MINUTE - 1);
            assertTrue(store.row(session.getId()).isOpen());
            time.advance(1);

            OfflineSession stored = store.row(session.getId());
            assertEquals(LogoutReason.APP_TIME_UP, stored.getEndReason());
            assertEquals(30 * MINUTE, stored.getActiveMs());
            CharacterPresenceEvent end = events.last();
            assertFalse(end.isOnline());
            assertEquals(LogoutReason.APP_TIME_UP, end.getLogoutReason());
            assertAppFields(session, end);
        }

        @Test
        void timer_shouldNeverFire_whenSessionHasNoPlannedMs() {
            OfflineSession session = started(request().plannedMs(null).build());

            time.advance(24 * 60 * MINUTE);

            assertTrue(store.row(session.getId()).isOpen());
        }

        @Test
        void restore_shouldCloseWithTimeUpWithoutPlacing_whenStoredActiveMsAlreadyReachedPlannedMs() {
            OfflineSession row = openRow(CHAR_ID, 5, 60 * MINUTE);
            bootWith(row);

            hostReady();

            assertEquals(LogoutReason.APP_TIME_UP, store.row(row.getId()).getEndReason());
            assertFalse(host.called("place:"));
        }
    }

    @Nested
    class ReturnRule {

        @ParameterizedTest(name = "active {0} min, threshold 30 min -> return {1}")
        @CsvSource({"29, true", "30, false", "31, false"})
        void close_shouldReturnToOriginOnlyBelowThreshold(long activeMinutes, boolean expected) {
            started(request().plannedMs(null).build());
            time.advance(activeMinutes * MINUTE);

            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertEquals(Arrays.asList(expected), host.returnFlags);
        }

        @Test
        void start_shouldStoreTheThresholdOfTheFileAtTheStart() {
            OfflineSession actual = started(request().build());

            assertEquals(Long.valueOf(30 * MINUTE), actual.getReturnThresholdMs());
            assertEquals(Long.valueOf(30 * MINUTE), store.row(actual.getId()).getReturnThresholdMs());
        }

        @ParameterizedTest(name = "fixed {0} min, file changed to {1} min, active {2} min -> return {3}")
        @CsvSource({"30, 60, 45, false", "30, 10, 20, true"})
        void close_shouldCompareWithTheThresholdFixedAtTheStart_whenTheFileChangedMeanwhile(
                int fixedMinutes, int changedMinutes, long activeMinutes, boolean expected) {
            rules = rules.toBuilder().returnThresholdMinutes(fixedMinutes).build();
            started(request().plannedMs(null).build());
            rules = rules.toBuilder().returnThresholdMinutes(changedMinutes).build();
            time.advance(activeMinutes * MINUTE);

            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertEquals(Arrays.asList(expected), host.returnFlags);
        }

        @Test
        void close_shouldUseTheCurrentThreshold_whenTheRowWasStoredWithoutOne() {
            rules = rules.toBuilder().returnThresholdMinutes(60).build();
            bootWith(openRow(CHAR_ID, 5, 40 * MINUTE));
            hostReady();

            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertEquals(Arrays.asList(Boolean.TRUE), host.returnFlags);
        }
    }

    @Nested
    class ClientIdentifierPlaceholders {

        @Test
        void start_shouldStoreAndPublishNull_whenHwidAndIpArePlaceholders() {
            OfflineSession actual =
                    started(request().hwid("N/A").initiatorIp("0.0.0.0").build());

            assertNull(actual.getHwid());
            assertNull(actual.getInitiatorIp());
            assertNull(store.row(actual.getId()).getHwid());
            assertNull(store.row(actual.getId()).getInitiatorIp());
            CharacterPresenceEvent online = events.single();
            assertNull(online.getHwid());
            assertNull(online.getIp());
        }
    }

    @Nested
    class FindOpenByMasterAccount {

        private final UUID master = UUID.fromString("0192f000-0000-7000-8000-000000000001");

        @Test
        void findOpenByMasterAccount_shouldReturnRunningAndPendingSessionsByStart() {
            OfflineSession pending =
                    openRow(2, 2, 0L).toBuilder().masterAccountId(master).build();
            OfflineSession otherMaster = openRow(3, 3, 0L).toBuilder()
                    .masterAccountId(UUID.randomUUID())
                    .build();
            OfflineSession noMaster = openRow(4, 4, 0L);
            OfflineSession otherActivity = openRow(5, 6, 0L).toBuilder()
                    .masterAccountId(master)
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .build();
            bootWith(pending, otherMaster, noMaster, otherActivity);
            OfflineSession running = started(request().build());

            List<OfflineSession> actual = engine.findOpenByMasterAccount(master, ActivityType.OFFLINE_FISHING);

            assertEquals(
                    Arrays.asList(pending.getId(), running.getId()),
                    Arrays.asList(actual.get(0).getId(), actual.get(1).getId()));
            assertEquals(2, actual.size());
        }

        @Test
        void findOpenByMasterAccount_shouldOrderByIdAfterTheStart_whenStartsAreEqual() {
            Instant start = time.now().minus(1, ChronoUnit.HOURS);
            OfflineSession high = openRow(1, 1, 0L).toBuilder()
                    .id(UUID.fromString("0192f000-0000-7000-8000-0000000000ff"))
                    .masterAccountId(master)
                    .startedAt(start)
                    .build();
            OfflineSession low = openRow(2, 2, 0L).toBuilder()
                    .id(UUID.fromString("0192f000-0000-7000-8000-000000000001"))
                    .masterAccountId(master)
                    .startedAt(start)
                    .build();
            bootWith(high, low);

            List<OfflineSession> actual = engine.findOpenByMasterAccount(master, ActivityType.OFFLINE_FISHING);

            assertEquals(low.getId(), actual.get(0).getId());
            assertEquals(high.getId(), actual.get(1).getId());
        }

        @Test
        void findOpenByMasterAccount_shouldIncludeAStartInFlight() {
            List<List<OfflineSession>> seenDuringPlacement = new ArrayList<List<OfflineSession>>();
            host.placement = s -> {
                seenDuringPlacement.add(engine.findOpenByMasterAccount(master, ActivityType.OFFLINE_FISHING));
                return OfflineSessionPlacement.placed(s.getSpotId());
            };

            OfflineSession actual = started(request().build());

            assertEquals(1, seenDuringPlacement.get(0).size());
            assertEquals(actual.getId(), seenDuringPlacement.get(0).get(0).getId());
        }

        @Test
        void findOpenByMasterAccount_shouldIncludeAnEndQueuedForRetry() {
            OfflineSession session = started(request().build());
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            List<OfflineSession> actual = engine.findOpenByMasterAccount(master, ActivityType.OFFLINE_FISHING);

            assertEquals(1, actual.size());
            assertEquals(session.getId(), actual.get(0).getId());
            assertEquals(LogoutReason.APP_STOPPED, actual.get(0).getEndReason());
        }

        @Test
        void findOpenByMasterAccount_shouldBeEmpty_whenTheSessionClosed() {
            started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertTrue(engine.findOpenByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                    .isEmpty());
        }
    }

    @Nested
    class FindOpen {

        @Test
        void findOpen_shouldListEverySessionOfTheActivityByStartWithItsSpot_whenRestoreAwaitsHostReady() {
            OfflineSession later = openRow(2, 7, 0L).toBuilder()
                    .startedAt(time.now().minus(1, ChronoUnit.HOURS))
                    .build();
            OfflineSession earlier = openRow(3, 8, 0L).toBuilder()
                    .spotPool("river")
                    .startedAt(time.now().minus(3, ChronoUnit.HOURS))
                    .build();
            OfflineSession otherActivity = openRow(4, 9, 0L).toBuilder()
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .build();
            bootWith(later, earlier, otherActivity);
            OfflineSession running = started(request().build());

            List<OfflineSession> actual = engine.findOpen(ActivityType.OFFLINE_FISHING);

            assertEquals(
                    Arrays.asList(earlier.getId(), later.getId(), running.getId()),
                    Arrays.asList(
                            actual.get(0).getId(),
                            actual.get(1).getId(),
                            actual.get(2).getId()));
            assertEquals(3, actual.size());
            assertEquals("river", actual.get(0).getSpotPool());
            assertEquals(8, actual.get(0).getSpotId());
            assertFalse(host.called("place:" + earlier.getId()));
        }

        @Test
        void findOpen_shouldIncludeAStartInFlight() {
            List<List<OfflineSession>> seenDuringPlacement = new ArrayList<List<OfflineSession>>();
            host.placement = s -> {
                seenDuringPlacement.add(engine.findOpen(ActivityType.OFFLINE_FISHING));
                return OfflineSessionPlacement.placed(s.getSpotId());
            };

            OfflineSession actual = started(request().build());

            assertEquals(1, seenDuringPlacement.get(0).size());
            assertEquals(actual.getId(), seenDuringPlacement.get(0).get(0).getId());
        }

        @Test
        void findOpen_shouldIncludeAnEndQueuedForRetry() {
            OfflineSession session = started(request().build());
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            List<OfflineSession> actual = engine.findOpen(ActivityType.OFFLINE_FISHING);

            assertEquals(1, actual.size());
            assertEquals(session.getId(), actual.get(0).getId());
            assertEquals(LogoutReason.APP_STOPPED, actual.get(0).getEndReason());
        }

        @Test
        void findOpen_shouldBeEmpty_whenTheSessionClosed() {
            started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertTrue(engine.findOpen(ActivityType.OFFLINE_FISHING).isEmpty());
        }
    }

    @Nested
    class FindLastStartedByCharacter {

        private final ActivityType privateStore = ActivityType.OFFLINE_PRIVATE_STORE;

        private OfflineSession row(int characterId, ActivityType activity, Instant startedAt) {
            return openRow(characterId, characterId, 0L).toBuilder()
                    .id(UUIDv7.generate(startedAt))
                    .activity(activity)
                    .plannedMs(null)
                    .startedAt(startedAt)
                    .endedAt(startedAt.plus(1, ChronoUnit.MINUTES))
                    .endReason(LogoutReason.APP_STOPPED)
                    .build();
        }

        private AppSessionStartRequest.Builder storeRequest() {
            return request().activity(privateStore).plannedMs(null);
        }

        @Test
        void findLastStarted_shouldReturnTheCharactersLatestRealStart_skippingOtherCharactersAndActivities() {
            Instant now = time.now();
            OfflineSession expected = row(CHAR_ID, privateStore, now.minus(10, ChronoUnit.MINUTES));
            store.seed(row(CHAR_ID, privateStore, now.minus(2, ChronoUnit.HOURS)));
            store.seed(expected);
            store.seed(row(CHAR_ID, ActivityType.OFFLINE_FISHING, now));
            store.seed(row(CHAR_ID + 1, privateStore, now));

            Optional<OfflineSession> actual =
                    engine.findLastStartedByCharacter(CHAR_ID, privateStore).join();

            assertEquals(expected.getId(), actual.get().getId());
        }

        @Test
        void findLastStarted_shouldCountASessionWithoutAMasterAccount() {
            OfflineSession expected = row(CHAR_ID, privateStore, time.now()).toBuilder()
                    .masterAccountId(null)
                    .build();
            store.seed(expected);

            assertEquals(
                    expected.getId(),
                    engine.findLastStartedByCharacter(CHAR_ID, privateStore)
                            .join()
                            .get()
                            .getId());
        }

        @Test
        void findLastStarted_shouldSkipTheCharactersStartInFlight() {
            OfflineSession earlier = row(CHAR_ID, privateStore, time.now().minus(1, ChronoUnit.HOURS));
            store.seed(earlier);
            List<Optional<OfflineSession>> seenDuringPlacement = new ArrayList<Optional<OfflineSession>>();
            host.placement = s -> {
                seenDuringPlacement.add(
                        engine.findLastStartedByCharacter(CHAR_ID, privateStore).join());
                return OfflineSessionPlacement.placed(s.getSpotId());
            };

            OfflineSession started = started(storeRequest().build());

            assertEquals(earlier.getId(), seenDuringPlacement.get(0).get().getId());
            assertEquals(
                    started.getId(),
                    engine.findLastStartedByCharacter(CHAR_ID, privateStore)
                            .join()
                            .get()
                            .getId());
        }

        @Test
        void findLastStarted_shouldSkipARolledBackStartWhoseDeleteIsStillPending() {
            host.failStartAction = true;
            store.failDeletes = 1;
            assertEquals(
                    Status.UNAVAILABLE,
                    engine.start(storeRequest().build()).join().getStatus());

            assertFalse(engine.findLastStartedByCharacter(CHAR_ID, privateStore)
                    .join()
                    .isPresent());
        }

        @Test
        void findLastStarted_shouldCompleteExceptionally_whenTheTableCannotBeRead() {
            store.failReads = true;

            CompletableFuture<Optional<OfflineSession>> actual =
                    engine.findLastStartedByCharacter(CHAR_ID, privateStore);

            assertTrue(actual.isCompletedExceptionally());
        }
    }

    @Nested
    class FindLastStartedByMasterAccount {

        private final UUID master = UUID.fromString("0192f000-0000-7000-8000-000000000001");

        private OfflineSession row(int characterId, Instant startedAt, @Nullable Instant endedAt) {
            OfflineSession.Builder b = openRow(characterId, characterId, 0L).toBuilder()
                    .id(UUIDv7.generate(startedAt))
                    .masterAccountId(master)
                    .startedAt(startedAt);
            if (endedAt != null) {
                b.endedAt(endedAt).endReason(LogoutReason.APP_STOPPED);
            }
            return b.build();
        }

        @Test
        void findLastStarted_shouldReturnTheLatestRealStart_openOrClosed() {
            Instant now = time.now();
            OfflineSession older = row(1, now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS));
            OfflineSession latestClosed = row(2, now.minus(1, ChronoUnit.HOURS), now.minus(30, ChronoUnit.MINUTES));
            store.seed(older);
            store.seed(latestClosed);
            store.seed(row(3, now, null).toBuilder()
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .build());

            Optional<OfflineSession> actual = engine.findLastStartedByMasterAccount(
                            master, ActivityType.OFFLINE_FISHING)
                    .join();

            assertEquals(latestClosed.getId(), actual.get().getId());
        }

        @Test
        void findLastStarted_shouldPreferTheGreaterId_whenStartsAreEqual() {
            Instant start = time.now().minus(1, ChronoUnit.HOURS);
            OfflineSession low = row(1, start, null).toBuilder()
                    .id(UUID.fromString("0192f000-0000-7000-8000-000000000001"))
                    .build();
            OfflineSession high = row(2, start, null).toBuilder()
                    .id(UUID.fromString("0192f000-0000-7000-8000-0000000000ff"))
                    .build();
            store.seed(high);
            store.seed(low);

            assertEquals(
                    high.getId(),
                    engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                            .join()
                            .get()
                            .getId());
        }

        @Test
        void findLastStarted_shouldSkipAStartInFlight() {
            OfflineSession earlier = row(1, time.now().minus(1, ChronoUnit.HOURS), time.now());
            store.seed(earlier);
            List<Optional<OfflineSession>> seenDuringPlacement = new ArrayList<Optional<OfflineSession>>();
            host.placement = s -> {
                seenDuringPlacement.add(engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                        .join());
                return OfflineSessionPlacement.placed(s.getSpotId());
            };

            OfflineSession started = started(request().build());

            assertEquals(earlier.getId(), seenDuringPlacement.get(0).get().getId());
            assertEquals(
                    started.getId(),
                    engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                            .join()
                            .get()
                            .getId());
        }

        @Test
        void findLastStarted_shouldSkipARolledBackStartWhoseDeleteIsStillPending() {
            host.failStartAction = true;
            store.failDeletes = 1;
            assertEquals(
                    Status.UNAVAILABLE, engine.start(request().build()).join().getStatus());

            assertFalse(engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                    .join()
                    .isPresent());
        }

        @Test
        void findLastStarted_shouldSkipAStartThatBeganDuringTheQuery() {
            OfflineSession earlier = row(1, time.now().minus(1, ChronoUnit.HOURS), time.now());
            store.seed(earlier);
            InFlightStart[] began = new InFlightStart[1];
            store.beforeFindLatestStarted = () -> {
                store.beforeFindLatestStarted = null;
                began[0] = new InFlightStart();
            };

            Optional<OfflineSession> actual = engine.findLastStartedByMasterAccount(
                            master, ActivityType.OFFLINE_FISHING)
                    .join();
            began[0].finish();

            assertEquals(earlier.getId(), actual.get().getId());
        }

        @Test
        void findLastStarted_shouldReturnTheRealStart_whenAStartInFlightIsPlacedDuringTheQuery() {
            store.seed(row(1, time.now().minus(1, ChronoUnit.HOURS), time.now()));
            InFlightStart inFlight = new InFlightStart();
            store.afterFindLatestStarted = () -> {
                store.afterFindLatestStarted = null;
                time.advance(5_000L);
                inFlight.finish();
            };

            OfflineSession actual = engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                    .join()
                    .get();

            OfflineSession expected = inFlight.outcome.join().getSession();
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getStartedAt(), actual.getStartedAt());
        }

        @Test
        void findLastStarted_shouldSkipAStartRolledBackDuringTheQuery() {
            OfflineSession earlier = row(1, time.now().minus(1, ChronoUnit.HOURS), time.now());
            store.seed(earlier);
            InFlightStart inFlight = new InFlightStart();
            host.failStartAction = true;
            store.afterFindLatestStarted = () -> {
                store.afterFindLatestStarted = null;
                inFlight.finish();
            };

            Optional<OfflineSession> actual = engine.findLastStartedByMasterAccount(
                            master, ActivityType.OFFLINE_FISHING)
                    .join();

            assertEquals(Status.UNAVAILABLE, inFlight.outcome.join().getStatus());
            assertEquals(earlier.getId(), actual.get().getId());
        }

        @Test
        void findLastStarted_shouldReadPastAFullPageOfStartsThatNeverHappened() {
            OfflineSession earlier = row(1, time.now().minus(1, ChronoUnit.HOURS), time.now());
            store.seed(earlier);
            host.failStartAction = true;
            int neverStarted = AppSessionEngine.LAST_STARTED_CANDIDATES + 1;
            store.failDeletes = neverStarted;
            for (int i = 0; i < neverStarted; i++) {
                AppSessionStartRequest request =
                        request().characterId(CHAR_ID + i).spotId(10 + i).build();
                assertEquals(Status.UNAVAILABLE, engine.start(request).join().getStatus());
            }

            Optional<OfflineSession> actual = engine.findLastStartedByMasterAccount(
                            master, ActivityType.OFFLINE_FISHING)
                    .join();

            assertEquals(earlier.getId(), actual.get().getId());
        }

        @Test
        void findLastStarted_shouldAnswerAfterTheLastRound_whenStartsKeepSettlingDuringEveryQuery() {
            OfflineSession earlier = row(1, time.now().minus(1, ChronoUnit.HOURS), time.now());
            store.seed(earlier);
            UUID otherMaster = UUID.fromString("0192f000-0000-7000-8000-000000000002");
            int[] queries = {0};
            store.afterFindLatestStarted = () -> {
                int n = ++queries[0];
                if (n <= 10) {
                    started(request()
                            .masterAccountId(otherMaster)
                            .characterId(CHAR_ID + n)
                            .spotId(10 + n)
                            .build());
                }
            };

            Optional<OfflineSession> actual = engine.findLastStartedByMasterAccount(
                            master, ActivityType.OFFLINE_FISHING)
                    .join();

            assertEquals(earlier.getId(), actual.get().getId());
            assertEquals(AppSessionEngine.LAST_STARTED_ROUNDS, queries[0]);
        }

        @Test
        void findLastStarted_shouldBeEmpty_whenTheMasterAccountHasNoSession() {
            store.seed(
                    row(1, time.now(), null).toBuilder().masterAccountId(null).build());

            assertFalse(engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING)
                    .join()
                    .isPresent());
        }

        @Test
        void findLastStarted_shouldCompleteExceptionally_whenTheTableCannotBeRead() {
            store.failReads = true;

            CompletableFuture<Optional<OfflineSession>> actual =
                    engine.findLastStartedByMasterAccount(master, ActivityType.OFFLINE_FISHING);

            assertTrue(actual.isCompletedExceptionally());
        }

        private final class InFlightStart {
            private final CountDownLatch placing = new CountDownLatch(1);
            private final CountDownLatch release = new CountDownLatch(1);
            final CompletableFuture<AppSessionStartOutcome> outcome;

            InFlightStart() {
                host.placement = s -> {
                    placing.countDown();
                    await(release);
                    return OfflineSessionPlacement.placed(s.getSpotId());
                };
                outcome = CompletableFuture.supplyAsync(
                        () -> engine.start(request().build()).join());
                await(placing);
            }

            void finish() {
                release.countDown();
                outcome.join();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class Stop {

        @Test
        void stop_shouldCloseWithStopped_whenSessionRuns() {
            OfflineSession session = started(request().build());
            time.advance(5 * MINUTE);

            Optional<OfflineSession> actual =
                    engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertTrue(actual.isPresent());
            assertEquals(LogoutReason.APP_STOPPED, actual.get().getEndReason());
            assertEquals(5 * MINUTE, actual.get().getActiveMs());
            assertEquals(
                    Arrays.asList(
                            "place:" + session.getId(),
                            "startAction:" + session.getId(),
                            "stopAction:" + session.getId(),
                            "remove:" + session.getId() + ":true"),
                    host.calls);
            OfflineSession stored = store.row(session.getId());
            assertEquals(LogoutReason.APP_STOPPED, stored.getEndReason());
            assertEquals(time.now().truncatedTo(ChronoUnit.SECONDS), stored.getEndedAt());
            assertFalse(engine.findActive(CHAR_ID).isPresent());
            CharacterPresenceEvent end = events.last();
            assertFalse(end.isOnline());
            assertEquals(LogoutReason.APP_STOPPED, end.getLogoutReason());
            assertAppFields(session, end);
        }

        @Test
        void stop_shouldFindNothing_whenSessionIsOfAnotherActivity() {
            started(request()
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .plannedMs(null)
                    .build());

            Optional<OfflineSession> actual =
                    engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertFalse(actual.isPresent());
            assertTrue(engine.findActive(CHAR_ID).isPresent());
        }

        @Test
        void stop_shouldFindNothing_whenCharacterHasNoSession() {
            assertFalse(
                    engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join().isPresent());
        }

        @Test
        void stop_shouldCloseWithStoppedBeforeRestore_whenRestoreHasNotReachedTheSession() {
            OfflineSession row = openRow(CHAR_ID, 5, 40 * MINUTE);
            bootWith(row);

            Optional<OfflineSession> actual =
                    engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();
            hostReady();

            assertEquals(LogoutReason.APP_STOPPED_BEFORE_RESTORE, actual.get().getEndReason());
            assertEquals(Arrays.asList("remove:" + row.getId() + ":false"), host.calls);
            assertEquals(
                    LogoutReason.APP_STOPPED_BEFORE_RESTORE,
                    store.row(row.getId()).getEndReason());
            CharacterPresenceEvent end = events.single();
            assertFalse(end.isOnline());
            assertEquals(LogoutReason.APP_STOPPED_BEFORE_RESTORE, end.getLogoutReason());
            assertAppFields(row, end);
        }
    }

    @Nested
    class Login {

        @Test
        void onClientLogin_shouldCloseWithLoginBeforeReturning_whenSessionRuns() {
            OfflineSession session = started(request().build());

            ClientLoginOutcome actual = engine.onClientLogin(CHAR_ID);

            assertEquals(ClientLoginOutcome.Status.CLOSED, actual.getStatus());
            assertEquals(LogoutReason.APP_LOGIN, actual.getSession().getEndReason());
            assertTrue(host.called("stopAction:" + session.getId()));
            assertTrue(host.called("remove:" + session.getId()));
            assertEquals(LogoutReason.APP_LOGIN, store.row(session.getId()).getEndReason());
            assertEquals(LogoutReason.APP_LOGIN, events.last().getLogoutReason());
        }

        @Test
        void onClientLogin_shouldCancelTheRestore_whenSessionWaitsForIt() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            bootWith(row);

            ClientLoginOutcome actual = engine.onClientLogin(CHAR_ID);
            hostReady();

            assertEquals(LogoutReason.APP_LOGIN, actual.getSession().getEndReason());
            assertFalse(host.called("place:"));
            assertFalse(host.called("stopAction:"));
            assertEquals(Arrays.asList("remove:" + row.getId() + ":true"), host.calls);
        }

        @Test
        void onClientLogin_shouldTouchNothing_whenAnotherCharacterHasTheSession() {
            started(request().build());

            assertEquals(
                    ClientLoginOutcome.Status.NONE,
                    engine.onClientLogin(CHAR_ID + 1).getStatus());
            assertTrue(engine.findActive(CHAR_ID).isPresent());
        }

        @Test
        void onClientLogin_shouldBePendingAndFinishTheCloseLater_whenTheCloseOutlastsTheWait() throws Exception {
            ExecutorService io = Executors.newSingleThreadExecutor();
            try {
                AppSessionEngine slow =
                        new AppSessionEngine(store, host, () -> rules, () -> rulesFromFile, io, 4, time, time, 50L);
                slow.bindEvents(events);
                OfflineSession session = slow.start(request().build()).join().getSession();
                CountDownLatch gate = new CountDownLatch(1);
                host.removeGate = gate;

                ClientLoginOutcome actual = slow.onClientLogin(CHAR_ID);
                gate.countDown();
                io.submit(() -> {}).get();

                assertEquals(ClientLoginOutcome.Status.PENDING, actual.getStatus());
                assertEquals(LogoutReason.APP_LOGIN, store.row(session.getId()).getEndReason());
            } finally {
                host.removeGate = null;
                io.shutdownNow();
            }
        }

        @Test
        void closeAfterLateLogin_shouldCloseWithLogin_whenLoginGaveUpWaitingForTheBoot() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            bootWith(row);

            engine.closeAfterLateLogin(CHAR_ID);

            assertEquals(LogoutReason.APP_LOGIN, store.row(row.getId()).getEndReason());
        }
    }

    @Nested
    class HostCallbacks {

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = LogoutReason.class,
                names = {
                    "APP_OUT_OF_BAIT",
                    "APP_DAILY_LIMIT",
                    "APP_INVENTORY_FULL",
                    "APP_SOLD_OUT",
                    "APP_INTERRUPTED",
                    "BANNED",
                    "KICKED"
                })
        void onActionEnded_shouldCloseWithTheHostsReason_whenSessionRuns(LogoutReason reason) {
            OfflineSession session = started(request().build());

            engine.onActionEnded(session.getId(), reason);

            assertEquals(reason, store.row(session.getId()).getEndReason());
            assertTrue(host.called("stopAction:" + session.getId()));
            assertEquals(reason, events.last().getLogoutReason());
        }

        @Test
        void onActionEnded_shouldBeIgnored_whenReasonBelongsToTheEngine() {
            OfflineSession session = started(request().build());

            engine.onActionEnded(session.getId(), LogoutReason.APP_TIME_UP);

            assertTrue(store.row(session.getId()).isOpen());
        }

        @Test
        void updateTypeState_shouldStoreJsonAndExposeIt_whenSessionIsOpen() {
            OfflineSession session = started(request().build());

            assertTrue(engine.updateTypeState(session.getId(), "{\"fee\":5}").join());

            assertEquals("{\"fee\":5}", store.row(session.getId()).getTypeState());
            assertEquals("{\"fee\":5}", engine.findActive(CHAR_ID).get().getTypeState());
        }

        @Test
        void updateTypeState_shouldRunInline_whenCalledFromInsideAHostCallbackOfTheSession() {
            List<Boolean> done = new ArrayList<Boolean>();
            host.onStartAction = s ->
                    done.add(engine.updateTypeState(s.getId(), "{\"inline\":1}").isDone());

            OfflineSession session = started(request().build());

            assertEquals(Arrays.asList(Boolean.TRUE), done);
            assertEquals("{\"inline\":1}", store.row(session.getId()).getTypeState());
        }

        @Test
        void start_shouldKeepAnInlineTypeStateWrite_whenThePlacementMadeIt() {
            host.placement = s -> {
                engine.updateTypeState(s.getId(), "{\"fromPlace\":1}");
                return OfflineSessionPlacement.placed(s.getSpotId());
            };

            OfflineSession session = started(request().build());

            assertEquals("{\"fromPlace\":1}", store.row(session.getId()).getTypeState());
            assertEquals("{\"fromPlace\":1}", host.startedSessions.get(0).getTypeState());
        }

        @Test
        void updateTypeState_shouldReturnFalse_whenSessionIsClosed() {
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertFalse(engine.updateTypeState(session.getId(), "{}").join());
        }
    }

    @Nested
    class Restore {

        @Test
        void restore_shouldNotRun_whenHostIsNotReady() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            bootWith(row);

            time.advance(24 * 60 * MINUTE);

            assertFalse(host.called("place:"));
            assertTrue(engine.findActive(CHAR_ID).isPresent());
            assertTrue(events.events.isEmpty());
        }

        @Test
        void hostReady_shouldPlaceStartAndPublishResumedAtOnce_whenModuleIsStarted() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            bootWith(row);

            hostReady();

            assertEquals(Arrays.asList("place:" + row.getId(), "startAction:" + row.getId()), host.calls);
            CharacterPresenceEvent event = events.single();
            assertTrue(event.isOnline());
            assertTrue(event.isResumed());
            assertEquals(row.getId(), event.getSessionId());
            assertAppFields(row, event);
        }

        @Test
        void startScheduling_shouldRestoreAtOnce_whenHostWasReadyBefore() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            store.seed(row);
            engine.boot();
            engine.hostReady();
            time.advance(MINUTE);
            assertFalse(host.called("place:"));

            engine.startScheduling();
            time.advance(0L);

            assertEquals(Arrays.asList("place:" + row.getId(), "startAction:" + row.getId()), host.calls);
        }

        @Test
        void hostReady_shouldDoNothing_whenCalledAgain() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            bootWith(row);
            hostReady();

            hostReady();

            assertEquals(Arrays.asList("place:" + row.getId(), "startAction:" + row.getId()), host.calls);
            assertEquals(1, events.events.size());
        }

        @Test
        void hostReady_shouldNotRestore_whenEngineIsShutDown() {
            bootWith(openRow(CHAR_ID, 5, 0L));
            engine.shutdown();

            hostReady();

            assertFalse(host.called("place:"));
        }

        @Test
        void restore_shouldStoreTheNewSpot_whenHostPlacesOnAnotherSpotOfThePool() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            host.placement = s -> OfflineSessionPlacement.placed(8);
            bootWith(row);

            hostReady();

            assertEquals(8, store.row(row.getId()).getSpotId());
            assertEquals(8, engine.findActive(CHAR_ID).get().getSpotId());
        }

        @Test
        void restore_shouldStoreThePlacementsTypeState_whenTheHostChangedIt() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L).toBuilder()
                    .typeState("{\"lots\":2}")
                    .build();
            host.placement = s -> OfflineSessionPlacement.placed(5, "{\"lots\":1}");
            bootWith(row);

            hostReady();

            assertEquals("{\"lots\":1}", store.row(row.getId()).getTypeState());
            assertEquals("{\"lots\":1}", host.startedSessions.get(0).getTypeState());
        }

        @Test
        void restore_shouldTryAgainAfterTheBatchPause_whenTheHostRefusesForNow() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            List<OfflineSessionPlacement> answers = new ArrayList<OfflineSessionPlacement>(Arrays.asList(
                    OfflineSessionPlacement.refusedForNow(AppSessionRefusalReason.CHARACTER_ONLINE, "locked"),
                    OfflineSessionPlacement.placed(5)));
            host.placement = s -> answers.remove(0);
            bootWith(row);

            hostReady();
            assertTrue(store.row(row.getId()).isOpen());
            assertTrue(events.events.isEmpty());

            time.advance(rules.restoreBatchPauseMillis());

            assertTrue(host.called("startAction:" + row.getId()));
            assertTrue(events.single().isResumed());
        }

        @Test
        void restore_shouldPutARefusedForNowSessionBehindTheOthers() {
            rules = rules.toBuilder().restoreBatchSize(1).build();
            OfflineSession first = openRow(1, 1, 0L);
            OfflineSession second = openRow(2, 2, 0L).toBuilder()
                    .startedAt(first.getStartedAt().plusSeconds(1))
                    .build();
            List<UUID> refusedOnce = new ArrayList<UUID>();
            host.placement = s -> {
                if (s.getId().equals(first.getId()) && refusedOnce.isEmpty()) {
                    refusedOnce.add(s.getId());
                    return OfflineSessionPlacement.refusedForNow(AppSessionRefusalReason.CHARACTER_ONLINE, null);
                }
                return OfflineSessionPlacement.placed(s.getSpotId());
            };
            bootWith(first, second);

            hostReady();
            assertTrue(events.events.isEmpty());
            time.advance(rules.restoreBatchPauseMillis());
            assertEquals(
                    Arrays.asList("place:" + first.getId(), "place:" + second.getId(), "startAction:" + second.getId()),
                    host.calls);
            time.advance(rules.restoreBatchPauseMillis());

            assertTrue(host.called("startAction:" + first.getId()));
            assertEquals(2, events.events.size());
        }

        @Test
        void restore_shouldCloseWithRestoreFailed_whenTheHostRefusesForNowEveryAttempt() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            host.placement = s -> OfflineSessionPlacement.refusedForNow(AppSessionRefusalReason.CHARACTER_ONLINE, null);
            bootWith(row);

            hostReady();
            time.advance(AppSessionEngine.RESTORE_ATTEMPTS * rules.restoreBatchPauseMillis());

            assertEquals(LogoutReason.APP_RESTORE_FAILED, store.row(row.getId()).getEndReason());
            int places = 0;
            for (String call : host.calls) {
                places += call.startsWith("place:") ? 1 : 0;
            }
            assertEquals(AppSessionEngine.RESTORE_ATTEMPTS, places);
        }

        @Test
        void restore_shouldCloseWithRestoreFailed_whenNoSpotIsFree() {
            OfflineSession row = openRow(CHAR_ID, 5, 40 * MINUTE);
            host.placement = s -> OfflineSessionPlacement.refused(AppSessionRefusalReason.NO_FREE_SPOT, null);
            bootWith(row);

            hostReady();

            assertEquals(LogoutReason.APP_RESTORE_FAILED, store.row(row.getId()).getEndReason());
            assertFalse(host.called("startAction:"));
            assertFalse(host.called("stopAction:"));
            assertEquals(Arrays.asList(Boolean.FALSE), host.returnFlags);
            CharacterPresenceEvent end = events.single();
            assertFalse(end.isOnline());
            assertEquals(LogoutReason.APP_RESTORE_FAILED, end.getLogoutReason());
        }

        @Test
        void restore_shouldCloseWithRestoreFailed_whenStartActionThrows() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            host.failStartAction = true;
            bootWith(row);

            hostReady();

            assertEquals(LogoutReason.APP_RESTORE_FAILED, store.row(row.getId()).getEndReason());
            assertTrue(host.called("remove:" + row.getId() + ":true"));
        }

        @Test
        void restore_shouldCapTheBatchAtTheEnginesShareOfTheIoPool_whenTheHostAsksForMore() {
            AppSessionEngine capped = new AppSessionEngine(
                    store, host, () -> rules, () -> rulesFromFile, Runnable::run, 2, time, time, 1_000L);
            capped.bindEvents(events);
            store.seed(openRow(1, 1, 0L));
            store.seed(openRow(2, 2, 0L));
            store.seed(openRow(3, 3, 0L));
            capped.boot();
            capped.startScheduling();

            capped.hostReady();
            time.advance(0L);
            assertEquals(2, events.events.size());
            time.advance(rules.restoreBatchPauseMillis());
            assertEquals(3, events.events.size());
        }

        @Test
        void restore_shouldGoInBatchesAtTheHostsPace() {
            rules = rules.toBuilder()
                    .restoreBatchSize(2)
                    .restoreBatchPauseMillis(5_000)
                    .build();
            bootWith(openRow(1, 1, 0L), openRow(2, 2, 0L), openRow(3, 3, 0L));

            hostReady();
            assertEquals(2, events.events.size());
            time.advance(4_999L);
            assertEquals(2, events.events.size());
            time.advance(1L);
            assertEquals(3, events.events.size());
        }
    }

    @Nested
    class Retention {

        @Test
        void boot_shouldDeleteClosedSessionsOlderThanRetention() {
            OfflineSession old = closed(openRow(1, 1, 0L), time.now().minus(31, ChronoUnit.DAYS));
            OfflineSession recent = closed(openRow(2, 2, 0L), time.now().minus(29, ChronoUnit.DAYS));
            OfflineSession open = openRow(3, 3, 0L);

            bootWith(old, recent, open);

            assertNull(store.row(old.getId()));
            assertNotNull(store.row(recent.getId()));
            assertNotNull(store.row(open.getId()));
        }

        @Test
        void boot_shouldDeleteNothing_whenTheRulesAreNotTheFiles() {
            rulesFromFile = false;
            OfflineSession old = closed(openRow(1, 1, 0L), time.now().minus(400, ChronoUnit.DAYS));

            bootWith(old);

            assertNotNull(store.row(old.getId()));
        }

        private OfflineSession closed(OfflineSession row, Instant endedAt) {
            return row.toBuilder()
                    .endedAt(endedAt)
                    .endReason(LogoutReason.APP_STOPPED)
                    .build();
        }
    }

    @Nested
    class Events {

        @Test
        void presence_shouldWaitForBind_whenSessionClosesBeforeThePlatformConnects() {
            AppSessionEngine unbound = new AppSessionEngine(
                    store, host, () -> rules, () -> rulesFromFile, Runnable::run, 4, time, time, 1_000L);
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            store.seed(row);
            unbound.boot();

            unbound.onClientLogin(CHAR_ID);
            assertTrue(events.events.isEmpty());
            unbound.bindEvents(events);

            assertEquals(LogoutReason.APP_LOGIN, events.single().getLogoutReason());
        }

        @Test
        void presence_shouldStillPublishEnd_whenTheTableCloseFails() {
            OfflineSession session = started(request().build());
            store.failCloses = 1;

            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertSame(LogoutReason.APP_STOPPED, events.last().getLogoutReason());
            assertTrue(store.row(session.getId()).isOpen());
        }
    }

    @Nested
    class PendingWrites {

        @Test
        void start_shouldReturnCharacterBusy_whenTheCloseOfTheLastSessionIsStillPending() {
            OfflineSession first = started(request().build());
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            AppSessionStartOutcome actual =
                    engine.start(request().spotId(6).build()).join();

            assertEquals(Status.CHARACTER_BUSY, actual.getStatus());
            assertEquals(first.getId(), actual.getSession().getId());
            assertEquals(LogoutReason.APP_STOPPED, actual.getSession().getEndReason());
            assertEquals(
                    LogoutReason.APP_STOPPED, engine.findActive(CHAR_ID).get().getEndReason());
            assertEquals(1, store.rows.size());
        }

        @Test
        void start_shouldReturnTheClosedSession_whenItsOwnRetryArrivesWhileTheCloseIsPending() {
            AppSessionStartRequest request = request().build();
            OfflineSession first = started(request);
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            AppSessionStartOutcome actual = engine.start(request).join();

            assertEquals(Status.EXISTING, actual.getStatus());
            assertEquals(first.getId(), actual.getSession().getId());
        }

        @Test
        void flushTick_shouldCloseThePendingRowAndFreeTheCharacter_whenTheRetrySucceeds() {
            engine.startScheduling();
            OfflineSession first = started(request().build());
            store.failCloses = 2;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            assertTrue(store.row(first.getId()).isOpen(), "the first retry fails as well");
            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(LogoutReason.APP_STOPPED, store.row(first.getId()).getEndReason());
            assertEquals(0, engine.pendingWriteCount());
            assertFalse(engine.findActive(CHAR_ID).isPresent());
            assertEquals(
                    Status.STARTED,
                    engine.start(request().spotId(6).build()).join().getStatus());
        }

        @Test
        void start_shouldKeepTheCharacterBusyUntilTheRollbackReachesTheTable_whenTheDeleteFails() {
            engine.startScheduling();
            host.failStartAction = true;
            store.failDeletes = 1;
            AppSessionStartOutcome failed = engine.start(request().build()).join();
            host.failStartAction = false;

            AppSessionStartOutcome busy = engine.start(request().build()).join();
            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            AppSessionStartOutcome actual = engine.start(request().build()).join();

            assertEquals(Status.UNAVAILABLE, failed.getStatus());
            assertEquals(Status.CHARACTER_BUSY, busy.getStatus());
            assertEquals(Status.STARTED, actual.getStatus());
        }

        @Test
        void restore_shouldWriteThePendingCloseFirst_whenARestoreBatchRuns() {
            OfflineSession first = openRow(CHAR_ID, 5, 0L);
            bootWith(first, openRow(CHAR_ID + 1, 6, 0L));
            store.failCloses = 1;
            engine.onClientLogin(CHAR_ID);

            hostReady();

            assertEquals(LogoutReason.APP_LOGIN, store.row(first.getId()).getEndReason());
            assertFalse(host.called("place:" + first.getId()));
        }

        @Test
        void shutdown_shouldRetryThePendingClose() {
            OfflineSession first = started(request().build());
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            engine.shutdown();

            assertEquals(LogoutReason.APP_STOPPED, store.row(first.getId()).getEndReason());
        }
    }

    @Nested
    class AcknowledgedEnd {

        private List<CharacterPresenceEvent> ends(UUID sessionId) {
            List<CharacterPresenceEvent> ends = new ArrayList<CharacterPresenceEvent>();
            for (CharacterPresenceEvent event : events.events) {
                if (!event.isOnline() && sessionId.equals(event.getSessionId())) {
                    ends.add(event);
                }
            }
            return ends;
        }

        @Test
        void close_shouldRecordTheEndPublished_whenKafkaAcknowledges() {
            OfflineSession session = started(request().build());

            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertEquals(time.now().truncatedTo(ChronoUnit.SECONDS), store.endPublishedAt(session.getId()));
        }

        @Test
        void close_shouldLeaveTheEndUnpublished_untilKafkaAnswers() {
            events.holdAcks = true;
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();
            assertNull(store.endPublishedAt(session.getId()));

            events.answerAll(null);

            assertNotNull(store.endPublishedAt(session.getId()));
        }

        @Test
        void flushTick_shouldRepublishTheSameEnd_untilKafkaAcknowledges() {
            engine.startScheduling();
            events.holdAcks = true;
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();
            events.answerAll(new IllegalStateException("broker down"));

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            events.answerAll(new IllegalStateException("broker down"));
            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            events.answerAll(null);
            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            List<CharacterPresenceEvent> actual = ends(session.getId());
            assertEquals(3, actual.size(), actual.toString());
            Instant endedAt = store.row(session.getId()).getEndedAt();
            for (CharacterPresenceEvent end : actual) {
                assertEquals(actual.get(0), end);
                assertEquals(LogoutReason.APP_STOPPED, end.getLogoutReason());
                assertEquals(endedAt, UUIDv7.extractCreatedAt(end.getEventId()));
                assertAppFields(session, end);
            }
            assertNotNull(store.endPublishedAt(session.getId()));
        }

        @Test
        void flushTick_shouldNotResendTheEnd_whileKafkaHasNotAnswered() {
            engine.startScheduling();
            events.holdAcks = true;
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(1, ends(session.getId()).size());
        }

        @Test
        void flushTick_shouldRepublishTheEnd_whenRecordingTheAcknowledgementFails() {
            engine.startScheduling();
            store.failMarks = 1;
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();
            assertNull(store.endPublishedAt(session.getId()));

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(2, ends(session.getId()).size());
            assertNotNull(store.endPublishedAt(session.getId()));
        }

        @Test
        void flushTick_shouldRepublishTheEnd_whenTheRowWasClosedBeforeTheRestart() {
            Instant endedAt = time.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
            OfflineSession closed = openRow(CHAR_ID, 5, 0L).toBuilder()
                    .endedAt(endedAt)
                    .endReason(LogoutReason.APP_TIME_UP)
                    .build();
            bootWith(closed);

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            CharacterPresenceEvent actual = events.single();
            assertFalse(actual.isOnline());
            assertEquals(LogoutReason.APP_TIME_UP, actual.getLogoutReason());
            assertEquals(endedAt, UUIDv7.extractCreatedAt(actual.getEventId()));
            assertAppFields(closed, actual);
            assertNotNull(store.endPublishedAt(closed.getId()));
        }

        @Test
        void flushTick_shouldSendTheEndAgain_whenItsCloseReachesTheTableOnRetry() {
            engine.startScheduling();
            OfflineSession session = started(request().build());
            store.failCloses = 1;
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();
            assertNull(store.endPublishedAt(session.getId()), "an acknowledgement does not mark an open row");

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(2, ends(session.getId()).size());
            assertNotNull(store.endPublishedAt(session.getId()));
        }

        @Test
        void close_shouldNotSendTheEndTwice_whenAFlushTickReadsTheClosedRowBeforeTheCloseReturns() {
            engine.startScheduling();
            OfflineSession session = started(request().build());
            store.afterClose = () -> {
                store.afterClose = null;
                time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            };

            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertEquals(1, ends(session.getId()).size());
            assertNotNull(store.endPublishedAt(session.getId()));
        }

        @Test
        void flushTick_shouldNotResendAnEnd_whenKafkaAcknowledgesItWhileTheTickReadsTheTable() {
            engine.startScheduling();
            events.holdAcks = true;
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();
            store.afterUnpublishedRead = () -> {
                store.afterUnpublishedRead = null;
                events.answerAll(null);
            };

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(1, ends(session.getId()).size());
            assertNotNull(store.endPublishedAt(session.getId()));
        }

        @Test
        void flushTick_shouldPageBeyondUnreadableRows_whenTheyFillTheWindow() {
            Instant endedAt = time.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
            List<OfflineSession> unreadable = new ArrayList<OfflineSession>();
            for (int i = 0; i < AppSessionEngine.REPUBLISH_BATCH; i++) {
                OfflineSession row = closedRow(CHAR_ID + i, i, endedAt);
                store.unreadable.add(row.getId());
                unreadable.add(row);
            }
            OfflineSession readable = closedRow(CHAR_ID - 1, 9_999, endedAt.plusSeconds(1));
            unreadable.forEach(store::seed);
            bootWith(readable);

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(readable.getId(), events.single().getSessionId());
            assertNotNull(store.endPublishedAt(readable.getId()));
            for (OfflineSession row : unreadable) {
                assertNull(store.endPublishedAt(row.getId()), "a newer version may still publish it");
            }
        }

        @Test
        void flushTick_shouldSkipARowWhoseEndCannotBeBuilt_andSendTheRest() {
            OfflineSession broken = closedRow(CHAR_ID, 5, Instant.EPOCH.minusSeconds(1));
            OfflineSession fine = closedRow(CHAR_ID + 1, 6, time.now().truncatedTo(ChronoUnit.SECONDS));
            store.seed(broken);
            bootWith(fine);

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertEquals(fine.getId(), events.single().getSessionId());
            assertNull(store.endPublishedAt(broken.getId()));
        }

        @Test
        void flushTick_shouldSendTheRestAndRetryLater_whenSendingOneEndThrows() {
            OfflineSession first = closedRow(CHAR_ID, 5, time.now().minus(2, ChronoUnit.MINUTES));
            OfflineSession second = closedRow(CHAR_ID + 1, 6, time.now().minus(1, ChronoUnit.MINUTES));
            boolean[] thrown = {false};
            engine.bindEvents(new AcknowledgedEvents() {
                @Override
                public void publish(Object event, SendCallback callback) {
                    if (!thrown[0]) {
                        thrown[0] = true;
                        throw new IllegalStateException("simulated producer failure");
                    }
                    events.publish(event, callback);
                }

                @Override
                public void publish(@Nullable Object event) {
                    events.publish(event);
                }

                @Override
                public boolean flush(long timeoutMs) {
                    return true;
                }
            });
            store.seed(first);
            bootWith(second);

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);
            assertEquals(second.getId(), events.single().getSessionId());
            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertNotNull(store.endPublishedAt(first.getId()));
            assertNotNull(store.endPublishedAt(second.getId()));
        }

        @Test
        void onEndAnswered_shouldRecordAcknowledgementsInOneWrite_whenTheyArriveTogether() {
            List<Runnable> io = new ArrayList<Runnable>();
            Executor queued = io::add;
            AppSessionEngine batching =
                    new AppSessionEngine(store, host, () -> rules, () -> rulesFromFile, queued, 4, time, time, 1_000L);
            batching.bindEvents(events);
            events.holdAcks = true;
            List<UUID> ids = new ArrayList<UUID>();
            for (int i = 0; i < 3; i++) {
                CompletableFuture<AppSessionStartOutcome> start = batching.start(
                        request().characterId(CHAR_ID + i).spotId(i).build());
                runAll(io);
                ids.add(start.join().getSession().getId());
                batching.stop(CHAR_ID + i, ActivityType.OFFLINE_FISHING);
                runAll(io);
            }

            events.answerAll(null);
            runAll(io);

            assertEquals(1, store.markWrites);
            for (UUID id : ids) {
                assertNotNull(store.endPublishedAt(id));
            }
        }

        private void runAll(List<Runnable> io) {
            while (!io.isEmpty()) {
                io.remove(0).run();
            }
        }

        private OfflineSession closedRow(int characterId, int spotId, Instant endedAt) {
            return openRow(characterId, spotId, 0L).toBuilder()
                    .endedAt(endedAt)
                    .endReason(LogoutReason.APP_TIME_UP)
                    .build();
        }

        @Test
        void flushTick_shouldRecordTheEndPublishedOnce_whenTheBoundEventsCannotAcknowledge() {
            RecordingEvents recorded = new RecordingEvents();
            engine.bindEvents(new NxEvents() {
                @Override
                public void publish(@Nullable Object event) {
                    recorded.publish(event);
                }

                @Override
                public boolean flush(long timeoutMs) {
                    return true;
                }
            });
            engine.startScheduling();
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            time.advance(AppSessionEngine.FLUSH_INTERVAL_MS);

            assertNotNull(store.endPublishedAt(session.getId()));
            assertEquals(2, recorded.events.size(), "online and the end, never sent again");
        }

        @Test
        void presence_shouldLeaveTheEndUnpublished_whenTheBufferBeforeConnectDropsIt() {
            AppSessionEngine unbound = new AppSessionEngine(
                    store, host, () -> rules, () -> rulesFromFile, Runnable::run, 4, time, time, 1_000L);
            List<OfflineSession> rows = new ArrayList<OfflineSession>();
            for (int i = 0; i <= PresencePublisher.MAX_PENDING; i++) {
                OfflineSession row = openRow(CHAR_ID + i, i, 0L);
                rows.add(row);
                store.seed(row);
            }
            unbound.boot();
            for (OfflineSession row : rows) {
                unbound.onClientLogin(row.getCharacterId());
            }

            unbound.bindEvents(events);

            assertEquals(PresencePublisher.MAX_PENDING, events.events.size());
            assertEquals(1, store.unpublishedEnds().size());
        }
    }

    @Nested
    class OpenSessionsSnapshot {

        @Test
        void startScheduling_shouldPublishASnapshotAtOnceAndEveryMinute() {
            engine.startScheduling();
            time.advance(0L);
            assertEquals(1, events.snapshots.size());

            time.advance(AppSessionsSnapshotPublisher.INTERVAL_MS);

            assertEquals(2, events.snapshots.size());
        }

        @Test
        void snapshot_shouldListRunningAndPendingRestoreSessions() {
            OfflineSession pending = openRow(CHAR_ID + 1, 6, 0L);
            store.seed(pending);
            engine.boot();
            OfflineSession running = started(request().build());
            OfflineSession ended =
                    started(request().characterId(CHAR_ID + 2).spotId(7).build());
            engine.stop(CHAR_ID + 2, ActivityType.OFFLINE_FISHING).join();
            OfflineSession closePending =
                    started(request().characterId(CHAR_ID + 3).spotId(8).build());
            store.failCloses = 1;
            engine.stop(CHAR_ID + 3, ActivityType.OFFLINE_FISHING).join();

            engine.startScheduling();
            time.advance(0L);

            AppSessionsSnapshotEvent actual = events.snapshots.get(0);
            assertEquals(
                    new HashSet<UUID>(Arrays.asList(pending.getId(), running.getId())),
                    new HashSet<UUID>(actual.getSessionIds()));
            assertFalse(actual.getSessionIds().contains(ended.getId()));
            assertFalse(actual.getSessionIds().contains(closePending.getId()));
        }

        @Test
        void snapshot_shouldListAStartingSession_whenItsRowIsStoredAndThePlacementRuns() {
            engine.startScheduling();
            time.advance(0L);
            List<AppSessionsSnapshotEvent> duringPlacement = new ArrayList<AppSessionsSnapshotEvent>();
            host.placement = s -> {
                time.advance(AppSessionsSnapshotPublisher.INTERVAL_MS);
                duringPlacement.add(events.snapshots.get(events.snapshots.size() - 1));
                return OfflineSessionPlacement.placed(s.getSpotId());
            };

            OfflineSession actual = started(request().build());

            assertEquals(1, duringPlacement.size());
            assertTrue(duringPlacement.get(0).getSessionIds().contains(actual.getId()));
            assertFalse(duringPlacement.get(0).getTakenAt().isAfter(actual.getStartedAt()));
        }

        @Test
        void snapshot_shouldCarryAnEmptyList_whenNoSessionIsOpen() {
            engine.startScheduling();
            time.advance(0L);

            AppSessionsSnapshotEvent actual = events.snapshots.get(0);
            assertTrue(actual.getSessionIds().isEmpty());
            assertEquals(time.now(), actual.getTakenAt());
        }
    }

    private static void assertAppFields(OfflineSession session, CharacterPresenceEvent event) {
        assertEquals(SessionSource.APP, event.getSource());
        assertEquals(session.getActivity(), event.getActivity());
        assertEquals(session.getId(), event.getSessionId());
        assertEquals(session.getCharacterId(), event.getCharId());
        assertEquals(session.getAccountName(), event.getAccountName());
        assertEquals(session.getInitiatorIp(), event.getIp());
        assertEquals(session.getHwid(), event.getHwid());
        assertNull(event.getMetadata());
    }

    /** Acknowledges at once unless {@link #holdAcks} is set; then the answers wait in {@link #unanswered}. */
    static final class RecordingEvents implements AcknowledgedEvents {
        final List<CharacterPresenceEvent> events = new ArrayList<CharacterPresenceEvent>();
        final List<AppSessionsSnapshotEvent> snapshots = new ArrayList<AppSessionsSnapshotEvent>();
        final List<SendCallback> unanswered = new ArrayList<SendCallback>();
        boolean holdAcks;

        @Override
        public void publish(@Nullable Object event) {
            if (event instanceof AppSessionsSnapshotEvent) {
                snapshots.add((AppSessionsSnapshotEvent) event);
            } else {
                events.add((CharacterPresenceEvent) event);
            }
        }

        @Override
        public void publish(Object event, SendCallback callback) {
            publish(event);
            if (holdAcks) {
                unanswered.add(callback);
            } else {
                callback.onCompletion(null);
            }
        }

        void answerAll(@Nullable Exception failure) {
            List<SendCallback> answering = new ArrayList<SendCallback>(unanswered);
            unanswered.clear();
            for (SendCallback callback : answering) {
                callback.onCompletion(failure);
            }
        }

        @Override
        public boolean flush(long timeoutMs) {
            return true;
        }

        CharacterPresenceEvent single() {
            assertEquals(1, events.size(), events.toString());
            return events.get(0);
        }

        CharacterPresenceEvent last() {
            return events.get(events.size() - 1);
        }
    }
}
