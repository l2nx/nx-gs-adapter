package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FishingSettingsTest {

    private static FishingSettings.Builder valid() {
        return FishingSettings.builder()
                .game(new FishingLimits(true, false, true))
                .app(new FishingLimits(true, true, false))
                .gameReleaseDelaySeconds(10);
    }

    @Nested
    class Settings {

        @Test
        void build_shouldRejectNegativeReleaseDelay() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().gameReleaseDelaySeconds(-1).build());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 10})
        void build_shouldAcceptNonNegativeReleaseDelay(int seconds) {
            FishingSettings actual = valid().gameReleaseDelaySeconds(seconds).build();

            assertEquals(seconds, actual.getGameReleaseDelaySeconds());
        }

        @Test
        void build_shouldRejectMissingLimits() {
            assertThrows(NullPointerException.class, () -> valid().game(null).build());
            assertThrows(NullPointerException.class, () -> valid().app(null).build());
        }

        @Test
        void build_shouldKeepGameAndAppLimitsApart() {
            FishingSettings actual = valid().build();

            assertEquals(new FishingLimits(true, false, true), actual.getGame());
            assertEquals(new FishingLimits(true, true, false), actual.getApp());
        }

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            FishingSettings original = valid().build();

            assertEquals(original, original.toBuilder().build());
            assertNotEquals(
                    original, original.toBuilder().gameReleaseDelaySeconds(11).build());
            assertNotEquals(
                    original, original.toBuilder().game(original.getApp()).build());
            assertNotEquals(
                    original, original.toBuilder().app(original.getGame()).build());
        }
    }

    @Nested
    class Limits {

        @Test
        void build_shouldLimitNothing_whenOmitted() {
            FishingLimits actual = FishingLimits.builder().build();

            assertFalse(actual.isAccountLimit());
            assertFalse(actual.isHwidLimit());
            assertFalse(actual.isIpLimit());
        }

        @Test
        void toBuilder_shouldRoundtripEachLimit() {
            FishingLimits original = new FishingLimits(true, false, true);

            assertEquals(original, original.toBuilder().build());
            assertNotEquals(original, original.toBuilder().accountLimit(false).build());
            assertNotEquals(original, original.toBuilder().hwidLimit(true).build());
            assertNotEquals(original, original.toBuilder().ipLimit(false).build());
        }
    }
}
