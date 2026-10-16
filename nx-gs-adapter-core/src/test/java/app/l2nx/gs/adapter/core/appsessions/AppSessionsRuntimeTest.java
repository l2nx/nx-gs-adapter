package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.ops.model.ModuleStates;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import app.l2nx.gs.adapter.core.schema.UnusableJdbcConnectionSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            AppSessionsFacade facade = new AppSessionsFacade(AppSettingsPublisher.viaNxKafka(), 10L);
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
            AppSessionsFacade facade = new AppSessionsFacade(AppSettingsPublisher.viaNxKafka(), 5_000L);
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
            AppSessionsFacade facade = new AppSessionsFacade(AppSettingsPublisher.viaNxKafka(), 5_000L);
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
    class HostReady {

        @Test
        void hostReady_shouldBeRemembered_whenCalledWhileTheEngineBoots() {
            InMemoryOfflineSessionStore store = new InMemoryOfflineSessionStore();
            OfflineSession open = openSession(7);
            store.seed(open);
            FakeOfflineSessionHost host = new FakeOfflineSessionHost();
            ManualScheduler time = new ManualScheduler();
            AppSessionEngine engine = new AppSessionEngine(store, host, Runnable::run, 4, time, time, 1_000L);
            AppSessionsFacade facade = new AppSessionsFacade(AppSettingsPublisher.viaNxKafka(), 5_000L);
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
        AppSessionEngine engine =
                new AppSessionEngine(store, new FakeOfflineSessionHost(), Runnable::run, 4, time, time, 1_000L);
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
