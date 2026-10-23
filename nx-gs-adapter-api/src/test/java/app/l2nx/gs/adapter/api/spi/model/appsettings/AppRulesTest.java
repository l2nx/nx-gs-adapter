package app.l2nx.gs.adapter.api.spi.model.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AppRulesTest {

    static Stream<Arguments> invalidRules() {
        String seventeen = "12345678901234567";
        return Stream.of(
                invalid("blank title", b -> b.phantomTitleText("  ")),
                invalid("empty title", b -> b.phantomTitleText("")),
                invalid("title of 17 code points", b -> b.phantomTitleText(seventeen)),
                invalid("color above FFFFFF", b -> b.phantomTitleColorRgb(0x1000000)),
                invalid("negative color", b -> b.phantomTitleColorRgb(-1)),
                invalid("recent logout 61", b -> b.recentLogoutMinutes(61)),
                invalid("recent logout -1", b -> b.recentLogoutMinutes(-1)),
                invalid("restart window 121", b -> b.restartWindowMinutes(121)),
                invalid("return threshold -1", b -> b.returnThresholdMinutes(-1)),
                invalid("start cooldown 1441", b -> b.fishingStartCooldownMinutes(1441)),
                invalid("min duration 0", b -> b.fishingMinDurationMinutes(0)),
                invalid(
                        "max duration below min",
                        b -> b.fishingMinDurationMinutes(60).fishingMaxDurationMinutes(59)),
                invalid("float distance 89", b -> b.fishingFloatDistance(89)),
                invalid("float distance 251", b -> b.fishingFloatDistance(251)),
                invalid("spot pool with a dash", b -> b.fishingSpotPool("giran-harbor")),
                invalid("fishing without a spot pool", b -> b.sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)),
                invalid("restore batch 0", b -> b.restoreBatchSize(0)),
                invalid("restore pause -1", b -> b.restoreBatchPauseMillis(-1)),
                invalid("retention 6 days", b -> b.historyRetentionDays(6)));
    }

    private static Arguments invalid(String name, UnaryOperator<AppRules.Builder> change) {
        return Arguments.of(name, change);
    }

    @Nested
    class Defaults {

        @Test
        void defaults_shouldMatchTheKeyCatalog() {
            AppRules actual = AppRules.defaults();

            assertNull(actual.phantomTitleText());
            assertEquals(0xFFFF00, actual.phantomTitleColorRgb());
            assertEquals(2, actual.recentLogoutMinutes());
            assertEquals(15, actual.restartWindowMinutes());
            assertEquals(30, actual.returnThresholdMinutes());
            assertEquals(15, actual.fishingStartCooldownMinutes());
            assertEquals(30, actual.fishingMinDurationMinutes());
            assertEquals(1440, actual.fishingMaxDurationMinutes());
            assertEquals(170, actual.fishingFloatDistance());
            assertEquals(SpotOrder.RANDOM, actual.fishingSpotOrder());
            assertNull(actual.fishingSpotPool());
            assertEquals(10, actual.restoreBatchSize());
            assertEquals(1000, actual.restoreBatchPauseMillis());
            assertEquals(90, actual.historyRetentionDays());
        }

        @ParameterizedTest(name = "{0} is off by default")
        @EnumSource(ActivityType.class)
        void sessionTypeEnabled_shouldBeFalseForEveryActivity_whenDefaults(ActivityType activity) {
            assertFalse(AppRules.defaults().sessionTypeEnabled(activity));
        }

        @Test
        void builder_shouldStartFromTheDefaults() {
            assertEquals(AppRules.defaults(), AppRules.builder().build());
        }
    }

    @Nested
    class SessionTypes {

        @Test
        void sessionTypeEnabled_shouldReportOnlyTheEnabledType() {
            AppRules actual = AppRules.builder()
                    .sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)
                    .fishingSpotPool("harbor")
                    .build();

            assertTrue(actual.sessionTypeEnabled(ActivityType.OFFLINE_FISHING));
            assertFalse(actual.sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE));
        }

        @Test
        void sessionTypeEnabled_shouldTurnATypeOffAgain() {
            AppRules actual =
                    AppRules.builder().sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE, true).build().toBuilder()
                            .sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE, false)
                            .build();

            assertEquals(AppRules.defaults(), actual);
        }

        @ParameterizedTest(name = "{0} has no settings key")
        @EnumSource(
                value = ActivityType.class,
                mode = EnumSource.Mode.EXCLUDE,
                names = {"OFFLINE_FISHING", "OFFLINE_PRIVATE_STORE"})
        void sessionTypeEnabled_shouldReject_whenTheActivityHasNoSettingsKey(ActivityType activity) {
            AppRules.Builder builder = AppRules.builder();

            assertThrows(IllegalArgumentException.class, () -> builder.sessionTypeEnabled(activity, true));
            assertThrows(IllegalArgumentException.class, () -> builder.sessionTypeEnabled(activity, false));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = ActivityType.class,
                names = {"OFFLINE_FISHING", "OFFLINE_PRIVATE_STORE"})
        void needsSpotPool_shouldMatchWhatBuildEnforces(ActivityType activity) {
            AppRules.Builder enabledWithoutPool = AppRules.builder().sessionTypeEnabled(activity, true);

            boolean buildRejects = false;
            try {
                enabledWithoutPool.build();
            } catch (IllegalArgumentException e) {
                buildRejects = true;
            }

            assertEquals(buildRejects, AppRules.needsSpotPool(activity));
        }
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.spi.model.appsettings.AppRulesTest#invalidRules")
        void build_shouldThrow_whenAnInvariantBreaks(String name, UnaryOperator<AppRules.Builder> change) {
            AppRules.Builder builder = change.apply(AppRules.builder());

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @ParameterizedTest(name = "min {0}, max {1}")
        @CsvSource({"60, 59", "60, 60", "60, 61"})
        void fishingDurationsOrdered_shouldMatchWhatBuildEnforces(int min, int max) {
            AppRules.Builder builder =
                    AppRules.builder().fishingMinDurationMinutes(min).fishingMaxDurationMinutes(max);

            boolean buildAccepts = true;
            try {
                builder.build();
            } catch (IllegalArgumentException e) {
                buildAccepts = false;
            }

            assertEquals(buildAccepts, AppRules.fishingDurationsOrdered(min, max));
        }

        @Test
        void build_shouldAcceptEveryRangeBound() {
            AppRules.builder()
                    .phantomTitleText("1234567890123456")
                    .phantomTitleColorRgb(AppRules.PHANTOM_TITLE_COLOR_RGB.max())
                    .recentLogoutMinutes(AppRules.RECENT_LOGOUT_MINUTES.max())
                    .restartWindowMinutes(AppRules.RESTART_WINDOW_MINUTES.max())
                    .returnThresholdMinutes(AppRules.RETURN_THRESHOLD_MINUTES.min())
                    .fishingStartCooldownMinutes(AppRules.FISHING_START_COOLDOWN_MINUTES.max())
                    .fishingMinDurationMinutes(AppRules.FISHING_DURATION_MINUTES.min())
                    .fishingMaxDurationMinutes(AppRules.FISHING_DURATION_MINUTES.min())
                    .fishingFloatDistance(AppRules.FISHING_FLOAT_DISTANCE.min())
                    .restoreBatchSize(AppRules.RESTORE_BATCH_SIZE.min())
                    .restoreBatchPauseMillis(AppRules.RESTORE_BATCH_PAUSE_MILLIS.min())
                    .historyRetentionDays(AppRules.HISTORY_RETENTION_DAYS.min())
                    .build();
        }
    }

    @Nested
    class Copy {

        @Test
        void toBuilder_shouldRoundtripEveryValue() {
            AppRules expected = AppRules.builder()
                    .phantomTitleText("away")
                    .phantomTitleColorRgb(0x00FF00)
                    .recentLogoutMinutes(5)
                    .restartWindowMinutes(20)
                    .returnThresholdMinutes(45)
                    .sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE, true)
                    .sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)
                    .fishingStartCooldownMinutes(10)
                    .fishingMinDurationMinutes(60)
                    .fishingMaxDurationMinutes(600)
                    .fishingFloatDistance(200)
                    .fishingSpotOrder(SpotOrder.FIRST_FREE)
                    .fishingSpotPool("harbor")
                    .restoreBatchSize(3)
                    .restoreBatchPauseMillis(250)
                    .historyRetentionDays(30)
                    .build();

            AppRules actual = expected.toBuilder().build();

            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertNotEquals(AppRules.defaults(), actual);
        }
    }
}
