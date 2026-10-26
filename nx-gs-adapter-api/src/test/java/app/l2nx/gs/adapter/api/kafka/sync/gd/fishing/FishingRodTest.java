package app.l2nx.gs.adapter.api.kafka.sync.gd.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FishingRodTest {

    private static FishingRod fullyPopulated() {
        return FishingRod.builder()
                .itemTemplateId(6529)
                .fishingTimeMs(120_000L)
                .fishingTimeReductionMs(5_000L)
                .chanceBonusPercent(10.0)
                .shotGrade(ItemGrade.S)
                .build();
    }

    private static FishingRod.Builder rod() {
        return FishingRod.builder().itemTemplateId(6529);
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals(6529, actual.get("itemTemplateId").getAsInt());
            assertEquals(120_000L, actual.get("fishingTimeMs").getAsLong());
            assertEquals(5_000L, actual.get("fishingTimeReductionMs").getAsLong());
            assertEquals(10.0, actual.get("chanceBonusPercent").getAsDouble());
            assertEquals("S", actual.get("shotGrade").getAsString());
        }

        @Test
        void toJson_shouldOmitFieldsTheBuildLacks() {
            JsonObject actual = new Gson().toJsonTree(rod().build()).getAsJsonObject();

            assertEquals(1, actual.size(), actual.toString());
        }

        @Test
        void fromJson_shouldRoundTripEveryField_whenWrittenByGson() {
            FishingRod expected = fullyPopulated();

            FishingRod actual = new Gson().fromJson(new Gson().toJson(expected), FishingRod.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldRoundTripEveryField() throws IOException {
            FishingRod expected = fullyPopulated();

            FishingRod actual = mapper().readValue(mapper().writeValueAsString(expected), FishingRod.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldReadUnknownShotGradeAsNull_whenConsumerIsLenient() throws IOException {
            FishingRod actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue("{\"itemTemplateId\":6529,\"shotGrade\":\"FUTURE\"}", FishingRod.class);

            assertNull(actual.getShotGrade());
        }
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "itemTemplateId {0}")
        @ValueSource(ints = {0, -1})
        void build_shouldRejectItemTemplateId_whenNotPositive(int itemTemplateId) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> rod().itemTemplateId(itemTemplateId).build());
        }

        @ParameterizedTest(name = "fishingTimeMs {0}")
        @ValueSource(longs = {0L, -1L})
        void build_shouldRejectFishingTime_whenNotPositive(long fishingTimeMs) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> rod().fishingTimeMs(fishingTimeMs).build());
        }

        @Test
        void build_shouldRejectFishingTimeReduction_whenNegative() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> rod().fishingTimeReductionMs(-1L).build());
        }

        @Test
        void build_shouldAcceptFishingTimeReduction_whenZero() {
            assertEquals(
                    Long.valueOf(0L), rod().fishingTimeReductionMs(0L).build().getFishingTimeReductionMs());
        }

        @ParameterizedTest(name = "bonus {0}")
        @ValueSource(doubles = {-100.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void build_shouldRejectChanceBonus_whenOutsideMinusHundredToHundredOrNotFinite(double chanceBonusPercent) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> rod().chanceBonusPercent(chanceBonusPercent).build());
        }

        @ParameterizedTest(name = "bonus {0}")
        @ValueSource(doubles = {-100.0, 2.5, 0.0, 100.0})
        void build_shouldAcceptChanceBonus_whenWithinBounds(double chanceBonusPercent) {
            assertEquals(
                    Double.valueOf(chanceBonusPercent),
                    rod().chanceBonusPercent(chanceBonusPercent).build().getChanceBonusPercent());
        }

        @Test
        void build_shouldRejectShotGrade_whenEvent() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> rod().shotGrade(ItemGrade.EVENT).build());
        }
    }

    @Test
    void toBuilder_shouldCopyEveryField() {
        FishingRod expected = fullyPopulated();

        FishingRod actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
