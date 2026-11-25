package app.l2nx.gs.adapter.api.kafka.sync.settings.app;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppSettingsTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static AppSettings.Builder valid() {
        return AppSettings.builder()
                .sessionTypes(Arrays.asList(
                        new AppSessionTypeSettings(ActivityType.OFFLINE_FISHING, true),
                        new AppSessionTypeSettings(ActivityType.OFFLINE_PRIVATE_STORE, false)))
                .recentlyLeftGameMinutes(2)
                .fishingStartCooldownMinutes(15)
                .privateStoreStartCooldownMinutes(5)
                .returnThresholdMinutes(30)
                .restartWindowMinutes(10)
                .fishingMinDurationMs(600_000L)
                .fishingMaxDurationMs(7_200_000L)
                .revive(new ReviveSettings(true))
                .chat(new ChatSettings(true, Arrays.asList("CLAN", "PARTY"), Arrays.asList("WHISPER")))
                .updatedAt(NOW);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    void build_shouldReject_whenSettingIsInvalid(String name, Class<? extends Exception> expected, Executable build) {
        assertThrows(expected, build);
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                negative("recentlyLeftGameMinutes", b -> b.recentlyLeftGameMinutes(-1)),
                negative("fishingStartCooldownMinutes", b -> b.fishingStartCooldownMinutes(-1)),
                negative("privateStoreStartCooldownMinutes", b -> b.privateStoreStartCooldownMinutes(-1)),
                negative("returnThresholdMinutes", b -> b.returnThresholdMinutes(-1)),
                negative("restartWindowMinutes", b -> b.restartWindowMinutes(-1)),
                invalid(
                        "null updatedAt",
                        NullPointerException.class,
                        () -> valid().updatedAt(null).build()),
                invalid(
                        "max duration below min",
                        IllegalArgumentException.class,
                        () -> valid().fishingMinDurationMs(10L)
                                .fishingMaxDurationMs(9L)
                                .build()),
                invalid(
                        "session type without an activity",
                        NullPointerException.class,
                        () -> new AppSessionTypeSettings(null, true)));
    }

    private static Arguments negative(String name, UnaryOperator<AppSettings.Builder> change) {
        return invalid(
                "negative " + name,
                IllegalArgumentException.class,
                () -> change.apply(valid()).build());
    }

    private static Arguments invalid(String name, Class<? extends Exception> expected, Executable build) {
        return Arguments.of(name, expected, build);
    }

    @Test
    void getSessionTypes_shouldBeEmptyAndFrozen_whenOmitted() {
        AppSettings actual = valid().sessionTypes(null).build();

        assertTrue(actual.getSessionTypes().isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> actual.getSessionTypes().add(new AppSessionTypeSettings(ActivityType.PRIVATE_STORE, true)));
    }

    @Test
    void gson_shouldWriteChatUnderItsWireNames() {
        String actual = GSON.toJson(valid().build());

        assertTrue(
                actual.contains("\"chat\":{\"enabled\":true,\"onlineSendChannels\":[\"CLAN\",\"PARTY\"],"
                        + "\"offlineSendChannels\":[\"WHISPER\"]}"),
                actual);
    }

    @Test
    void gson_shouldReadNoChat_whenAnOlderHostOmitsTheGroup() {
        String json = GSON.toJson(valid().chat(null).build());

        AppSettings actual = GSON.fromJson(json, AppSettings.class);

        assertFalse(json.contains("chat"), json);
        assertNull(actual.getChat());
    }

    @Test
    void gson_shouldKeepEmptyChatChannelLists() {
        ChatSettings original =
                new ChatSettings(true, Collections.<String>emptyList(), Collections.<String>emptyList());

        String json = GSON.toJson(original);
        ChatSettings actual = GSON.fromJson(json, ChatSettings.class);

        assertTrue(json.contains("\"onlineSendChannels\":[]"), json);
        assertEquals(original, actual);
    }

    @Test
    void chatSettings_shouldBeEmptyAndFrozen_whenChannelsOmitted() {
        ChatSettings actual = ChatSettings.builder().enabled(true).build();

        assertTrue(actual.getOnlineSendChannels().isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> actual.getOfflineSendChannels().add("CLAN"));
    }

    @Test
    void chatSettings_shouldStayEmptyAndEqual_whenGsonOmitsTheLists() {
        ChatSettings actual = GSON.fromJson("{\"enabled\":true}", ChatSettings.class);

        assertTrue(actual.getOnlineSendChannels().isEmpty());
        assertTrue(actual.getOfflineSendChannels().isEmpty());
        assertEquals(ChatSettings.builder().enabled(true).build(), actual);
        assertEquals(ChatSettings.builder().enabled(true).build().hashCode(), actual.hashCode());
        assertEquals("ChatSettings[enabled=true, onlineSendChannels=[], offlineSendChannels=[]]", actual.toString());
    }

    @Test
    void chatSettings_shouldHandOutFrozenLists_whenGsonFillsThem() {
        ChatSettings actual = GSON.fromJson(
                "{\"enabled\":true,\"onlineSendChannels\":[\"CLAN\"],\"offlineSendChannels\":[\"WHISPER\"]}",
                ChatSettings.class);

        assertThrows(
                UnsupportedOperationException.class,
                () -> actual.getOnlineSendChannels().add("PARTY"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> actual.getOfflineSendChannels().clear());
        assertEquals(new ChatSettings(true, Arrays.asList("CLAN"), Arrays.asList("WHISPER")), actual);
    }

    @Test
    void gson_shouldWriteReviveEnabledUnderItsWireName() {
        String actual = GSON.toJson(valid().build());

        assertTrue(actual.contains("\"revive\":{\"enabled\":true}"), actual);
    }

    @Test
    void gson_shouldReadNoRevive_whenAnOlderHostOmitsTheGroup() {
        String json = GSON.toJson(valid().revive(null).build());

        AppSettings actual = GSON.fromJson(json, AppSettings.class);

        assertFalse(json.contains("revive"), json);
        assertNull(actual.getRevive());
    }

    @Test
    void entityName_shouldBeTheWireName() {
        assertEquals("appsettings", AppSettings.ENTITY_NAME);
    }

    @Test
    void gson_shouldRoundtripEnvelopeWithPayload() {
        SettingsSyncEvent<AppSettings> original =
                new SettingsSyncEvent<AppSettings>(AppSettings.ENTITY_NAME, valid().build(), 1_760_000_000_000L);

        String json = GSON.toJson(original);
        SettingsSyncEvent<AppSettings> actual =
                GSON.fromJson(json, new TypeToken<SettingsSyncEvent<AppSettings>>() {}.getType());

        assertTrue(json.contains("\"activity\":\"OFFLINE_FISHING\""));
        assertEquals(original, actual);
        assertEquals(original, original.toBuilder().build());
        assertEquals("appsettings", actual.getEntityName());
        assertEquals(1_760_000_000_000L, actual.getTimestampEpochMs());

        AppSettings settings = actual.getPayload().toBuilder().build();
        assertEquals(valid().build(), settings);
        assertEquals(
                Arrays.asList(
                        new AppSessionTypeSettings(ActivityType.OFFLINE_FISHING, true),
                        new AppSessionTypeSettings(ActivityType.OFFLINE_PRIVATE_STORE, false)),
                settings.getSessionTypes());
        assertEquals(
                ActivityType.OFFLINE_FISHING, settings.getSessionTypes().get(0).getActivity());
        assertTrue(settings.getSessionTypes().get(0).isEnabled());
        assertFalse(settings.getSessionTypes().get(1).isEnabled());
        assertTrue(settings.getChat().isEnabled());
        assertEquals(Arrays.asList("CLAN", "PARTY"), settings.getChat().getOnlineSendChannels());
        assertEquals(Arrays.asList("WHISPER"), settings.getChat().getOfflineSendChannels());
        assertEquals(2, settings.getRecentlyLeftGameMinutes());
        assertEquals(15, settings.getFishingStartCooldownMinutes());
        assertEquals(5, settings.getPrivateStoreStartCooldownMinutes());
        assertEquals(30, settings.getReturnThresholdMinutes());
        assertEquals(10, settings.getRestartWindowMinutes());
        assertEquals(600_000L, settings.getFishingMinDurationMs());
        assertEquals(7_200_000L, settings.getFishingMaxDurationMs());
        assertEquals(new ReviveSettings(true), settings.getRevive());
        assertEquals(
                new ChatSettings(true, Arrays.asList("CLAN", "PARTY"), Arrays.asList("WHISPER")), settings.getChat());
        assertEquals(NOW, settings.getUpdatedAt());
        assertNotEquals(
                settings, settings.toBuilder().fishingStartCooldownMinutes(16).build());
        assertNotEquals(
                settings,
                settings.toBuilder().privateStoreStartCooldownMinutes(6).build());
        assertNotEquals(
                settings, settings.toBuilder().revive(new ReviveSettings(false)).build());
        assertNotEquals(
                settings,
                settings.toBuilder()
                        .chat(new ChatSettings(true, Arrays.asList("CLAN"), Arrays.asList("WHISPER")))
                        .build());
    }

    @Test
    void gson_shouldWriteTheCooldownsUnderTheirWireNames() {
        String actual = GSON.toJson(valid().build());

        assertTrue(actual.contains("\"fishingStartCooldownMinutes\":15"), actual);
        assertTrue(actual.contains("\"privateStoreStartCooldownMinutes\":5"), actual);
        assertFalse(actual.contains("\"startCooldownMinutes\""), actual);
    }

    @Test
    void settingsSyncEvent_shouldRejectNullEntityNameAndPayload() {
        assertThrows(NullPointerException.class, () -> new SettingsSyncEvent<String>(null, "p", 1L));
        assertThrows(NullPointerException.class, () -> new SettingsSyncEvent<String>("e", null, 1L));
    }
}
