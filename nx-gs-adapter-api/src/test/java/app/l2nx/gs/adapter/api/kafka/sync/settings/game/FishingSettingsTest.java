package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FishingSettingsTest {

    private static FishingSettings.Builder valid() {
        return FishingSettings.builder()
                .game(new FishingLimits(true, false, true))
                .app(new FishingLimits(true, true, false))
                .gameReleaseDelaySeconds(10);
    }

    @Nested
    class Build {

        @Test
        void build_shouldRejectNegativeReleaseDelay() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().gameReleaseDelaySeconds(-1).build());
        }

        @Test
        void build_shouldAcceptZeroReleaseDelay() {
            assertEquals(0, valid().gameReleaseDelaySeconds(0).build().getGameReleaseDelaySeconds());
        }

        @Test
        void build_shouldRejectMissingLimits() {
            assertThrows(NullPointerException.class, () -> valid().game(null).build());
            assertThrows(NullPointerException.class, () -> valid().app(null).build());
        }
    }
}
