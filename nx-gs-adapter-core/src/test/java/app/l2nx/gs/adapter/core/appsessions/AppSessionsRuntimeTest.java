package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.ops.model.ModuleStates;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionCheckOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.session.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import app.l2nx.gs.adapter.core.schema.UnusableJdbcConnectionSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppSessionsRuntimeTest {

    @AfterEach
    void reset() {
        AppSessionsRuntime.resetForTesting();
    }

    @Nested
    class Boot {

        @Test
        void boot_shouldDisableTheEngine_whenNoHostIsRegistered() {
            AppSessionsRuntime.boot(
                    Runnable::run,
                    2,
                    Collections::<OfflineSessionHost>emptyList,
                    () -> Collections.<JdbcConnectionSource>singletonList(new UnusableJdbcConnectionSource()));

            assertEquals(ModuleStates.DISABLED, AppSessionsRuntime.moduleState());
            assertNull(AppSessionsRuntime.engine());
            AppSessionStartOutcome actual =
                    AppSessionsRuntime.facade().start(request()).join();
            assertEquals(AppSessionStartOutcome.Status.UNAVAILABLE, actual.getStatus());
        }

        @Test
        void boot_shouldFail_whenTwoHostsAreRegistered() {
            AppSessionsRuntime.boot(
                    Runnable::run,
                    2,
                    () -> Arrays.<OfflineSessionHost>asList(new FakeOfflineSessionHost(), new FakeOfflineSessionHost()),
                    () -> Collections.<JdbcConnectionSource>singletonList(new UnusableJdbcConnectionSource()));

            assertEquals(ModuleStates.FAILED, AppSessionsRuntime.moduleState());
            assertNull(AppSessionsRuntime.engine());
        }

        @Test
        void boot_shouldFail_whenTheTableCannotBeRead() {
            AppSessionsRuntime.boot(
                    Runnable::run,
                    2,
                    () -> Collections.<OfflineSessionHost>singletonList(new FakeOfflineSessionHost()),
                    () -> Collections.<JdbcConnectionSource>singletonList(new UnusableJdbcConnectionSource()));

            assertEquals(ModuleStates.FAILED, AppSessionsRuntime.moduleState());
            assertFalse(AppSessionsRuntime.facade().findActive(1).isPresent());
        }
    }

    @Nested
    class Facade {

        @Test
        void onClientLogin_shouldReturnAtOnce_whenAdapterWasNeverStarted() {
            assertEquals(
                    ClientLoginOutcome.Status.NONE,
                    AppSessionsRuntime.facade().onClientLogin(1).getStatus());
        }

        @Test
        void onClientLogin_shouldBePendingAndCloseLater_whenTheBootWaitRunsOut() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession open = openSession(7);
            store.seed(open);
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.booting();

            ClientLoginOutcome actual = facade.onClientLogin(7);
            facade.ready(bootedEngine(store));

            assertEquals(ClientLoginOutcome.Status.PENDING, actual.getStatus());
            assertNotNull(store.row(open.getId()).getEndReason());
        }

        @Test
        void onClientLogin_shouldKeepTheInterruptAndCloseLater_whenInterruptedWhileWaitingForTheBoot() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession open = openSession(7);
            store.seed(open);
            AppSessionsFacade facade = new AppSessionsFacade(5_000L);
            facade.booting();

            Thread.currentThread().interrupt();
            ClientLoginOutcome actual = facade.onClientLogin(7);
            boolean interrupted = Thread.interrupted();
            facade.ready(bootedEngine(store));

            assertEquals(ClientLoginOutcome.Status.PENDING, actual.getStatus());
            assertTrue(interrupted, "the interrupt flag must survive");
            assertNotNull(store.row(open.getId()).getEndReason());
        }

        @Test
        void onClientLogin_shouldWaitForTheBoot_whenEngineIsBooting() throws Exception {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession open = openSession(7);
            store.seed(open);
            AppSessionsFacade facade = new AppSessionsFacade(5_000L);
            facade.booting();
            ExecutorService login = Executors.newSingleThreadExecutor();
            try {
                CountDownLatch entered = new CountDownLatch(1);
                Future<ClientLoginOutcome> closed = login.submit(() -> {
                    entered.countDown();
                    return facade.onClientLogin(7);
                });
                entered.await(1, TimeUnit.SECONDS);

                facade.ready(bootedEngine(store));

                ClientLoginOutcome actual = closed.get(2, TimeUnit.SECONDS);
                assertEquals(ClientLoginOutcome.Status.CLOSED, actual.getStatus());
                assertNotNull(store.row(open.getId()).getEndReason());
            } finally {
                login.shutdownNow();
            }
        }
    }

    @Nested
    class Check {

        @Test
        void check_shouldBeUnavailable_whenTheEngineDoesNotRun() {
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.booting();

            assertEquals(
                    AppSessionCheckOutcome.Status.UNAVAILABLE,
                    facade.check(7, "harbor", 1).join().getStatus());
        }

        @ParameterizedTest(name = "spotPool=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "a-spot-pool-name-longer-than-32-chars"})
        void check_shouldBeUnavailable_whenTheStartRequestRejectsTheSpotPool(String spotPool) {
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.ready(bootedEngine(new InMemoryOfflineSessionStore()));

            AppSessionCheckOutcome actual = facade.check(7, spotPool, 1).join();

            assertEquals(AppSessionCheckOutcome.Status.UNAVAILABLE, actual.getStatus());
            assertTrue(actual.getDetail().startsWith("AppSessionStartRequest.spotPool"), actual.getDetail());
        }

        @Test
        void check_shouldBeUnavailable_whenTheStartRequestRejectsTheSpotId() {
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.ready(bootedEngine(new InMemoryOfflineSessionStore()));

            assertEquals(
                    AppSessionCheckOutcome.Status.UNAVAILABLE,
                    facade.check(7, "harbor", -1).join().getStatus());
        }

        @Test
        void check_shouldTrimTheSpotPool_asTheStartRequestDoes() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            store.seed(openSession(7));
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.ready(bootedEngine(store));

            assertEquals(
                    AppSessionCheckOutcome.Status.SPOT_TAKEN,
                    facade.check(8, " harbor ", 1).join().getStatus());
        }
    }

    @Nested
    class FindOpen {

        @Test
        void findOpen_shouldListSessionsAwaitingTheRestore_whenHostReadyWasNotCalled() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession open = openSession(7);
            store.seed(open);
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.ready(bootedEngine(store));

            List<OfflineSession> actual = facade.findOpen(open.getActivity());

            assertEquals(1, actual.size());
            assertEquals(open.getId(), actual.get(0).getId());
        }

        @Test
        void findOpen_shouldBeEmpty_whenTheEngineDoesNotRun() {
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.booting();

            assertTrue(facade.findOpen(ActivityType.OFFLINE_FISHING).isEmpty());
        }
    }

    @Nested
    class FindLastStarted {

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("app.l2nx.gs.adapter.core.appsessions.AppSessionsRuntimeTest#lastStartedWithoutAnEngine")
        void findLastStarted_shouldCompleteExceptionally_whenTheEngineDoesNotRun(
                AppSessionsFacade.State state, String read) {
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            if (state == AppSessionsFacade.State.BOOTING) {
                facade.booting();
            } else if (state == AppSessionsFacade.State.DISABLED) {
                facade.disable();
            }

            CompletableFuture<Optional<OfflineSession>> actual = read.equals("byCharacter")
                    ? facade.findLastStartedByCharacter(7, ActivityType.OFFLINE_PRIVATE_STORE)
                    : facade.findLastStartedByMasterAccount(UUID.randomUUID(), ActivityType.OFFLINE_FISHING);

            assertEquals(state, facade.state());
            CompletionException thrown = assertThrows(CompletionException.class, actual::join);
            assertInstanceOf(IllegalStateException.class, thrown.getCause());
        }

        @Test
        void findLastStartedByCharacter_shouldReadTheEngine_whenItRuns() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession closed = openSession(7).toBuilder()
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .build();
            store.seed(closed);
            store.close(closed.getId(), 0L, closed.getStartedAt().plusSeconds(30), LogoutReason.APP_STOPPED);
            AppSessionsFacade facade = new AppSessionsFacade(10L);
            facade.ready(bootedEngine(store));

            Optional<OfflineSession> actual = facade.findLastStartedByCharacter(7, ActivityType.OFFLINE_PRIVATE_STORE)
                    .join();

            assertEquals(closed.getId(), actual.get().getId());
        }
    }

    static Stream<Arguments> lastStartedWithoutAnEngine() {
        List<Arguments> cases = new ArrayList<Arguments>();
        for (AppSessionsFacade.State state : Arrays.asList(
                AppSessionsFacade.State.IDLE, AppSessionsFacade.State.BOOTING, AppSessionsFacade.State.DISABLED)) {
            cases.add(Arguments.of(state, "byCharacter"));
            cases.add(Arguments.of(state, "byMasterAccount"));
        }
        return cases.stream();
    }

    @Nested
    class HostReady {

        @Test
        void hostReady_shouldBeRemembered_whenCalledWhileTheEngineBoots() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession open = openSession(7);
            store.seed(open);
            FakeOfflineSessionHost host = new FakeOfflineSessionHost();
            ManualScheduler time = new ManualScheduler();
            AppSessionEngine engine = new AppSessionEngine(
                    store, host, AppRules::defaults, () -> true, Runnable::run, 4, time, time, 1_000L);
            AppSessionsFacade facade = new AppSessionsFacade(5_000L);
            facade.booting();

            facade.hostReady();
            engine.boot();
            facade.ready(engine);
            engine.startScheduling();
            time.advance(0L);

            assertTrue(host.called("place:" + open.getId()));
        }
    }

    private static OfflineSession openSession(int characterId) {
        return OfflineSession.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .characterId(characterId)
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("acc")
                .spotPool("harbor")
                .spotId(1)
                .startedAt(new ManualScheduler().now())
                .build();
    }

    private static AppSessionEngine bootedEngine(InMemoryOfflineSessionStore store) {
        ManualScheduler time = new ManualScheduler();
        AppSessionEngine engine = new AppSessionEngine(
                store,
                new FakeOfflineSessionHost(),
                AppRules::defaults,
                () -> true,
                Runnable::run,
                4,
                time,
                time,
                1_000L);
        engine.boot();
        return engine;
    }

    private static AppSessionStartRequest request() {
        return AppSessionStartRequest.builder()
                .idempotencyKey(UUID.randomUUID())
                .characterId(1)
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("acc")
                .spotPool("harbor")
                .spotId(1)
                .build();
    }
}
