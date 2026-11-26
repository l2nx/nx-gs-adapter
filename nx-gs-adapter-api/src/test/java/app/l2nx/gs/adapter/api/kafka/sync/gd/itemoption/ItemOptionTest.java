package app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.ItemSkillType;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemOptionTest {

    private static StatModifier statModifier() {
        return StatModifier.builder()
                .stat(Stat.MAX_HP)
                .statRaw("engineStat")
                .operation(StatOperation.ADD)
                .operationRaw("engineOperation")
                .value(120.0)
                .build();
    }

    private static ItemOption fullyPopulated() {
        return ItemOption.builder()
                .id(16001)
                .statModifiers(Collections.singletonList(statModifier()))
                .skills(Arrays.asList(
                        ItemOptionSkill.builder()
                                .skillId(3240)
                                .skillLevel(10)
                                .type(ItemSkillType.EQUIPPED)
                                .typeRaw("passive_skill")
                                .build(),
                        ItemOptionSkill.builder()
                                .skillId(3241)
                                .skillLevel(10)
                                .type(ItemSkillType.ON_NORMAL_ATTACK_CRITICAL_HIT)
                                .typeRaw("CRITICAL")
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
            assertEquals("ON_NORMAL_ATTACK_CRITICAL_HIT", triggered.get("type").getAsString());
            assertEquals("CRITICAL", triggered.get("typeRaw").getAsString());
            assertEquals(2.5, triggered.get("chancePercent").getAsDouble());
        }

        @Test
        void readValue_shouldKeepSkill_whenTypeUnknownToConsumer() throws IOException {
            String json = "{\"id\":1,\"statModifiers\":[],\"skills\":[{\"skillId\":7,\"skillLevel\":1,"
                    + "\"type\":\"ON_FUTURE\",\"typeRaw\":\"ENGINE_X\",\"chancePercent\":5.0}]}";

            ItemOption actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json, ItemOption.class);

            ItemOptionSkill skill = actual.getSkills().get(0);
            assertNull(skill.getType());
            assertEquals("ENGINE_X", skill.getTypeRaw());
            assertEquals(Double.valueOf(5.0), skill.getChancePercent());
        }
    }

    @Nested
    class BuildSkill {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ItemSkillType.class, names = "EQUIPPED_ENCHANT", mode = EnumSource.Mode.EXCLUDE)
        void build_shouldAccept_whenChanceIsPairedWithType(ItemSkillType type) {
            ItemOptionSkill actual = ItemOptionSkill.builder()
                    .skillId(1)
                    .skillLevel(1)
                    .type(type)
                    .typeRaw("T")
                    .chancePercent(type.chanceEvent() ? 10.0 : null)
                    .build();

            assertEquals(type, actual.getType());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ItemSkillType.class)
        void build_shouldReject_whenChancePairingIsBroken(ItemSkillType type) {
            ItemOptionSkill.Builder builder = ItemOptionSkill.builder()
                    .skillId(1)
                    .skillLevel(1)
                    .type(type)
                    .typeRaw("T")
                    .chancePercent(type.chanceEvent() ? null : 10.0);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void build_shouldReject_whenTypeNeedsAnEnchantThreshold() {
            ItemOptionSkill.Builder builder = ItemOptionSkill.builder()
                    .skillId(1)
                    .skillLevel(1)
                    .type(ItemSkillType.EQUIPPED_ENCHANT)
                    .typeRaw("T");

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void build_shouldSkipPairing_whenTypeIsUnknown() {
            ItemOptionSkill actual = ItemOptionSkill.builder()
                    .skillId(1)
                    .skillLevel(1)
                    .typeRaw("BUILD_CUSTOM")
                    .chancePercent(10.0)
                    .build();

            assertNull(actual.getType());
            assertEquals("BUILD_CUSTOM", actual.getTypeRaw());
        }

        @ParameterizedTest(name = "typeRaw=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void constructor_shouldReject_whenTypeRawIsMissing(String typeRaw) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ItemOptionSkill(1, 1, ItemSkillType.EQUIPPED, typeRaw, null));
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {-0.1, 0.0, 100.1, Double.NaN})
        void constructor_shouldRejectChance_whenOutOfRange(double chancePercent) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ItemOptionSkill(1, 1, ItemSkillType.ON_PHYSICAL_HIT, "T", chancePercent));
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(doubles = {Double.MIN_VALUE, 100.0})
        void constructor_shouldAcceptChance_atRangeBounds(double chancePercent) {
            ItemOptionSkill actual = new ItemOptionSkill(1, 1, ItemSkillType.ON_PHYSICAL_HIT, "T", chancePercent);

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
    void toBuilderAndJackson_shouldRoundTripEveryField() throws IOException {
        ItemOption expected = fullyPopulated();

        ItemOption actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(16001, actual.getId());
        assertEquals(Collections.singletonList(statModifier()), actual.getStatModifiers());
        StatModifier modifier = actual.getStatModifiers().get(0);
        assertEquals(Stat.MAX_HP, modifier.getStat());
        assertEquals("engineStat", modifier.getStatRaw());
        assertEquals(StatOperation.ADD, modifier.getOperation());
        assertEquals("engineOperation", modifier.getOperationRaw());
        assertEquals(120.0, modifier.getValue());
        assertEquals(2, actual.getSkills().size());
        assertEquals(
                new ItemOptionSkill(3240, 10, ItemSkillType.EQUIPPED, "passive_skill", null),
                actual.getSkills().get(0));

        ItemOptionSkill triggered = actual.getSkills().get(1);
        assertEquals(3241, triggered.getSkillId());
        assertEquals(10, triggered.getSkillLevel());
        assertEquals(ItemSkillType.ON_NORMAL_ATTACK_CRITICAL_HIT, triggered.getType());
        assertEquals("CRITICAL", triggered.getTypeRaw());
        assertEquals(Double.valueOf(2.5), triggered.getChancePercent());

        assertEquals(expected, mapper().readValue(mapper().writeValueAsString(expected), ItemOption.class));
    }
}
