package app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.ItemOptionTrigger;
import app.l2nx.gs.adapter.api.domain.stat.Stat;
import app.l2nx.gs.adapter.api.domain.stat.StatOperation;
import app.l2nx.gs.adapter.api.kafka.sync.gd.StatModifier;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemOptionTest {

    private static ItemOption fullyPopulated() {
        return ItemOption.builder()
                .id(16001)
                .statModifiers(Collections.singletonList(StatModifier.builder()
                        .stat(Stat.MAX_HP)
                        .statRaw("engineStat")
                        .operation(StatOperation.ADD)
                        .operationRaw("engineOperation")
                        .value(120.0)
                        .build()))
                .skills(Arrays.asList(
                        ItemOptionSkill.builder().skillId(3240).skillLevel(10).build(),
                        ItemOptionSkill.builder()
                                .skillId(3241)
                                .skillLevel(10)
                                .trigger(ItemOptionTrigger.CRITICAL)
                                .chancePercent(2.5)
                                .build()))
                .build();
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals(16001, actual.get("id").getAsInt());
            assertEquals(
                    "MAX_HP",
                    actual.getAsJsonArray("statModifiers")
                            .get(0)
                            .getAsJsonObject()
                            .get("stat")
                            .getAsString());
            JsonObject triggered = actual.getAsJsonArray("skills").get(1).getAsJsonObject();
            assertEquals(3241, triggered.get("skillId").getAsInt());
            assertEquals(10, triggered.get("skillLevel").getAsInt());
            assertEquals("CRITICAL", triggered.get("trigger").getAsString());
            assertEquals(2.5, triggered.get("chancePercent").getAsDouble());
        }

        @Test
        void readValue_shouldRoundTripEveryField() throws IOException {
            ItemOption expected = fullyPopulated();

            ItemOption actual = mapper().readValue(mapper().writeValueAsString(expected), ItemOption.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldKeepSkill_whenTriggerUnknownToConsumer() throws IOException {
            String json = "{\"id\":1,\"statModifiers\":[],"
                    + "\"skills\":[{\"skillId\":7,\"skillLevel\":1,\"trigger\":\"ON_FUTURE\",\"chancePercent\":5.0}]}";

            ItemOption actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json, ItemOption.class);

            ItemOptionSkill skill = actual.getSkills().get(0);
            assertNull(skill.getTrigger());
            assertEquals(Double.valueOf(5.0), skill.getChancePercent());
        }
    }

    @Nested
    class BuildSkill {

        @Test
        void build_shouldGrantSkill_whenNoTrigger() {
            ItemOptionSkill actual =
                    ItemOptionSkill.builder().skillId(1).skillLevel(1).build();

            assertNull(actual.getTrigger());
            assertNull(actual.getChancePercent());
        }

        @ParameterizedTest(name = "{0} with chance {1}")
        @CsvSource({", 10.0", "ATTACK,", "MAGIC,", "CRITICAL,"})
        void build_shouldRejectChance_whenNotPairedWithTrigger(ItemOptionTrigger trigger, Double chancePercent) {
            ItemOptionSkill.Builder builder = ItemOptionSkill.builder()
                    .skillId(1)
                    .skillLevel(1)
                    .trigger(trigger)
                    .chancePercent(chancePercent);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {-0.1, 0.0, 100.1, Double.NaN})
        void constructor_shouldRejectChance_whenOutOfRange(double chancePercent) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ItemOptionSkill(1, 1, ItemOptionTrigger.ATTACK, chancePercent));
        }

        @Test
        void build_shouldRejectZeroChance_withTrigger() {
            ItemOptionSkill.Builder builder = ItemOptionSkill.builder()
                    .skillId(1)
                    .skillLevel(1)
                    .trigger(ItemOptionTrigger.MAGIC)
                    .chancePercent(0.0);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {Double.MIN_VALUE, 100.0})
        void constructor_shouldAcceptChance_atRangeBounds(double chancePercent) {
            ItemOptionSkill actual = new ItemOptionSkill(1, 1, ItemOptionTrigger.ATTACK, chancePercent);

            assertEquals(Double.valueOf(chancePercent), actual.getChancePercent());
        }
    }

    @Test
    void build_shouldRejectNullLists() {
        assertThrows(
                NullPointerException.class,
                () -> ItemOption.builder().id(1).skills(null).build());
    }

    @Test
    void toBuilder_shouldCopyEveryField() {
        ItemOption expected = fullyPopulated();

        ItemOption actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
