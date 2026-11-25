package app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.ActivityUsage;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.UsageStep;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.UsageUnit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CharacterActivityUsagesDtoTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static ActivityUsage.Builder usage(ActivityType type) {
        return ActivityUsage.builder().type(type).unit(UsageUnit.MS).used(0L).updatedAt(NOW);
    }

    private static ActivityUsage fishing() {
        return usage(ActivityType.FISHING)
                .activeSince(NOW)
                .used(3_600_000L)
                .limit(7_200_000L)
                .remaining(3_600_000L)
                .resetSchedule("DTSTART:20261015T030000Z\nRRULE:FREQ=DAILY")
                .steps(Arrays.asList(new UsageStep(1_800_000L, 0.5), new UsageStep(3_600_000L, 0.1)))
                .metadata(Collections.singletonMap("k", "v"))
                .build();
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                invalid(
                        "null type",
                        NullPointerException.class,
                        () -> usage(null).build()),
                invalid(
                        "null unit",
                        NullPointerException.class,
                        () -> usage(ActivityType.FISHING).unit(null).build()),
                invalid(
                        "null updatedAt",
                        NullPointerException.class,
                        () -> usage(ActivityType.FISHING).updatedAt(null).build()),
                invalid(
                        "remaining without a limit",
                        IllegalArgumentException.class,
                        () -> usage(ActivityType.FISHING).remaining(5L).build()),
                invalid(
                        "negative used",
                        IllegalArgumentException.class,
                        () -> usage(ActivityType.FISHING).used(-1L).build()),
                invalid("negative step threshold", IllegalArgumentException.class, () -> new UsageStep(-1L, 1.0)),
                invalid("NaN step multiplier", IllegalArgumentException.class, () -> new UsageStep(1L, Double.NaN)),
                invalid("negative step multiplier", IllegalArgumentException.class, () -> new UsageStep(1L, -0.1)),
                invalid(
                        "two rows of one type and key",
                        IllegalArgumentException.class,
                        () -> new CharacterActivityUsagesDto(
                                7,
                                Arrays.asList(
                                        usage(ActivityType.AUTOFARMING)
                                                .subtype("a")
                                                .build(),
                                        usage(ActivityType.AUTOFARMING)
                                                .subtype("b")
                                                .build()))));
    }

    private static Arguments invalid(String name, Class<? extends Exception> expected, Executable constructor) {
        return Arguments.of(name, expected, constructor);
    }

    @Nested
    class ActivityUsageConstructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.CharacterActivityUsagesDtoTest#invalidValues")
        void constructor_shouldReject_whenValueIsInvalid(
                String name, Class<? extends Exception> expected, Executable constructor) {
            assertThrows(expected, constructor);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", " ", "\t"})
        void build_shouldNormalizeBlankOptionalStringsToNull(String blank) {
            ActivityUsage actual = usage(ActivityType.AUTOFARMING)
                    .key(blank)
                    .subtype(blank)
                    .resetSchedule(blank)
                    .build();

            assertNull(actual.getKey());
            assertNull(actual.getSubtype());
            assertNull(actual.getResetSchedule());
        }

        @Test
        void getSteps_shouldBeEmptyAndFrozen_whenOmitted() {
            ActivityUsage actual = usage(ActivityType.FISHING).build();

            assertTrue(actual.getSteps().isEmpty());
            assertThrows(
                    UnsupportedOperationException.class, () -> actual.getSteps().add(new UsageStep(1L, 1.0)));
        }

        @Test
        void getMetadata_shouldBeNull_whenOmittedAndFrozenOtherwise() {
            assertNull(usage(ActivityType.FISHING).build().getMetadata());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> fishing().getMetadata().put("x", "y"));
        }
    }

    @Nested
    class Dto {

        @Test
        void constructor_shouldFreezeAndCopyUsages() {
            List<ActivityUsage> source = new ArrayList<ActivityUsage>(Collections.singletonList(fishing()));

            CharacterActivityUsagesDto dto = new CharacterActivityUsagesDto(7, source);
            source.clear();

            assertEquals(1, dto.getUsages().size());
            assertThrows(
                    UnsupportedOperationException.class, () -> dto.getUsages().clear());
        }

        @Test
        void getUsages_shouldBeEmpty_whenNull() {
            assertTrue(new CharacterActivityUsagesDto(7, null).getUsages().isEmpty());
        }

        @Test
        void constructor_shouldAccept_whenSameTypeHasDifferentKeys() {
            List<ActivityUsage> rows = Arrays.asList(
                    usage(ActivityType.AUTOFARMING).build(),
                    usage(ActivityType.AUTOFARMING).key("12").build(),
                    usage(ActivityType.AUTOFARMING).key("13").build());

            assertEquals(3, new CharacterActivityUsagesDto(7, rows).getUsages().size());
        }

        @Test
        void entityName_shouldBeTheWireName() {
            assertEquals("characteractivityusage", CharacterActivityUsagesDto.ENTITY_NAME);
        }
    }

    @Nested
    class GsonRoundtrip {

        @Test
        void gson_shouldWriteEnumsAsConstantNames_andRoundtrip() {
            CharacterActivityUsagesDto original = new CharacterActivityUsagesDto(
                    7,
                    Arrays.asList(
                            fishing().toBuilder().key("k").subtype("s").build(),
                            usage(ActivityType.OFFLINE_FISHING).activeSince(NOW).build()));

            String json = GSON.toJson(original);

            JsonObject first = GSON.fromJson(json, JsonObject.class)
                    .getAsJsonArray("usages")
                    .get(0)
                    .getAsJsonObject();
            assertEquals("FISHING", first.get("type").getAsString());
            assertEquals("MS", first.get("unit").getAsString());
            CharacterActivityUsagesDto actual = GSON.fromJson(json, CharacterActivityUsagesDto.class);
            assertEquals(original, actual);
            assertEquals(original, original.toBuilder().build());
            assertNotEquals(original, original.toBuilder().characterId(8).build());
            assertEquals(7, actual.getCharacterId());
            ActivityUsage usage = actual.getUsages().get(0).toBuilder().build();
            assertEquals(original.getUsages().get(0), usage);
            assertEquals(ActivityType.FISHING, usage.getType());
            assertEquals("k", usage.getKey());
            assertEquals("s", usage.getSubtype());
            assertEquals(NOW, usage.getActiveSince());
            assertEquals(UsageUnit.MS, usage.getUnit());
            assertEquals(3_600_000L, usage.getUsed());
            assertEquals(Long.valueOf(7_200_000L), usage.getLimit());
            assertEquals(Long.valueOf(3_600_000L), usage.getRemaining());
            assertEquals("DTSTART:20261015T030000Z\nRRULE:FREQ=DAILY", usage.getResetSchedule());
            assertEquals(
                    Arrays.asList(new UsageStep(1_800_000L, 0.5), new UsageStep(3_600_000L, 0.1)), usage.getSteps());
            assertEquals(Collections.singletonMap("k", "v"), usage.getMetadata());
            assertEquals(NOW, usage.getUpdatedAt());
            assertEquals(1_800_000L, usage.getSteps().get(0).getAfter());
            assertEquals(0.5, usage.getSteps().get(0).getMultiplier());
        }
    }
}
