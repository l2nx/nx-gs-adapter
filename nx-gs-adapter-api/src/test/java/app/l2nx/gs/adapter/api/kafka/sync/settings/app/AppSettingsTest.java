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

    @Test
    void build_shouldRejectNullUpdatedAt() {
        assertThrows(NullPointerException.class, () -> valid().updatedAt(null).build());
    }

    @Test
    void build_shouldRejectMaxBelowMinDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().fishingMinDurationMs(10L).fishingMaxDurationMs(9L).build());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeMinutes")
    void build_shouldRejectNegativeMinutes(String name, UnaryOperator<AppSettings.Builder> change) {
        assertThrows(IllegalArgumentException.class, () -> change.apply(valid()).build());
    }

    static Stream<Arguments> negativeMinutes() {
        return Stream.of(
                negative("recentlyLeftGameMinutes", b -> b.recentlyLeftGameMinutes(-1)),
                negative("fishingStartCooldownMinutes", b -> b.fishingStartCooldownMinutes(-1)),
                negative("privateStoreStartCooldownMinutes", b -> b.privateStoreStartCooldownMinutes(-1)),
                negative("returnThresholdMinutes", b -> b.returnThresholdMinutes(-1)),
                negative("restartWindowMinutes", b -> b.restartWindowMinutes(-1)));
    }

    private static Arguments negative(String name, UnaryOperator<AppSettings.Builder> change) {
        return Arguments.of(name, change);
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
    void appSessionTypeSettings_shouldRejectNullActivity() {
        assertThrows(NullPointerException.class, () -> new AppSessionTypeSettings(null, true));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        AppSettings original = valid().build();

        assertEquals(original, original.toBuilder().build());
        assertNotEquals(
                original, original.toBuilder().fishingStartCooldownMinutes(16).build());
        assertNotEquals(
                original,
                original.toBuilder().privateStoreStartCooldownMinutes(6).build());
        assertNotEquals(
                original, original.toBuilder().revive(new ReviveSettings(false)).build());
        assertNotEquals(
                original,
                original.toBuilder()
                        .chat(new ChatSettings(true, Arrays.asList("CLAN"), Arrays.asList("WHISPER")))
                        .build());
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

    @Test
    void settingsSyncEvent_shouldRoundtripThroughBuilder() {
        SettingsSyncEvent<String> original = new SettingsSyncEvent<String>("e", "p", 1L);

        assertEquals(original, original.toBuilder().build());
    }
}
