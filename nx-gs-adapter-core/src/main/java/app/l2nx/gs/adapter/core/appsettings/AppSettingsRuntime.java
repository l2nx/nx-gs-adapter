package app.l2nx.gs.adapter.core.appsettings;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSessionTypeSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.ReviveSettings;
import app.l2nx.gs.adapter.api.spi.capability.NxAppRules;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * The live {@link AppRules}, replaced as a whole by one volatile write. Holds the defaults until a game server's first
 * load; a login server keeps them.
 */
public final class AppSettingsRuntime implements NxAppRules {

    private static final NxLog log = NxLogFactory.getLogger(AppSettingsRuntime.class);

    private static final AppSettingsRuntime INSTANCE = new AppSettingsRuntime();
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;
    private static volatile boolean loginServer;
    private static volatile @Nullable Runnable changeListener;
    private static @Nullable AppSettingsWatcher watcher;
    private static volatile AppSettingsWatcher.@Nullable ContentSource contentSourceForTesting;

    private volatile Live live = Live.STAND_IN;

    AppSettingsRuntime() {}

    @Override
    public AppRules current() {
        return live.rules;
    }

    void applied(AppRules rules) {
        live = new Live(rules, true);
    }

    /** Also for a missing file; the same rules instance stays live. */
    void matchesFile() {
        Live now = live;
        if (!now.fromFile) {
            live = new Live(now.rules, true);
        }
    }

    void reset() {
        live = Live.STAND_IN;
    }

    /** {@code false} while the defaults only stand in for a file that is rejected, unreadable, half-written or not loaded. */
    boolean fromFile() {
        return live.fromFile;
    }

    public static NxAppRules rules() {
        return INSTANCE;
    }

    public static boolean rulesFromFile() {
        return INSTANCE.fromFile();
    }

    /** {@code true} once a game server loaded its file or a login server settled on the defaults. */
    public static boolean initialized() {
        return initialized;
    }

    /** Synchronous first load, then the watcher thread; idempotent, never throws. */
    public static void loadForGameServer(Path configFile) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                startWatching(AppSettingsFile.location(configFile));
            } finally {
                // only now: NxAdapter.appRules() reads the flag without the lock and must not get the defaults of a
                // first load still in progress
                initialized = true;
            }
        }
    }

    private static void startWatching(Path file) {
        AppSettingsWatcher.ContentSource source = contentSourceForTesting;
        AppSettingsWatcher started = source == null
                ? new AppSettingsWatcher(file, INSTANCE, AppSettingsRuntime::changed)
                : new AppSettingsWatcher(file, INSTANCE, AppSettingsRuntime::changed, source);
        try {
            started.loadAtStart();
        } catch (Throwable t) {
            log.error(
                    "App settings: the first load of {} threw {}",
                    file,
                    t.getClass().getName(),
                    t);
        }
        started.startPolling();
        watcher = started;
    }

    /** Also undoes a game-server load that ran before the host type was known. */
    public static void defaultsForLoginServer() {
        synchronized (LOCK) {
            stopWatcher();
            INSTANCE.reset();
            loginServer = true;
            initialized = true;
        }
    }

    public static boolean loginServer() {
        return loginServer;
    }

    /** Runs on the watcher thread after every applied change; {@code null} removes it. */
    public static void changeListener(@Nullable Runnable listener) {
        changeListener = listener;
    }

    private static void changed(AppRules applied) {
        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    public static AppSettings snapshot(Instant builtAt) {
        return toSettings(INSTANCE.current(), builtAt);
    }

    static AppSettings toSettings(AppRules rules, Instant updatedAt) {
        List<AppSessionTypeSettings> types = new ArrayList<AppSessionTypeSettings>();
        for (ActivityType activity : AppSettingsCatalog.SESSION_TYPE_KEYS.values()) {
            types.add(new AppSessionTypeSettings(activity, rules.sessionTypeEnabled(activity)));
        }
        return AppSettings.builder()
                .sessionTypes(types)
                .recentlyLeftGameMinutes(rules.recentLogoutMinutes())
                .fishingStartCooldownMinutes(rules.fishingStartCooldownMinutes())
                .privateStoreStartCooldownMinutes(rules.privateStoreStartCooldownMinutes())
                .returnThresholdMinutes(rules.returnThresholdMinutes())
                .restartWindowMinutes(rules.restartWindowMinutes())
                .fishingMinDurationMs(TimeUnit.MINUTES.toMillis(rules.fishingMinDurationMinutes()))
                .fishingMaxDurationMs(TimeUnit.MINUTES.toMillis(rules.fishingMaxDurationMinutes()))
                .revive(new ReviveSettings(rules.reviveEnabled()))
                .updatedAt(updatedAt)
                .build();
    }

    public static void shutdown() {
        synchronized (LOCK) {
            stopWatcher();
        }
    }

    private static void stopWatcher() {
        AppSettingsWatcher running = watcher;
        if (running != null) {
            running.stopPolling();
            watcher = null;
        }
    }

    static void contentSourceForTesting(AppSettingsWatcher.@Nullable ContentSource source) {
        contentSourceForTesting = source;
    }

    static boolean watchingForTesting() {
        synchronized (LOCK) {
            return watcher != null;
        }
    }

    public static void resetForTesting() {
        synchronized (LOCK) {
            stopWatcher();
            INSTANCE.reset();
            contentSourceForTesting = null;
            changeListener = null;
            loginServer = false;
            initialized = false;
        }
    }

    /** The rules and where they come from, swapped together so a reader never pairs one with the other's origin. */
    private static final class Live {
        static final Live STAND_IN = new Live(AppRules.defaults(), false);

        final AppRules rules;
        final boolean fromFile;

        Live(AppRules rules, boolean fromFile) {
            this.rules = rules;
            this.fromFile = fromFile;
        }
    }
}
