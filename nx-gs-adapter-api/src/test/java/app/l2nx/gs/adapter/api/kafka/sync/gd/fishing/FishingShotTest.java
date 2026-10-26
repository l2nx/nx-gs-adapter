package app.l2nx.gs.adapter.api.kafka.sync.gd.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FishingShotTest {

    private static FishingShot fullyPopulated() {
        return FishingShot.builder().itemTemplateId(6540).grade(ItemGrade.S).build();
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals(6540, actual.get("itemTemplateId").getAsInt());
            assertEquals("S", actual.get("grade").getAsString());
        }

        @Test
        void toJson_shouldOmitGrade_whenShotFitsAnyRod() {
            JsonObject actual = new Gson()
                    .toJsonTree(FishingShot.builder().itemTemplateId(6540).build())
                    .getAsJsonObject();

            assertFalse(actual.has("grade"));
        }

        @Test
        void fromJson_shouldRoundTripEveryField_whenWrittenByGson() {
            FishingShot expected = fullyPopulated();

            FishingShot actual = new Gson().fromJson(new Gson().toJson(expected), FishingShot.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldRoundTripEveryField() throws IOException {
            FishingShot expected = fullyPopulated();

            FishingShot actual = mapper().readValue(mapper().writeValueAsString(expected), FishingShot.class);

            assertEquals(expected, actual);
        }
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "itemTemplateId {0}")
        @ValueSource(ints = {0, -1})
        void build_shouldRejectItemTemplateId_whenNotPositive(int itemTemplateId) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FishingShot.builder().itemTemplateId(itemTemplateId).build());
        }

        @Test
        void build_shouldRejectGrade_whenEvent() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FishingShot.builder()
                            .itemTemplateId(6540)
                            .grade(ItemGrade.EVENT)
                            .build());
        }
    }

    @Test
    void toBuilder_shouldCopyEveryField() {
        FishingShot expected = fullyPopulated();

        FishingShot actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
