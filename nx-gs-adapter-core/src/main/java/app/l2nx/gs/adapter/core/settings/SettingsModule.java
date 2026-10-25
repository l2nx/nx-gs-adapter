package app.l2nx.gs.adapter.core.settings;

import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.game.GameSettings;
import app.l2nx.gs.adapter.api.spi.AdapterModule;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
import app.l2nx.gs.adapter.core.appsettings.AppSettingsRuntime;
import app.l2nx.gs.commons.concurrent.DaemonThreadFactory;
import app.l2nx.gs.commons.concurrent.SafeRunnable;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Resends every settings entity every 5 minutes unconditionally, so a reload the adapter cannot observe or a lost
 * snapshot heals within one period. The tick only dispatches; build and send hop to the IO pool.
 */
public final class SettingsModule implements AdapterModule {

    private static final NxLog log = NxLogFactory.getLogger(SettingsModule.class);

    static final String NAME = "settings";
    static final String TICK_THREAD_NAME = "nx-settings-tick";
    static final long TICK_PERIOD_MS = 5L * 60L * 1000L;
    static final long FIRST_GAME_SETTINGS_RETRY_MS = 15_000L;

    private final BooleanSupplier loginServer;
    private final Function<Instant, AppSettings> appSnapshot;
    private final Consumer<@Nullable Runnable> appChanges;
    private final SettingsPublisher<AppSettings> appSettings;
    private final SettingsPublisher<GameSettings> gameSettings;
    private final Supplier<GameSettingsSource> gameSettingsSources;
    private final Supplier<ScheduledExecutorService> tickSchedulers;
    private final AtomicBoolean gameSettingsBuilt = new AtomicBoolean();

    private @Nullable ScheduledExecutorService tick;
    private volatile GameSettingsSource gameSettingsSource = GameSettingsSource.none();
    private volatile Executor io = Runnable::run;

    public SettingsModule() {
        this(
                AppSettingsRuntime::loginServer,
                AppSettingsRuntime::snapshot,
                AppSettingsRuntime::changeListener,
                SettingsPublisher.<AppSettings>viaNxKafka(
                        AppSettings.ENTITY_NAME,
                        s -> s.toBuilder().updatedAt(Instant.EPOCH).build()),
                SettingsPublisher.<GameSettings>viaNxKafka(
                        GameSettings.ENTITY_NAME,
                        s -> s.toBuilder().updatedAt(Instant.EPOCH).build()),
                GameSettingsSource::shared,
                () -> Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory.named(TICK_THREAD_NAME, log)));
    }

    SettingsModule(
            BooleanSupplier loginServer,
            Function<Instant, AppSettings> appSnapshot,
            Consumer<@Nullable Runnable> appChanges,
            SettingsPublisher<AppSettings> appSettings,
            SettingsPublisher<GameSettings> gameSettings,
            Supplier<GameSettingsSource> gameSettingsSources,
            Supplier<ScheduledExecutorService> tickSchedulers) {
        this.loginServer = loginServer;
        this.appSnapshot = appSnapshot;
        this.appChanges = appChanges;
        this.appSettings = appSettings;
        this.gameSettings = gameSettings;
        this.gameSettingsSources = gameSettingsSources;
        this.tickSchedulers = tickSchedulers;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void onConnect(ConnectContext ctx) {
        gameSettingsSource = gameSettingsSources.get();
        io = ctx.io();
        appSettings.connect(ctx.getSyncTopics(), ctx.getServerId(), ctx.io());
        gameSettings.connect(ctx.getSyncTopics(), ctx.getServerId(), ctx.io());
        if (!loginServer.getAsBoolean()) {
            appChanges.accept(this::appSettingsChanged);
        }
    }

    @Override
    public synchronized void start() {
        stopTick();
        boolean app = !loginServer.getAsBoolean();
        GameSettingsSource source = gameSettingsSource;
        if (!app && !source.present()) {
            log.debug("Settings sync idle: a login server without a GameSettingsProvider");
            return;
        }
        ScheduledExecutorService started = tickSchedulers.get();
        tick = started;
        if (app) {
            hop(this::sendAppSettings);
        }
        if (source.present()) {
            gameSettingsBuilt.set(false);
            hop(() -> firstGameSettings(started));
        }
        // SafeRunnable: a throw would cancel the periodic task and silently end every later tick
        started.scheduleWithFixedDelay(
                SafeRunnable.wrap(this::tick, log), TICK_PERIOD_MS, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        stopTick();
    }

    @Override
    public void onDisconnect() {
        stopTick();
        appChanges.accept(null);
        appSettings.disconnect();
        gameSettings.disconnect();
    }

    void tick() {
        if (!loginServer.getAsBoolean()) {
            hop(this::sendAppSettings);
        }
        if (gameSettingsSource.present()) {
            hop(this::sendGameSettings);
        }
    }

    private void appSettingsChanged() {
        try {
            hop(() -> appSettings.publish(appSnapshot));
        } catch (RejectedExecutionException shuttingDown) {
            log.debug("App settings change not published, the IO pool is shut down");
        }
    }

    private void sendAppSettings() {
        appSettings.resend(appSnapshot);
    }

    private boolean sendGameSettings() {
        SettingsPublisher.Build build = gameSettings.startBuild();
        GameSettings snapshot = gameSettingsSource.snapshot(build.builtAt);
        if (snapshot == null) {
            return false;
        }
        gameSettingsBuilt.set(true);
        gameSettings.resend(build, snapshot);
        return true;
    }

    private void firstGameSettings(ScheduledExecutorService scheduler) {
        if (gameSettingsBuilt.get() || sendGameSettings()) {
            return;
        }
        try {
            scheduler.schedule(
                    SafeRunnable.wrap(() -> hop(() -> firstGameSettings(scheduler)), log),
                    FIRST_GAME_SETTINGS_RETRY_MS,
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            log.debug("Game settings retry dropped, the tick was stopped");
        }
    }

    private void hop(Runnable work) {
        io.execute(SafeRunnable.wrap(work, log));
    }

    private synchronized void stopTick() {
        ScheduledExecutorService running = tick;
        if (running != null) {
            running.shutdownNow();
            tick = null;
        }
    }
}
