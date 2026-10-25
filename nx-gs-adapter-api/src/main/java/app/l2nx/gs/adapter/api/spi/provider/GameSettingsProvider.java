package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.settings.game.GameSettings;
import java.time.Instant;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/**
 * Source of the engine configuration snapshot for settings sync, discovered via {@link ServiceLoader}.
 * Called from adapter IO threads; an implementation must not block on the game thread.
 */
public interface GameSettingsProvider {

    /**
     * Builds the snapshot from live configuration on every call, never from a cache. Carries {@code updatedAt = builtAt}.
     *
     * @return {@code null} when the host is not ready: no snapshot this time
     */
    @Nullable
    GameSettings gameSettings(Instant builtAt);
}
