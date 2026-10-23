package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.ops.model.ModuleStates;
import app.l2nx.gs.adapter.api.spi.capability.NxAppSessions;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import app.l2nx.gs.adapter.core.appsettings.AppSettingsRuntime;
import app.l2nx.gs.commons.concurrent.DaemonThreadFactory;
import app.l2nx.gs.commons.spi.ServiceProviders;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * JVM-wide holder of the engine, which boots independently of the platform connection so a login is answered from
 * JVM start.
 */
public final class AppSessionsRuntime {

    private static final NxLog log = NxLogFactory.getLogger(AppSessionsRuntime.class);

    static final long BOOT_WAIT_MS = 30_000L;

    private static final AppSessionsFacade FACADE = new AppSessionsFacade(BOOT_WAIT_MS);
    private static volatile String moduleState = ModuleStates.INIT;

    private AppSessionsRuntime() {}

    public static NxAppSessions facade() {
        return FACADE;
    }

    public static void booting() {
        FACADE.booting();
    }

    public static void disable(String reason) {
        log.info("App sessions disabled: {}", reason);
        moduleState = ModuleStates.DISABLED;
        FACADE.disable();
    }

    /** Never throws. The engine holds at most half of {@code ioWorkers}, the rest stays for the other modules. */
    public static void boot(Executor io, int ioWorkers) {
        boot(
                io,
                ioWorkers,
                () -> ServiceProviders.loadAll(OfflineSessionHost.class, AppSessionsRuntime.class),
                () -> ServiceProviders.loadAll(JdbcConnectionSource.class, AppSessionsRuntime.class));
    }

    static void boot(
            Executor io,
            int ioWorkers,
            Supplier<List<OfflineSessionHost>> hosts,
            Supplier<List<JdbcConnectionSource>> sources) {
        try {
            List<OfflineSessionHost> foundHosts = hosts.get();
            if (foundHosts.isEmpty()) {
                disable("no OfflineSessionHost on the classpath");
                return;
            }
            if (foundHosts.size() > 1) {
                fail("several OfflineSessionHost implementations [" + ServiceProviders.classNames(foundHosts)
                        + "]; keep exactly one");
                return;
            }
            List<JdbcConnectionSource> foundSources = sources.get();
            if (foundSources.size() != 1) {
                fail("app sessions need exactly one JdbcConnectionSource, found "
                        + (foundSources.isEmpty() ? "none" : "[" + ServiceProviders.classNames(foundSources) + "]"));
                return;
            }
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                    DaemonThreadFactory.named("nx-app-sessions-scheduler", log));
            AppSessionEngine engine = new AppSessionEngine(
                    new JdbcOfflineSessionStore(foundSources.get(0)),
                    foundHosts.get(0),
                    AppSettingsRuntime.rules(),
                    AppSettingsRuntime::rulesFromFile,
                    io,
                    BoundedExecutor.limitFor(ioWorkers),
                    scheduler,
                    AppSessionClock.SYSTEM,
                    AppSessionEngine.LOGIN_CLOSE_TIMEOUT_MS);
            try {
                CompletableFuture.runAsync(engine::boot, io).join();
            } catch (CompletionException e) {
                scheduler.shutdownNow();
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                fail("boot failed: " + cause.getClass().getName() + ": " + cause.getMessage());
                return;
            }
            moduleState = ModuleStates.ACTIVE;
            FACADE.ready(engine);
            log.info(
                    "App sessions ready with host {}",
                    foundHosts.get(0).getClass().getName());
        } catch (Throwable t) {
            fail("boot threw " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    public static void shutdown() {
        AppSessionEngine engine = FACADE.engine();
        if (engine != null) {
            try {
                engine.shutdown();
            } catch (Throwable t) {
                log.error("App sessions shutdown threw {}", t.getClass().getName(), t);
            }
        }
    }

    static @Nullable AppSessionEngine engine() {
        return FACADE.engine();
    }

    static String moduleState() {
        return moduleState;
    }

    public static void resetForTesting() {
        shutdown();
        FACADE.reset();
        moduleState = ModuleStates.INIT;
    }

    private static void fail(String reason) {
        log.error("App sessions FAILED: {}", reason);
        moduleState = ModuleStates.FAILED;
        FACADE.disable();
    }
}
