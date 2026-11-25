package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Fishing rules as the engine applies them, not its configuration keys: a key that does not affect app starts never
 * changes {@code app}. Staff diagnostics, never shown to players: a limit known to be off is a hint how to bypass it.
 */
public final class FishingSettings {

    private final FishingLimits game;
    private final FishingLimits app;
    private final int gameReleaseDelaySeconds;

    public FishingSettings(FishingLimits game, FishingLimits app, int gameReleaseDelaySeconds) {
        this.game = Objects.requireNonNull(game, "FishingSettings.game is required");
        this.app = Objects.requireNonNull(app, "FishingSettings.app is required");
        if (gameReleaseDelaySeconds < 0) {
            throw new IllegalArgumentException("FishingSettings.gameReleaseDelaySeconds must not be negative");
        }
        this.gameReleaseDelaySeconds = gameReleaseDelaySeconds;
    }

    /** Limits of fishing in the game client. */
    public FishingLimits getGame() {
        return game;
    }

    /** Limits of a fishing start from the app. */
    public FishingLimits getApp() {
        return app;
    }

    /** How long a character that fished in the game client keeps the account and HWID slots after it stops. */
    public int getGameReleaseDelaySeconds() {
        return gameReleaseDelaySeconds;
    }

    public Builder toBuilder() {
        return new Builder().game(game).app(app).gameReleaseDelaySeconds(gameReleaseDelaySeconds);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FishingSettings)) return false;
        FishingSettings that = (FishingSettings) o;
        return game.equals(that.game)
                && app.equals(that.app)
                && gameReleaseDelaySeconds == that.gameReleaseDelaySeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(game, app, gameReleaseDelaySeconds);
    }

    @Override
    public String toString() {
        return "FishingSettings[game=" + game
                + ", app=" + app
                + ", gameReleaseDelaySeconds=" + gameReleaseDelaySeconds + "]";
    }

    public static final class Builder {
        private @Nullable FishingLimits game;
        private @Nullable FishingLimits app;
        private int gameReleaseDelaySeconds;

        public Builder game(FishingLimits game) {
            this.game = game;
            return this;
        }

        public Builder app(FishingLimits app) {
            this.app = app;
            return this;
        }

        public Builder gameReleaseDelaySeconds(int gameReleaseDelaySeconds) {
            this.gameReleaseDelaySeconds = gameReleaseDelaySeconds;
            return this;
        }

        public FishingSettings build() {
            return new FishingSettings(game, app, gameReleaseDelaySeconds);
        }
    }
}
