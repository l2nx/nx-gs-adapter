package app.l2nx.gs.adapter.api.kafka.sync.gd.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FishingBaitTest {

    private static FishingBait fullyPopulated() {
        return FishingBait.builder()
                .itemTemplateId(7807)
                .chancePercent(40)
                .minCharacterLevel(20)
                .maxCharacterLevel(85)
                .fishingTimeMinMs(20_000L)
                .fishingTimeMaxMs(30_000L)
                .build();
    }

    private static FishingBait.Builder bait() {
        return FishingBait.builder().itemTemplateId(7807).chancePercent(40);
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals(7807, actual.get("itemTemplateId").getAsInt());
            assertEquals(40.0, actual.get("chancePercent").getAsDouble());
            assertEquals(20, actual.get("minCharacterLevel").getAsInt());
            assertEquals(85, actual.get("maxCharacterLevel").getAsInt());
            assertEquals(20_000L, actual.get("fishingTimeMinMs").getAsLong());
            assertEquals(30_000L, actual.get("fishingTimeMaxMs").getAsLong());
        }

        @Test
        void toJson_shouldOmitFieldsTheBuildLacks() {
            JsonObject actual = new Gson().toJsonTree(bait().build()).getAsJsonObject();

            assertEquals(2, actual.size(), actual.toString());
            assertFalse(actual.has("fishingTimeMinMs"));
        }

        @Test
        void fromJson_shouldRoundTripEveryField_whenWrittenByGson() {
            FishingBait expected = fullyPopulated();

            FishingBait actual = new Gson().fromJson(new Gson().toJson(expected), FishingBait.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldRoundTripEveryField() throws IOException {
            FishingBait expected = fullyPopulated();

            FishingBait actual = mapper().readValue(mapper().writeValueAsString(expected), FishingBait.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldReadAbsentNullableFieldsAsNull() throws IOException {
            FishingBait actual =
                    mapper().readValue("{\"itemTemplateId\":7807,\"chancePercent\":40}", FishingBait.class);

            assertNull(actual.getMinCharacterLevel());
            assertNull(actual.getMaxCharacterLevel());
            assertNull(actual.getFishingTimeMinMs());
            assertNull(actual.getFishingTimeMaxMs());
        }

        @Test
        void readValue_shouldRejectBait_whenChanceAbove100() {
            JsonMappingException actual = assertThrows(
                    JsonMappingException.class,
                    () -> mapper().readValue("{\"itemTemplateId\":7807,\"chancePercent\":101}", FishingBait.class));

            assertInstanceOf(IllegalArgumentException.class, actual.getCause());
        }
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {0.0, 12.5, 100.0})
        void build_shouldAcceptChance_whenWithinBounds(double chancePercent) {
            assertEquals(
                    chancePercent, bait().chancePercent(chancePercent).build().getChancePercent());
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {-0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void build_shouldRejectChance_whenOutsideZeroToHundredOrNotFinite(double chancePercent) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bait().chancePercent(chancePercent).build());
        }

        @ParameterizedTest(name = "itemTemplateId {0}")
        @ValueSource(ints = {0, -1})
        void build_shouldRejectItemTemplateId_whenNotPositive(int itemTemplateId) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bait().itemTemplateId(itemTemplateId).build());
        }

        @ParameterizedTest(name = "min {0}, max {1}")
        @CsvSource(
                value = {"0,NULL", "NULL,0", "50,49"},
                nullValues = "NULL")
        void build_shouldRejectLevelBounds_whenNotPositiveOrInverted(Integer min, Integer max) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bait().minCharacterLevel(min).maxCharacterLevel(max).build());
        }

        @ParameterizedTest(name = "min {0}, max {1}")
        @CsvSource(
                value = {"40,NULL", "NULL,40", "40,40"},
                nullValues = "NULL")
        void build_shouldAcceptLevelBounds_whenOneSidedOrEqual(Integer min, Integer max) {
            FishingBait actual =
                    bait().minCharacterLevel(min).maxCharacterLevel(max).build();

            assertEquals(min, actual.getMinCharacterLevel());
            assertEquals(max, actual.getMaxCharacterLevel());
        }

        @ParameterizedTest(name = "min {0}, max {1}")
        @CsvSource(
                value = {"1000,NULL", "NULL,1000", "0,1000", "-1,1000", "2000,1000"},
                nullValues = "NULL")
        void build_shouldRejectFishingTime_whenNotAPositiveOrderedPair(Long min, Long max) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bait().fishingTimeMinMs(min).fishingTimeMaxMs(max).build());
        }

        @Test
        void build_shouldAcceptFishingTime_whenMinEqualsMax() {
            FishingBait actual =
                    bait().fishingTimeMinMs(1000L).fishingTimeMaxMs(1000L).build();

            assertEquals(Long.valueOf(1000L), actual.getFishingTimeMinMs());
            assertEquals(Long.valueOf(1000L), actual.getFishingTimeMaxMs());
        }
    }

    @Test
    void toBuilder_shouldCopyEveryField() {
        FishingBait expected = fullyPopulated();

        FishingBait actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
