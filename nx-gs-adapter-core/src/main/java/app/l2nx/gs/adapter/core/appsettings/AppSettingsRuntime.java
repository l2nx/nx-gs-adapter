package app.l2nx.gs.adapter.core.appsettings;

import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.capability.NxAppRules;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.nio.file.Path;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The live {@link AppRules}, replaced as a whole by one volatile write. Holds the defaults until a game server's first
 * load; a login server keeps them.
 */
public final class AppSettingsRuntime implements NxAppRules {

    private static final NxLog log = NxLogFactory.getLogger(AppSettingsRuntime.class);

    private static final AppSettingsRuntime INSTANCE = new AppSettingsRuntime();
    private static final AppSettingsPublisher PUBLISHER = AppSettingsPublisher.viaNxKafka();
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;
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
                ? new AppSettingsWatcher(file, INSTANCE, PUBLISHER::publish)
                : new AppSettingsWatcher(file, INSTANCE, PUBLISHER::publish, source);
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
            PUBLISHER.clear();
            initialized = true;
        }
    }

    public static void connect(SyncTopics topics, @Nullable UUID serverId) {
        PUBLISHER.connect(topics, serverId);
    }

    public static void disconnect() {
        PUBLISHER.disconnect();
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
            PUBLISHER.disconnect();
            PUBLISHER.clear();
            INSTANCE.reset();
            contentSourceForTesting = null;
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
