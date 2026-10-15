package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome.Status;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.OfflineSessionPlacement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppSessionEngineTest {

    private static final long MINUTE = 60_000L;
    private static final int CHAR_ID = 268_480_001;

    private InMemoryOfflineSessionStore store;
    private FakeOfflineSessionHost host;
    private ManualScheduler time;
    private RecordingEvents events;
    private AppSessionEngine engine;

    @BeforeEach
    void setUp() {
        store = new InMemoryOfflineSessionStore();
        host = new FakeOfflineSessionHost();
        time = new ManualScheduler();
        events = new RecordingEvents();
        engine = newEngine();
    }

    private AppSessionEngine newEngine() {
        AppSessionEngine created = new AppSessionEngine(store, host, Runnable::run, 4, time, time, 1_000L);
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
        void start_shouldStoreTheHostsSpot_whenHostPlacesOnAnotherSpot() {
            host.placement = s -> OfflineSessionPlacement.placed(7);

            OfflineSession actual = started(request().build());

            assertEquals(7, actual.getSpotId());
            assertEquals(7, store.row(actual.getId()).getSpotId());
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

            time.advance(host.settings.getRestoreDelayMs());
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

            time.advance(host.settings.getRestoreDelayMs());

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
            time.advance(host.settings.getRestoreDelayMs());

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
            time.advance(host.settings.getRestoreDelayMs());

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
                AppSessionEngine slow = new AppSessionEngine(store, host, io, 4, time, time, 50L);
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

        @Test
        void onActionEnded_shouldCloseWithTheHostsReason_whenSessionRuns() {
            OfflineSession session = started(request().build());

            engine.onActionEnded(session.getId(), LogoutReason.APP_OUT_OF_BAIT);

            assertEquals(
                    LogoutReason.APP_OUT_OF_BAIT, store.row(session.getId()).getEndReason());
            assertTrue(host.called("stopAction:" + session.getId()));
            assertEquals(LogoutReason.APP_OUT_OF_BAIT, events.last().getLogoutReason());
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
        void updateTypeState_shouldReturnFalse_whenSessionIsClosed() {
            OfflineSession session = started(request().build());
            engine.stop(CHAR_ID, ActivityType.OFFLINE_FISHING).join();

            assertFalse(engine.updateTypeState(session.getId(), "{}").join());
        }
    }

    @Nested
    class Restore {

        @Test
        void restore_shouldPlaceStartAndPublishResumed_whenDelayPassed() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            bootWith(row);

            time.advance(host.settings.getRestoreDelayMs() - 1);
            assertFalse(host.called("place:"));
            time.advance(1);

            assertEquals(Arrays.asList("place:" + row.getId(), "startAction:" + row.getId()), host.calls);
            CharacterPresenceEvent event = events.single();
            assertTrue(event.isOnline());
            assertTrue(event.isResumed());
            assertEquals(row.getId(), event.getSessionId());
            assertAppFields(row, event);
        }

        @Test
        void restore_shouldStoreTheNewSpot_whenHostPlacesOnAnotherSpotOfThePool() {
            OfflineSession row = openRow(CHAR_ID, 5, 0L);
            host.placement = s -> OfflineSessionPlacement.placed(8);
            bootWith(row);

            time.advance(host.settings.getRestoreDelayMs());

            assertEquals(8, store.row(row.getId()).getSpotId());
            assertEquals(8, engine.findActive(CHAR_ID).get().getSpotId());
        }

        @Test
        void restore_shouldCloseWithRestoreFailed_whenNoSpotIsFree() {
            OfflineSession row = openRow(CHAR_ID, 5, 40 * MINUTE);
            host.placement = s -> OfflineSessionPlacement.refused(AppSessionRefusalReason.NO_FREE_SPOT, null);
            bootWith(row);

            time.advance(host.settings.getRestoreDelayMs());

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

            time.advance(host.settings.getRestoreDelayMs());

            assertEquals(LogoutReason.APP_RESTORE_FAILED, store.row(row.getId()).getEndReason());
            assertTrue(host.called("remove:" + row.getId() + ":true"));
        }

        @Test
        void restore_shouldCapTheBatchAtTheEnginesShareOfTheIoPool_whenTheHostAsksForMore() {
            AppSessionEngine capped = new AppSessionEngine(store, host, Runnable::run, 2, time, time, 1_000L);
            capped.bindEvents(events);
            store.seed(openRow(1, 1, 0L));
            store.seed(openRow(2, 2, 0L));
            store.seed(openRow(3, 3, 0L));
            capped.boot();
            capped.startScheduling();

            time.advance(host.settings.getRestoreDelayMs());
            assertEquals(2, events.events.size());
            time.advance(host.settings.getRestoreBatchIntervalMs());
            assertEquals(3, events.events.size());
        }

        @Test
        void restore_shouldGoInBatchesAtTheHostsPace() {
            host.settings = host.settings.toBuilder()
                    .restoreBatchSize(2)
                    .restoreBatchIntervalMs(5_000L)
                    .build();
            bootWith(openRow(1, 1, 0L), openRow(2, 2, 0L), openRow(3, 3, 0L));

            time.advance(host.settings.getRestoreDelayMs());
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
        void boot_shouldKeepEverything_whenRetentionIsZero() {
            host.settings = host.settings.toBuilder().retentionDays(0).build();
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
            AppSessionEngine unbound = new AppSessionEngine(store, host, Runnable::run, 4, time, time, 1_000L);
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

            time.advance(host.settings.getRestoreDelayMs());

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

    static final class RecordingEvents implements NxEvents {
        final List<CharacterPresenceEvent> events = new ArrayList<CharacterPresenceEvent>();

        @Override
        public void publish(@Nullable Object event) {
            events.add((CharacterPresenceEvent) event);
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
