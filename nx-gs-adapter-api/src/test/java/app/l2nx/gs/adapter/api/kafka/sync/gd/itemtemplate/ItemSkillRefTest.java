package app.l2nx.gs.adapter.api.kafka.sync.gd.itemtemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.ItemSkillType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.io.IOException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemSkillRefTest {

    private static ItemSkillRef.Builder paired(ItemSkillType type) {
        return ItemSkillRef.builder()
                .id(3240)
                .level(1)
                .type(type)
                .typeRaw("ENGINE_TOKEN")
                .minEnchantLevel(type.enchantGated() ? 4 : null)
                .chancePercent(type.chanceEvent() ? 10 : null);
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Build {

        @ParameterizedTest(name = "{0}")
        @EnumSource(ItemSkillType.class)
        void build_shouldAccept_whenFieldsArePairedWithType(ItemSkillType type) {
            ItemSkillRef actual = paired(type).build();

            assertEquals(type, actual.getType());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ItemSkillType.class)
        void build_shouldReject_whenChancePairingIsBroken(ItemSkillType type) {
            ItemSkillRef.Builder builder = paired(type).chancePercent(type.chanceEvent() ? null : 10);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ItemSkillType.class)
        void build_shouldReject_whenEnchantPairingIsBroken(ItemSkillType type) {
            ItemSkillRef.Builder builder = paired(type).minEnchantLevel(type.enchantGated() ? null : 4);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void build_shouldSkipPairing_whenTypeIsUnknown() {
            ItemSkillRef actual = ItemSkillRef.builder()
                    .id(1)
                    .level(1)
                    .typeRaw("BUILD_CUSTOM")
                    .minEnchantLevel(3)
                    .chancePercent(25)
                    .build();

            assertNull(actual.getType());
            assertEquals("BUILD_CUSTOM", actual.getTypeRaw());
        }
    }

    @Nested
    class Construct {

        @ParameterizedTest(name = "typeRaw=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void constructor_shouldReject_whenTypeRawIsMissing(String typeRaw) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ItemSkillRef(1, 1, ItemSkillType.EQUIPPED, typeRaw, null, null));
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(ints = {-1, 101})
        void constructor_shouldReject_whenChanceIsOutOfRange(int chancePercent) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ItemSkillRef(1, 1, ItemSkillType.ON_PHYSICAL_HIT, "T", null, chancePercent));
        }

        @ParameterizedTest(name = "chance {0}")
        @ValueSource(ints = {0, 100})
        void constructor_shouldAcceptChance_atRangeBounds(int chancePercent) {
            ItemSkillRef actual = new ItemSkillRef(1, 1, ItemSkillType.ON_PHYSICAL_HIT, "T", null, chancePercent);

            assertEquals(Integer.valueOf(chancePercent), actual.getChancePercent());
        }

        @Test
        void constructor_shouldNotCheckPairing() {
            ItemSkillRef actual = new ItemSkillRef(1, 1, ItemSkillType.EQUIPPED, "T", null, 30);

            assertEquals(Integer.valueOf(30), actual.getChancePercent());
        }
    }

    @Nested
    class Json {

        @Test
        void readValue_shouldKeepTypeRaw_whenTypeUnknownToConsumer() throws IOException {
            String json = "{\"id\":7,\"level\":2,\"type\":\"ON_FUTURE\",\"typeRaw\":\"ENGINE_X\",\"chancePercent\":5}";

            ItemSkillRef actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json, ItemSkillRef.class);

            assertNull(actual.getType());
            assertEquals("ENGINE_X", actual.getTypeRaw());
            assertEquals(Integer.valueOf(5), actual.getChancePercent());
        }

        @Test
        void toBuilderAndJackson_shouldRoundTripEveryField() throws IOException {
            ItemSkillRef expected = paired(ItemSkillType.EQUIPPED_ENCHANT).build();

            assertEquals(expected, expected.toBuilder().build());
            assertEquals(expected, mapper().readValue(mapper().writeValueAsString(expected), ItemSkillRef.class));
            assertEquals(Integer.valueOf(4), expected.getMinEnchantLevel());
        }
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ItemSkillRef.class).verify();
    }
}
