package app.l2nx.gs.adapter.core.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSessionTypeSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSettingsRuntimeTest {

    @TempDir
    Path dir;

    private Path config;

    @BeforeEach
    void setUp() throws IOException {
        AppSettingsRuntime.resetForTesting();
        config = dir.resolve("l2nx.properties");
        Files.write(
                dir.resolve(AppSettingsFile.FILE_NAME),
                "app.session.return-threshold-minutes=45\n".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        AppSettingsRuntime.resetForTesting();
    }

    /** What {@code NxAdapter.appRules()} does on a game server. */
    private AppRules appRulesOfAGameServer() {
        if (!AppSettingsRuntime.initialized()) {
            AppSettingsRuntime.loadForGameServer(config);
        }
        return AppSettingsRuntime.rules().current();
    }

    @Nested
    class LoadForGameServer {

        @Test
        void loadForGameServer_shouldReportInitializedOnlyAfterTheFirstLoad() throws Exception {
            CountDownLatch loading = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AppSettingsRuntime.contentSourceForTesting(f -> {
                loading.countDown();
                await(release);
                return Files.readAllBytes(f);
            });
            CompletableFuture<AppRules> first = CompletableFuture.supplyAsync(() -> appRulesOfAGameServer());
            assertTrue(loading.await(5, TimeUnit.SECONDS));

            boolean initializedDuringTheLoad = AppSettingsRuntime.initialized();
            CompletableFuture<AppRules> concurrent = CompletableFuture.supplyAsync(() -> appRulesOfAGameServer());
            release.countDown();

            assertFalse(initializedDuringTheLoad);
            assertEquals(45, first.get(5, TimeUnit.SECONDS).returnThresholdMinutes());
            assertEquals(45, concurrent.get(5, TimeUnit.SECONDS).returnThresholdMinutes());
        }

        @Test
        void loadForGameServer_shouldTakeTheRulesForTheFiles_whenTheFileIsApplied() {
            AppSettingsRuntime.loadForGameServer(config);

            assertTrue(AppSettingsRuntime.rulesFromFile());
        }

        @Test
        void loadForGameServer_shouldNotTakeTheDefaultsForTheFile_whenTheFileIsRejected() throws IOException {
            Files.write(
                    dir.resolve(AppSettingsFile.FILE_NAME),
                    "app.history.retention-days=1\n".getBytes(StandardCharsets.UTF_8));

            AppSettingsRuntime.loadForGameServer(config);

            assertSame(AppRules.defaults(), AppSettingsRuntime.rules().current());
            assertFalse(AppSettingsRuntime.rulesFromFile());
        }
    }

    @Nested
    class DefaultsForLoginServer {

        @Test
        void defaultsForLoginServer_shouldStopTheWatcherAndResetTheRules_whenAGameServerLoadRan() {
            AppSettingsRuntime.loadForGameServer(config);
            assertEquals(45, AppSettingsRuntime.rules().current().returnThresholdMinutes());

            AppSettingsRuntime.defaultsForLoginServer();

            assertSame(AppRules.defaults(), AppSettingsRuntime.rules().current());
            assertFalse(AppSettingsRuntime.watchingForTesting());
            assertTrue(AppSettingsRuntime.initialized());
        }

        @Test
        void defaultsForLoginServer_shouldWatchNothing() {
            AppSettingsRuntime.defaultsForLoginServer();

            assertSame(AppRules.defaults(), AppSettingsRuntime.rules().current());
            assertFalse(AppSettingsRuntime.watchingForTesting());
        }
    }

    @Nested
    class Publication {

        @Test
        void loginServer_shouldBeTrue_whenTheJvmSettledOnTheLoginServerDefaults() {
            AppSettingsRuntime.loadForGameServer(config);

            AppSettingsRuntime.defaultsForLoginServer();

            assertTrue(AppSettingsRuntime.loginServer());
        }

        @Test
        void loginServer_shouldBeFalse_whenAGameServerLoadedItsFile() {
            AppSettingsRuntime.loadForGameServer(config);

            assertFalse(AppSettingsRuntime.loginServer());
        }

        @Test
        void changeListener_shouldRun_whenTheWatcherAppliesTheFile() {
            AtomicInteger runs = new AtomicInteger();
            AppSettingsRuntime.changeListener(runs::incrementAndGet);

            AppSettingsRuntime.loadForGameServer(config);

            assertEquals(1, runs.get());
        }

        @Test
        void snapshot_shouldCarryTheLiveRulesAndTheBuildMoment() {
            AppSettingsRuntime.loadForGameServer(config);
            Instant builtAt = Instant.parse("2026-10-25T12:00:00Z");

            AppSettings actual = AppSettingsRuntime.snapshot(builtAt);

            assertEquals(45, actual.getReturnThresholdMinutes());
            assertEquals(builtAt, actual.getUpdatedAt());
        }
    }

    @Nested
    class ToSettings {

        @Test
        void toSettings_shouldCarryEverySessionTypeAndTheSnapshotKeys() {
            AppRules rules = AppRules.builder()
                    .sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)
                    .fishingSpotPool("harbor")
                    .recentLogoutMinutes(3)
                    .fishingStartCooldownMinutes(20)
                    .privateStoreStartCooldownMinutes(8)
                    .returnThresholdMinutes(40)
                    .restartWindowMinutes(25)
                    .fishingMinDurationMinutes(10)
                    .fishingMaxDurationMinutes(240)
                    .build();

            AppSettings actual = AppSettingsRuntime.toSettings(rules, Instant.parse("2026-10-23T10:00:00Z"));

            AppSettings expected = AppSettings.builder()
                    .sessionTypes(Arrays.asList(
                            new AppSessionTypeSettings(ActivityType.OFFLINE_PRIVATE_STORE, false),
                            new AppSessionTypeSettings(ActivityType.OFFLINE_FISHING, true)))
                    .recentlyLeftGameMinutes(3)
                    .fishingStartCooldownMinutes(20)
                    .privateStoreStartCooldownMinutes(8)
                    .returnThresholdMinutes(40)
                    .restartWindowMinutes(25)
                    .fishingMinDurationMs(600_000L)
                    .fishingMaxDurationMs(14_400_000L)
                    .updatedAt(Instant.parse("2026-10-23T10:00:00Z"))
                    .build();
            assertEquals(expected, actual);
        }

        @Test
        void toSettings_shouldIgnoreValuesOutsideTheSnapshot() {
            Instant at = Instant.parse("2026-10-23T10:00:00Z");

            AppSettings actual = AppSettingsRuntime.toSettings(
                    AppRules.builder()
                            .fishingFloatDistance(200)
                            .restoreBatchSize(2)
                            .build(),
                    at);

            assertEquals(AppSettingsRuntime.toSettings(AppRules.defaults(), at), actual);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
