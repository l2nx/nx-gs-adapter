package app.l2nx.gs.adapter.api.kafka.sync.settings.appsettings;

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
import org.junit.jupiter.api.Test;

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
                .startCooldownMinutes(15)
                .returnThresholdMinutes(30)
                .restartWindowMinutes(10)
                .fishingMinDurationMs(600_000L)
                .fishingMaxDurationMs(7_200_000L)
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

    @Test
    void build_shouldRejectNegativeMinutes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().restartWindowMinutes(-1).build());
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
        assertNotEquals(original, original.toBuilder().startCooldownMinutes(16).build());
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
