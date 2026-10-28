package app.l2nx.gs.adapter.core.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.appsettings.ReviveTownMode;
import app.l2nx.gs.adapter.api.spi.model.appsettings.SpotOrder;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppSettingsFileTest {

    private static final char BACKSLASH = (char) 0x5C;

    private static AppSettingsFile.Result parse(String content) throws IOException {
        return AppSettingsFile.parse(new StringReader(content));
    }

    private static AppRules valid(String content) throws IOException {
        AppSettingsFile.Result result = parse(content);
        assertEquals(Collections.emptyList(), result.errors());
        return result.rules();
    }

    @Nested
    class Parse {

        @Test
        void parse_shouldReturnTheDefaults_whenTheFileIsEmpty() throws IOException {
            assertEquals(AppRules.defaults(), valid(""));
        }

        @Test
        void parse_shouldTypeEveryKey_whenAllAreSet() throws IOException {
            AppRules actual = valid("app.phantom.title.text = Gone fishing \n"
                    + "app.phantom.title.color=00ff00\n"
                    + "app.session.recent-logout-minutes=5\n"
                    + "app.session.restart-window-minutes=20\n"
                    + "app.session.return-threshold-minutes=45\n"
                    + "app.private-store.enabled=true\n"
                    + "app.private-store.start-cooldown-minutes=0\n"
                    + "app.fishing.enabled=true\n"
                    + "app.fishing.start-cooldown-minutes=10\n"
                    + "app.fishing.min-duration-minutes=60\n"
                    + "app.fishing.max-duration-minutes=600\n"
                    + "app.fishing.float-distance=200\n"
                    + "app.fishing.spot-order=FIRST_FREE\n"
                    + "app.fishing.spot-pool=harbor_1\n"
                    + "app.revive.enabled=false\n"
                    + "app.revive.town.mode=SPOT_POOL\n"
                    + "app.revive.town.spot-pool=harbor_1, giran\n"
                    + "app.chat.enabled=true\n"
                    + "app.chat.online-send-channels=WHISPER, PARTY\n"
                    + "app.chat.offline-send-channels=CLAN\n"
                    + "app.restore.batch-size=3\n"
                    + "app.restore.batch-pause-millis=250\n"
                    + "app.history.retention-days=30\n");

            AppRules expected = AppRules.builder()
                    .phantomTitleText("Gone fishing")
                    .phantomTitleColorRgb(0x00FF00)
                    .recentLogoutMinutes(5)
                    .restartWindowMinutes(20)
                    .returnThresholdMinutes(45)
                    .sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE, true)
                    .sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)
                    .privateStoreStartCooldownMinutes(0)
                    .fishingStartCooldownMinutes(10)
                    .fishingMinDurationMinutes(60)
                    .fishingMaxDurationMinutes(600)
                    .fishingFloatDistance(200)
                    .fishingSpotOrder(SpotOrder.FIRST_FREE)
                    .fishingSpotPool("harbor_1")
                    .reviveEnabled(false)
                    .reviveTownMode(ReviveTownMode.SPOT_POOL)
                    .reviveTownSpotPools(Arrays.asList("harbor_1", "giran"))
                    .chatEnabled(true)
                    .chatOnlineSendChannels(EnumSet.of(ChatChannel.PARTY, ChatChannel.WHISPER))
                    .chatOfflineSendChannels(EnumSet.of(ChatChannel.CLAN))
                    .restoreBatchSize(3)
                    .restoreBatchPauseMillis(250)
                    .historyRetentionDays(30)
                    .build();
            assertEquals(expected, actual);
        }

        @Test
        void parse_shouldKeepCommentsInAnyLanguage_whenReadAsUtf8() throws IOException {
            AppRules actual = valid("# Рыбалка из приложения\napp.phantom.title.text=Рыбак\n");

            assertEquals("Рыбак", actual.phantomTitleText());
        }

        @ParameterizedTest(name = "{0}=''{1}'' is unset")
        @CsvSource({"app.phantom.title.text, ''", "app.phantom.title.text, '   '", "app.fishing.spot-pool, ''"})
        void parse_shouldTreatBlankAsUnset_whenTheKeyIsOptional(String key, String value) throws IOException {
            AppRules actual = valid(key + "=" + value + "\n");

            assertNull(key.equals("app.phantom.title.text") ? actual.phantomTitleText() : actual.fishingSpotPool());
        }

        @ParameterizedTest(name = "''{0}'' -> {1}")
        @CsvSource(
                delimiter = '|',
                value = {"''|", "'   '|", "town|town", "' a , b_2 '|a,b_2", "a,b,c|a,b,c"})
        void parse_shouldReadTheReviveSpotPoolsAsAList(String value, String expected) throws IOException {
            List<String> expectedPools =
                    expected == null ? Collections.<String>emptyList() : Arrays.asList(expected.split(","));

            AppRules actual = valid("app.revive.town.spot-pool=" + value + "\n");

            assertEquals(expectedPools, actual.reviveTownSpotPools());
        }

        @ParameterizedTest(name = "{0}=''{1}'' is read-only")
        @CsvSource({
            "app.chat.online-send-channels, ''",
            "app.chat.online-send-channels, '   '",
            "app.chat.offline-send-channels, ''"
        })
        void parse_shouldReadBlankChatChannelsAsNone(String key, String value) throws IOException {
            AppRules actual = valid(key + "=" + value + "\n");

            assertTrue(
                    key.equals("app.chat.online-send-channels")
                            ? actual.chatOnlineSendChannels().isEmpty()
                            : actual.chatOfflineSendChannels().isEmpty());
        }

        @Test
        void parse_shouldSkipALeadingByteOrderMark() throws IOException {
            AppRules actual = valid((char) 0xFEFF + "app.session.return-threshold-minutes=45\n");

            assertEquals(45, actual.returnThresholdMinutes());
        }

        @Test
        void parse_shouldCountTitleLengthInCodePoints() throws IOException {
            String sixteenEmoji = new String(new char[16]).replace("\0", "🎣");

            assertEquals(
                    sixteenEmoji,
                    valid("app.phantom.title.text=" + sixteenEmoji).phantomTitleText());
        }
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "{0}={1} -> {2}")
        @CsvSource(
                delimiter = '|',
                quoteCharacter = '"',
                value = {
                    "app.phantom.title.text|12345678901234567|app.phantom.title.text: longer than 16 characters",
                    "app.phantom.title.color|FFFF0|app.phantom.title.color: expected 6 hex digits RGB, got 'FFFF0'",
                    "app.session.recent-logout-minutes|61|app.session.recent-logout-minutes: out of range 0..60: 61",
                    "app.session.restart-window-minutes|-1|app.session.restart-window-minutes: out of range 0..120: -1",
                    "app.session.return-threshold-minutes|x|app.session.return-threshold-minutes: not an integer: 'x'",
                    "app.private-store.enabled|TRUE|app.private-store.enabled: expected true or false, got 'TRUE'",
                    "app.private-store.start-cooldown-minutes|1441|app.private-store.start-cooldown-minutes: out of"
                            + " range 0..1440: 1441",
                    "app.private-store.start-cooldown-minutes|-1|app.private-store.start-cooldown-minutes: out of"
                            + " range 0..1440: -1",
                    "app.fishing.enabled|yes|app.fishing.enabled: expected true or false, got 'yes'",
                    "app.fishing.start-cooldown-minutes|1441|app.fishing.start-cooldown-minutes: out of range 0..1440:"
                            + " 1441",
                    "app.fishing.min-duration-minutes|0|app.fishing.min-duration-minutes: less than 1: 0",
                    "app.fishing.float-distance|89|app.fishing.float-distance: out of range 90..250: 89",
                    "app.fishing.spot-order|random|app.fishing.spot-order: expected RANDOM or FIRST_FREE, got 'random'",
                    "app.fishing.spot-pool|Giran-Harbor|app.fishing.spot-pool: expected 1-32 characters of a-z, 0-9"
                            + " and _, got 'Giran-Harbor'",
                    "app.revive.enabled|on|app.revive.enabled: expected true or false, got 'on'",
                    "app.revive.town.mode|native|app.revive.town.mode: expected NATIVE or SPOT_POOL, got 'native'",
                    "app.revive.town.spot-pool|giran-town|app.revive.town.spot-pool: expected comma-separated names of"
                            + " 1-32 characters of a-z, 0-9 and _, got 'giran-town'",
                    "app.revive.town.spot-pool|a,,b|app.revive.town.spot-pool: expected comma-separated names of 1-32"
                            + " characters of a-z, 0-9 and _, got ''",
                    "app.revive.town.spot-pool|a,|app.revive.town.spot-pool: expected comma-separated names of 1-32"
                            + " characters of a-z, 0-9 and _, got ''",
                    "app.revive.town.spot-pool|a, a|app.revive.town.spot-pool: 'a' is listed twice",
                    "app.chat.enabled|1|app.chat.enabled: expected true or false, got '1'",
                    "app.chat.online-send-channels|clan|app.chat.online-send-channels: expected WHISPER, PARTY, CLAN or"
                            + " ALLIANCE, got 'clan'",
                    "app.chat.online-send-channels|TRADE|app.chat.online-send-channels: expected WHISPER, PARTY, CLAN or"
                            + " ALLIANCE, got 'TRADE'",
                    "app.chat.online-send-channels|UNKNOWN_7|app.chat.online-send-channels: expected WHISPER, PARTY,"
                            + " CLAN or ALLIANCE, got 'UNKNOWN_7'",
                    "app.chat.online-send-channels|CLAN,,PARTY|app.chat.online-send-channels: expected WHISPER, PARTY,"
                            + " CLAN or ALLIANCE, got ''",
                    "app.chat.online-send-channels|CLAN, CLAN|app.chat.online-send-channels: 'CLAN' is listed twice",
                    "app.chat.offline-send-channels|CLAN,PARTY|app.chat.offline-send-channels: PARTY is not available"
                            + " out of game",
                    "app.chat.offline-send-channels|WHISPER,WHISPER|app.chat.offline-send-channels: 'WHISPER' is listed"
                            + " twice",
                    "app.restore.batch-size|0|app.restore.batch-size: less than 1: 0",
                    "app.restore.batch-pause-millis|-5|app.restore.batch-pause-millis: less than 0: -5",
                    "app.history.retention-days|6|app.history.retention-days: less than 7: 6"
                })
        void parse_shouldRejectTheFile_whenAValueIsInvalid(String key, String value, String expected)
                throws IOException {
            AppSettingsFile.Result actual = parse(key + "=" + value + "\n");

            assertNull(actual.rules());
            assertEquals(Collections.singletonList(expected), actual.errors());
        }

        @Test
        void parse_shouldRejectTheFile_whenAnEscapeIsMalformed() throws IOException {
            AppSettingsFile.Result actual = parse("app.phantom.title.text=" + BACKSLASH + "u12\n");

            assertNull(actual.rules());
            assertEquals(1, actual.errors().size());
            assertTrue(
                    actual.errors().get(0).startsWith("the file cannot be parsed: "),
                    actual.errors().get(0));
        }

        @Test
        void parse_shouldRejectTheFile_whenAKeyIsUnknown() throws IOException {
            AppSettingsFile.Result actual = parse("app.fishing.enabeld=true\n");

            assertNull(actual.rules());
            assertEquals(Collections.singletonList("app.fishing.enabeld: unknown key"), actual.errors());
        }

        @Test
        void parse_shouldRejectTheFile_whenMaxDurationIsBelowMin() throws IOException {
            AppSettingsFile.Result actual =
                    parse("app.fishing.min-duration-minutes=60\napp.fishing.max-duration-minutes=59\n");

            assertEquals(
                    Collections.singletonList(
                            "app.fishing.max-duration-minutes: less than app.fishing.min-duration-minutes (60)"),
                    actual.errors());
        }

        @Test
        void parse_shouldRejectTheFile_whenFishingIsEnabledWithoutASpotPool() throws IOException {
            AppSettingsFile.Result actual = parse("app.fishing.enabled=true\n");

            assertEquals(
                    Collections.singletonList("app.fishing.enabled: true requires app.fishing.spot-pool"),
                    actual.errors());
        }

        @Test
        void parse_shouldRejectTheFile_whenReviveSpotPoolModeHasNoPool() throws IOException {
            AppSettingsFile.Result actual = parse("app.revive.town.mode=SPOT_POOL\napp.revive.town.spot-pool= \n");

            assertNull(actual.rules());
            assertEquals(
                    Collections.singletonList("app.revive.town.mode: SPOT_POOL requires app.revive.town.spot-pool"),
                    actual.errors());
        }

        @Test
        void parse_shouldReportOnlyThePoolError_whenReviveSpotPoolModeHasAnInvalidPool() throws IOException {
            AppSettingsFile.Result actual = parse("app.revive.town.mode=SPOT_POOL\napp.revive.town.spot-pool=A\n");

            assertEquals(
                    Collections.singletonList("app.revive.town.spot-pool: expected comma-separated names of 1-32"
                            + " characters of a-z, 0-9 and _, got 'A'"),
                    actual.errors());
        }

        @Test
        void parse_shouldReportEveryProblemOnce_whenSeveralKeysAreBad() throws IOException {
            AppSettingsFile.Result actual = parse("app.restore.batch-size=0\n"
                    + "app.fishing.min-duration-minutes=x\n"
                    + "app.fishing.max-duration-minutes=10\n"
                    + "zz.unknown=1\n"
                    + "aa.unknown=1\n");

            List<String> expected = Arrays.asList(
                    "app.fishing.min-duration-minutes: not an integer: 'x'",
                    "app.restore.batch-size: less than 1: 0",
                    "aa.unknown: unknown key",
                    "zz.unknown: unknown key");
            assertEquals(expected, actual.errors());
        }
    }

    @Test
    void catalog_shouldHaveAnEnabledKeyForExactlyTheRulesSessionTypes() {
        assertEquals(AppRules.SESSION_TYPES, EnumSet.copyOf(AppSettingsCatalog.SESSION_TYPE_KEYS.values()));
    }

    @Test
    void catalog_shouldKeepTheEnabledKeyNamesInTheRulesOrder() {
        assertEquals(
                Arrays.asList("app.private-store.enabled", "app.fishing.enabled"),
                new ArrayList<String>(AppSettingsCatalog.SESSION_TYPE_KEYS.keySet()));
    }

    @Nested
    class ReferenceFile {

        // Gradle runs tests with the module directory as the working directory.
        private final Path reference =
                Paths.get("").toAbsolutePath().getParent().resolve(AppSettingsFile.FILE_NAME);

        @Test
        void referenceFile_shouldSetEveryCatalogKeyToItsDefault() throws IOException {
            Properties lines = new Properties();
            try (Reader reader = Files.newBufferedReader(reference, StandardCharsets.UTF_8)) {
                lines.load(reader);
            }
            List<String> catalog = new ArrayList<String>();
            for (AppSettingsCatalog.Key key : AppSettingsCatalog.all()) {
                catalog.add(key.name);
            }

            try (Reader reader = Files.newBufferedReader(reference, StandardCharsets.UTF_8)) {
                assertEquals(AppRules.defaults(), valid(reader));
            }
            assertEquals(new TreeSet<String>(catalog), new TreeSet<String>(lines.stringPropertyNames()));
        }

        private AppRules valid(Reader reader) throws IOException {
            AppSettingsFile.Result result = AppSettingsFile.parse(reader);
            assertEquals(Collections.emptyList(), result.errors());
            return result.rules();
        }
    }

    @Nested
    class Location {

        @Test
        void location_shouldBeTheWorkingDirectory_whenTheConfigFileHasNoParent() {
            assertEquals(Paths.get("l2nx-app.properties"), AppSettingsFile.location(Paths.get("l2nx.properties")));
        }

        @Test
        void location_shouldSitNextToTheConfigFile() {
            assertEquals(
                    Paths.get("conf", "server", "l2nx-app.properties"),
                    AppSettingsFile.location(Paths.get("conf", "server", "custom.properties")));
        }
    }

    @Nested
    class Diff {

        @Test
        void diff_shouldListOnlyTheChangedKeysInTheFilesNotation() {
            AppRules before = AppRules.defaults();
            AppRules after = before.toBuilder()
                    .returnThresholdMinutes(45)
                    .sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)
                    .fishingSpotPool("harbor")
                    .phantomTitleColorRgb(0x00FF00)
                    .reviveTownMode(ReviveTownMode.SPOT_POOL)
                    .reviveTownSpotPools(Arrays.asList("harbor", "giran"))
                    .chatEnabled(true)
                    .chatOfflineSendChannels(EnumSet.noneOf(ChatChannel.class))
                    .build();

            List<String> actual = AppSettingsCatalog.diff(before, after);

            assertEquals(
                    Arrays.asList(
                            "app.phantom.title.color: FFFF00 -> 00FF00",
                            "app.session.return-threshold-minutes: 30 -> 45",
                            "app.fishing.enabled: false -> true",
                            "app.fishing.spot-pool: <unset> -> harbor",
                            "app.revive.town.mode: NATIVE -> SPOT_POOL",
                            "app.revive.town.spot-pool: <unset> -> harbor,giran",
                            "app.chat.enabled: false -> true",
                            "app.chat.offline-send-channels: WHISPER,CLAN,ALLIANCE -> <none>"),
                    actual);
        }

        @Test
        void diff_shouldBeEmpty_whenNothingChanged() {
            assertTrue(AppSettingsCatalog.diff(
                            AppRules.defaults(), AppRules.builder().build())
                    .isEmpty());
        }
    }
}
