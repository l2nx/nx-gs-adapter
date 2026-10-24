package app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.LifeStoneGrade;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LifeStoneTest {

    private static AugmentationOutcome outcome(int groupId, int slot, int itemOptionId, double chancePercent) {
        return AugmentationOutcome.builder()
                .augmentationTargetGroupId(groupId)
                .slot(slot)
                .itemOptionId(itemOptionId)
                .chancePercent(chancePercent)
                .build();
    }

    private static LifeStone fullyPopulated() {
        return LifeStone.builder()
                .itemTemplateId(8762)
                .grade(LifeStoneGrade.TOP)
                .gradeRaw("3")
                .level(76)
                .minCharacterLevel(76)
                .augmentationTargetGroupIds(Arrays.asList(1, 2))
                .outcomes(Arrays.asList(outcome(1, 1, 16001, 40.0), outcome(1, 2, 16500, 0.25)))
                .build();
    }

    private static LifeStone.Builder stone(AugmentationOutcome... outcomes) {
        return LifeStone.builder()
                .itemTemplateId(1)
                .augmentationTargetGroupIds(Collections.singletonList(1))
                .outcomes(Arrays.asList(outcomes));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals(8762, actual.get("itemTemplateId").getAsInt());
            assertEquals("TOP", actual.get("grade").getAsString());
            assertEquals("3", actual.get("gradeRaw").getAsString());
            assertEquals(76, actual.get("minCharacterLevel").getAsInt());
            assertEquals(2, actual.getAsJsonArray("augmentationTargetGroupIds").size());
            JsonObject outcome = actual.getAsJsonArray("outcomes").get(1).getAsJsonObject();
            assertEquals(1, outcome.get("augmentationTargetGroupId").getAsInt());
            assertEquals(2, outcome.get("slot").getAsInt());
            assertEquals(16500, outcome.get("itemOptionId").getAsInt());
            assertEquals(0.25, outcome.get("chancePercent").getAsDouble());
        }

        @Test
        void readValue_shouldRoundTripEveryField() throws IOException {
            LifeStone expected = fullyPopulated();

            LifeStone actual = mapper().readValue(mapper().writeValueAsString(expected), LifeStone.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldKeepGradeRaw_whenGradeUnknownToConsumer() throws IOException {
            String json = "{\"itemTemplateId\":1,\"grade\":\"FUTURE\",\"gradeRaw\":\"9\","
                    + "\"augmentationTargetGroupIds\":[],\"outcomes\":[]}";

            LifeStone actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json, LifeStone.class);

            assertNull(actual.getGrade());
            assertEquals("9", actual.getGradeRaw());
        }

        @Test
        void readValue_shouldRejectStone_whenSlotChancesExceed100() {
            String json = "{\"itemTemplateId\":1,\"augmentationTargetGroupIds\":[1],\"outcomes\":["
                    + "{\"augmentationTargetGroupId\":1,\"slot\":1,\"itemOptionId\":10,\"chancePercent\":60.0},"
                    + "{\"augmentationTargetGroupId\":1,\"slot\":1,\"itemOptionId\":11,\"chancePercent\":41.0}]}";

            JsonMappingException actual =
                    assertThrows(JsonMappingException.class, () -> mapper().readValue(json, LifeStone.class));

            assertInstanceOf(IllegalArgumentException.class, actual.getCause());
        }
    }

    @Nested
    class Validate {

        @Test
        void build_shouldAcceptSlotSum_whenItIs100UpToRounding() {
            List<AugmentationOutcome> outcomes = new ArrayList<AugmentationOutcome>();
            for (int i = 0; i < 10; i++) {
                outcomes.add(outcome(1, 1, 100 + i, 10.000000001));
            }

            assertDoesNotThrow(() -> stone().outcomes(outcomes).build());
        }

        @Test
        void build_shouldCountSlotsSeparately() {
            assertDoesNotThrow(() ->
                    stone(outcome(1, 1, 10, 70.0), outcome(1, 2, 10, 70.0)).build());
        }

        @Test
        void build_shouldRejectSlotSum_whenAbove100() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stone(outcome(1, 2, 10, 50.0), outcome(1, 2, 11, 50.1))
                            .build());
        }

        @Test
        void build_shouldRejectOutcome_whenGroupNotApplicable() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stone(outcome(2, 1, 10, 5.0)).build());
        }

        @Test
        void build_shouldRejectRepeatedOutcome() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stone(outcome(1, 1, 10, 5.0), outcome(1, 1, 10, 5.0)).build());
        }

        @Test
        void build_shouldAcceptSameOption_inOtherSlotOrGroup() {
            assertDoesNotThrow(() -> stone(outcome(1, 1, 10, 5.0), outcome(1, 2, 10, 5.0), outcome(2, 1, 10, 5.0))
                    .augmentationTargetGroupIds(Arrays.asList(1, 2))
                    .build());
        }

        @Test
        void build_shouldKeepKeysApart_whenIdsAreNegative() {
            assertDoesNotThrow(() -> stone(outcome(-1, 1, -1, 60.0), outcome(-1, 1, 1, 30.0), outcome(1, 1, -1, 60.0))
                    .augmentationTargetGroupIds(Arrays.asList(-1, 1))
                    .build());
        }

        @Test
        void build_shouldSumChancesPerGroup() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stone(outcome(2, 1, 10, 30.0), outcome(1, 1, 11, 90.0), outcome(2, 1, 12, 70.1))
                            .augmentationTargetGroupIds(Arrays.asList(1, 2))
                            .build());
        }

        @Test
        void build_shouldRejectGrade_whenGradeRawMissing() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stone().grade(LifeStoneGrade.TOP).build());
        }

        @Test
        void build_shouldAcceptGradeRaw_whenGradeUnknown() {
            LifeStone actual = stone().gradeRaw("9").build();

            assertNull(actual.getGrade());
            assertEquals("9", actual.getGradeRaw());
        }

        @Test
        void build_shouldRejectRepeatedGroup() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stone().augmentationTargetGroupIds(Arrays.asList(1, 1))
                            .build());
        }

        @Test
        void build_shouldAcceptApplicableGroupWithoutOutcomes() {
            LifeStone actual =
                    stone().augmentationTargetGroupIds(Arrays.asList(1, 2)).build();

            assertEquals(Arrays.asList(1, 2), actual.getAugmentationTargetGroupIds());
        }

        @ParameterizedTest(name = "slot {0}")
        @ValueSource(ints = {0, 3})
        void outcome_shouldRejectSlot_whenNotOneOrTwo(int slot) {
            assertThrows(IllegalArgumentException.class, () -> outcome(1, slot, 10, 5.0));
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {0.0, -1.0, 100.01, Double.NaN})
        void outcome_shouldRejectChance_whenOutsideZeroExclusiveToHundred(double chancePercent) {
            assertThrows(IllegalArgumentException.class, () -> outcome(1, 1, 10, chancePercent));
        }
    }

    @Test
    void toBuilder_shouldCopyEveryField() {
        LifeStone expected = fullyPopulated();

        LifeStone actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
