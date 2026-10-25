package app.l2nx.gs.adapter.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSessionTypeSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.game.GameSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.game.MailSettings;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
import app.l2nx.gs.adapter.api.spi.provider.GameSettingsProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SettingsModuleTest {

    private static final UUID SERVER_ID = UUID.fromString("0192f000-0000-7000-8000-0000000000aa");
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private final List<ProducerRecord<byte[], Object>> sent = new ArrayList<ProducerRecord<byte[], Object>>();
    private final List<RecordingScheduler> schedulers = new ArrayList<RecordingScheduler>();
    private final GameSettingsProvider provider = mock(GameSettingsProvider.class);
    private final RecordingLog log = new RecordingLog();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ConnectContext ctx =
            ConnectContext.builder().serverId(SERVER_ID).syncTopics(topics()).build();

    private boolean loginServer;
    private @Nullable Runnable appChangeListener;
    private int appBuilds;

    private static SyncTopics topics() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put(AppSettings.ENTITY_NAME, "tenant.gs.settings.sync");
        settings.put(GameSettings.ENTITY_NAME, "tenant.gs.settings.sync");
        return new SyncTopics(null, null, null, settings);
    }

    @AfterEach
    void tearDown() {
        for (RecordingScheduler scheduler : schedulers) {
            scheduler.shutdownNow();
        }
    }

    private SettingsModule module() {
        return moduleWith(GameSettingsSource.of(Collections.singletonList(provider), log));
    }

    private SettingsModule moduleWith(GameSettingsSource source) {
        return new SettingsModule(
                () -> loginServer,
                this::appSnapshot,
                listener -> appChangeListener = listener,
                new SettingsPublisher<AppSettings>(
                        AppSettings.ENTITY_NAME,
                        s -> s.toBuilder().updatedAt(Instant.EPOCH).build(),
                        (record, callback) -> sent.add(record),
                        clock,
                        log),
                new SettingsPublisher<GameSettings>(
                        GameSettings.ENTITY_NAME,
                        s -> s.toBuilder().updatedAt(Instant.EPOCH).build(),
                        (record, callback) -> sent.add(record),
                        clock,
                        log),
                () -> source,
                () -> {
                    RecordingScheduler scheduler = new RecordingScheduler();
                    schedulers.add(scheduler);
                    return scheduler;
                });
    }

    private AppSettings appSnapshot(Instant builtAt) {
        appBuilds++;
        return AppSettings.builder()
                .sessionTypes(Collections.singletonList(new AppSessionTypeSettings(ActivityType.OFFLINE_FISHING, true)))
                .recentlyLeftGameMinutes(2)
                .fishingStartCooldownMinutes(15)
                .privateStoreStartCooldownMinutes(5)
                .returnThresholdMinutes(30)
                .restartWindowMinutes(10)
                .fishingMinDurationMs(600_000L)
                .fishingMaxDurationMs(14_400_000L)
                .updatedAt(builtAt)
                .build();
    }

    private static GameSettings settings(long feeAdena, Instant updatedAt) {
        return GameSettings.builder()
                .mail(MailSettings.builder()
                        .feeAdena(feeAdena)
                        .feeAdenaPerAttachment(0L)
                        .build())
                .updatedAt(updatedAt)
                .build();
    }

    private SettingsModule started(SettingsModule module) {
        module.onConnect(ctx);
        module.start();
        return module;
    }

    private List<String> sentEntities() {
        List<String> names = new ArrayList<String>();
        for (ProducerRecord<byte[], Object> record : sent) {
            names.add(((SettingsSyncEvent<?>) record.value()).getEntityName());
        }
        return names;
    }

    private Object payload(int index) {
        return ((SettingsSyncEvent<?>) sent.get(index).value()).getPayload();
    }

    @Nested
    class Connect {

        @Test
        void onConnect_shouldWireWithoutSendingOrArmingTheTick() {
            SettingsModule module = module();

            module.onConnect(ctx);

            assertTrue(sent.isEmpty());
            assertTrue(schedulers.isEmpty());
            assertNotNull(appChangeListener);
        }

        @Test
        void appSettingsChange_shouldSkipThePublish_whenTheContentIsUnchanged() {
            started(module());
            int before = sent.size();

            appChangeListener.run();

            assertEquals(before, sent.size());
            assertEquals(2, appBuilds);
        }
    }

    @Nested
    class Start {

        @Test
        void start_shouldSendBothEntitiesAtOnce() {
            GameSettings snapshot = settings(100L, NOW);
            when(provider.gameSettings(NOW)).thenReturn(snapshot);

            started(module());

            assertEquals(2, sent.size());
            assertTrue(sentEntities().contains(AppSettings.ENTITY_NAME));
            assertSame(snapshot, payload(sentEntities().indexOf(GameSettings.ENTITY_NAME)));
        }

        @Test
        void start_shouldArmTheTickOnePeriodAhead() {
            when(provider.gameSettings(NOW)).thenReturn(settings(100L, NOW));

            started(module());

            assertEquals(1, schedulers.size());
            RecordingScheduler scheduler = schedulers.get(0);
            assertEquals(SettingsModule.TICK_PERIOD_MS, scheduler.initialDelayMs);
            assertEquals(SettingsModule.TICK_PERIOD_MS, scheduler.periodMs);
            assertEquals(TimeUnit.MINUTES.toMillis(5), SettingsModule.TICK_PERIOD_MS);
            assertTrue(scheduler.oneShotDelaysMs.isEmpty());
        }

        @Test
        void start_shouldReplaceTheTick_whenStartedAfterTheNextHandshake() {
            SettingsModule module = started(module());

            started(module);

            assertEquals(2, schedulers.size());
            assertTrue(schedulers.get(0).isShutdown());
            assertFalse(schedulers.get(1).isShutdown());
        }

        @Test
        void start_shouldArmTheTick_whenAGameServerHasNoProvider() {
            SettingsModule module =
                    moduleWith(GameSettingsSource.of(Collections.<GameSettingsProvider>emptyList(), log));

            started(module);

            assertEquals(1, schedulers.size());
            assertEquals(Collections.singletonList(AppSettings.ENTITY_NAME), sentEntities());
            assertTrue(log.errors().isEmpty());
            assertTrue(log.warnings().isEmpty());
        }

        @Test
        void start_shouldStayIdle_whenALoginServerHasNoProvider() {
            loginServer = true;
            SettingsModule module =
                    moduleWith(GameSettingsSource.of(Collections.<GameSettingsProvider>emptyList(), log));

            started(module);

            assertTrue(schedulers.isEmpty());
            assertTrue(sent.isEmpty());
            assertNull(appChangeListener);
        }

        @Test
        void start_shouldSendOnlyGameSettings_whenALoginServerHasAProvider() {
            loginServer = true;
            when(provider.gameSettings(NOW)).thenReturn(settings(100L, NOW));

            started(module());

            assertEquals(1, schedulers.size());
            assertEquals(Collections.singletonList(GameSettings.ENTITY_NAME), sentEntities());
        }
    }

    @Nested
    class FirstGameSettings {

        @Test
        void start_shouldRetrySoon_whenTheHostIsNotReadyAtConnect() {
            when(provider.gameSettings(any(Instant.class))).thenReturn(null);

            started(module());

            assertEquals(
                    Collections.singletonList(SettingsModule.FIRST_GAME_SETTINGS_RETRY_MS),
                    schedulers.get(0).oneShotDelaysMs);
            assertEquals(Collections.singletonList(AppSettings.ENTITY_NAME), sentEntities());
        }

        @Test
        void retry_shouldStop_whenTheFirstSnapshotIsBuilt() throws Exception {
            when(provider.gameSettings(any(Instant.class)))
                    .thenThrow(new IllegalStateException("config loading"))
                    .thenReturn(settings(100L, NOW));
            started(module());
            RecordingScheduler scheduler = schedulers.get(0);

            scheduler.runOneShot(0);

            assertTrue(sentEntities().contains(GameSettings.ENTITY_NAME));
            assertEquals(1, scheduler.oneShotDelaysMs.size());
            assertEquals(1, log.warnings().size());
        }

        @Test
        void retry_shouldKeepGoing_whileTheProviderThrows() throws Exception {
            when(provider.gameSettings(any(Instant.class))).thenThrow(new IllegalStateException("config loading"));
            started(module());
            RecordingScheduler scheduler = schedulers.get(0);

            scheduler.runOneShot(0);

            assertEquals(2, scheduler.oneShotDelaysMs.size());
            assertFalse(sentEntities().contains(GameSettings.ENTITY_NAME));
            assertEquals(1, log.warnings().size());
        }
    }

    @Nested
    class Disconnect {

        @Test
        void onDisconnect_shouldStopTheTickAndDropTheChangeListener() {
            SettingsModule module = started(module());

            module.onDisconnect();

            assertTrue(schedulers.get(0).isShutdown());
            assertNull(appChangeListener);
        }

        @Test
        void stop_shouldShutTheTickThread() {
            SettingsModule module = started(module());

            module.stop();

            assertTrue(schedulers.get(0).isShutdown());
        }

        @Test
        void stop_shouldTolerateANeverStartedTick() {
            SettingsModule module = module();

            module.stop();
            module.onDisconnect();

            assertTrue(schedulers.isEmpty());
        }
    }

    @Nested
    class Tick {

        @Test
        void tick_shouldSendBothEntitiesUnconditionally_whenNothingChanged() {
            when(provider.gameSettings(any(Instant.class))).thenAnswer(call -> settings(100L, call.getArgument(0)));
            SettingsModule module = started(module());

            module.tick();
            module.tick();

            assertEquals(6, sent.size());
        }

        @Test
        void tick_shouldStampTheBuildMoment_whenTheProviderSetAnotherOne() {
            when(provider.gameSettings(any(Instant.class))).thenReturn(settings(100L, NOW.minusSeconds(3600)));
            SettingsModule module = started(module());

            module.tick();

            verify(provider, times(2)).gameSettings(NOW);
            assertEquals(NOW, ((GameSettings) payload(3)).getUpdatedAt());
        }

        @Test
        void tick_shouldSendOnlyAppSettings_whenTheProviderThrows() {
            when(provider.gameSettings(any(Instant.class))).thenThrow(new IllegalStateException("config reloading"));
            SettingsModule module = started(module());

            module.tick();
            module.tick();

            assertEquals(3, sent.size());
            assertFalse(sentEntities().contains(GameSettings.ENTITY_NAME));
            assertEquals(1, log.warnings().size());
        }
    }

    private static final class RecordingScheduler extends ScheduledThreadPoolExecutor {
        long initialDelayMs = -1L;
        long periodMs = -1L;
        final List<Long> oneShotDelaysMs = new ArrayList<Long>();
        final List<Runnable> oneShots = new ArrayList<Runnable>();

        RecordingScheduler() {
            super(1);
        }

        void runOneShot(int index) {
            oneShots.get(index).run();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            initialDelayMs = unit.toMillis(initialDelay);
            periodMs = unit.toMillis(delay);
            return super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            oneShotDelaysMs.add(unit.toMillis(delay));
            oneShots.add(command);
            return super.schedule(() -> {}, 1, TimeUnit.DAYS);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
