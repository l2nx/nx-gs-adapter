package app.l2nx.gs.adapter.core.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
