package app.l2nx.gs.adapter.core.settings;

import app.l2nx.gs.adapter.api.kafka.sync.settings.game.GameSettings;
import app.l2nx.gs.adapter.api.spi.provider.GameSettingsProvider;
import app.l2nx.gs.commons.spi.ServiceProviders;
import app.l2nx.gs.commons.spi.SingleProvider;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

final class GameSettingsSource {

    private static final NxLog defaultLog = NxLogFactory.getLogger(GameSettingsSource.class);

    private final @Nullable GameSettingsProvider provider;
    private final NxLog log;
    private final AtomicBoolean failing = new AtomicBoolean();

    private GameSettingsSource(@Nullable GameSettingsProvider provider, NxLog log) {
        this.provider = provider;
        this.log = log;
    }

    static GameSettingsSource shared() {
        return Discovered.INSTANCE;
    }

    static GameSettingsSource none() {
        return new GameSettingsSource(null, defaultLog);
    }

    static GameSettingsSource of(List<GameSettingsProvider> found, NxLog log) {
        SingleProvider<GameSettingsProvider> single = ServiceProviders.single(GameSettingsProvider.class, found);
        if (single.ambiguous()) {
            log.error("{}; game settings are not published", single.ambiguity());
        }
        return new GameSettingsSource(single.get(), log);
    }

    boolean present() {
        return provider != null;
    }

    /**
     * {@code updatedAt} is stamped with {@code builtAt} whatever the provider set. {@code null}: no provider, host not
     * ready, or the provider threw.
     */
    @Nullable
    GameSettings snapshot(Instant builtAt) {
        GameSettingsProvider current = provider;
        if (current == null) {
            return null;
        }
        GameSettings built;
        try {
            built = current.gameSettings(builtAt);
        } catch (Throwable t) {
            if (failing.compareAndSet(false, true)) {
                log.warn(
                        "GameSettingsProvider {} threw {}, no game settings snapshot until a call succeeds",
                        current.getClass().getName(),
                        t.getClass().getName(),
                        t);
            }
            return null;
        }
        failing.set(false);
        if (built == null || builtAt.equals(built.getUpdatedAt())) {
            return built;
        }
        return built.toBuilder().updatedAt(builtAt).build();
    }

    private static GameSettingsSource discover() {
        try {
            return of(ServiceProviders.loadAll(GameSettingsProvider.class, GameSettingsSource.class), defaultLog);
        } catch (Throwable t) {
            // a broken provider registration must not poison the holder class for the rest of the JVM
            defaultLog.error(
                    "GameSettingsProvider discovery threw {}, game settings are not published",
                    t.getClass().getName(),
                    t);
            return none();
        }
    }

    private static final class Discovered {
        static final GameSettingsSource INSTANCE = discover();
    }
}
