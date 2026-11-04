package app.l2nx.gs.adapter.api.domain.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemSnapshotTest {

    private static final Gson GSON = new Gson();

    private static ItemSnapshot.Builder minimal() {
        return ItemSnapshot.builder().itemId(268500001L).itemTemplateId(6577L);
    }

    private static ItemSnapshot full() {
        Map<Attribute, Integer> attributes = new LinkedHashMap<Attribute, Integer>();
        attributes.put(Attribute.FIRE, 150);
        attributes.put(Attribute.HOLY, 60);
        return minimal()
                .count(3L)
                .enchantLevel(7)
                .attributes(attributes)
                .augmentation(new ItemAugmentation(16341, 18000))
                .build();
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "itemId={0}")
        @CsvSource({"0", "-1"})
        void constructor_shouldReject_whenItemIdNotPositive(long itemId) {
            ItemSnapshot.Builder builder = minimal().itemId(itemId);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @ParameterizedTest(name = "itemTemplateId={0}")
        @CsvSource({"0", "-1"})
        void constructor_shouldReject_whenItemTemplateIdNotPositive(long itemTemplateId) {
            ItemSnapshot.Builder builder = minimal().itemTemplateId(itemTemplateId);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldAcceptIdsAboveIntRange() {
            ItemSnapshot actual = minimal().itemId(5_000_000_000L).build();

            assertEquals(5_000_000_000L, actual.getItemId());
        }

        @Test
        void constructor_shouldKeepOptionalFieldsNull_whenBuilderOmits() {
            ItemSnapshot actual = minimal().build();

            assertNull(actual.getCount());
            assertNull(actual.getEnchantLevel());
            assertNull(actual.getAttributes());
            assertNull(actual.getAugmentation());
        }

        @Test
        void constructor_shouldKeepZeroEnchantApartFromNull() {
            assertEquals(Integer.valueOf(0), minimal().enchantLevel(0).build().getEnchantLevel());
        }
    }

    @Nested
    class Attributes {

        @Test
        void getAttributes_shouldStayEmpty_whenItemHasNone() {
            ItemSnapshot actual = minimal().attributes(Collections.emptyMap()).build();

            assertNotNull(actual.getAttributes());
            assertTrue(actual.getAttributes().isEmpty());
        }

        @Test
        void getAttributes_shouldBeUnmodifiable() {
            ItemSnapshot actual = full();

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> actual.getAttributes().put(Attribute.DARK, 1));
        }

        @Test
        void constructor_shouldDefensivelyCopyAttributes() {
            Map<Attribute, Integer> source = new HashMap<Attribute, Integer>();
            source.put(Attribute.WATER, 100);
            ItemSnapshot actual = minimal().attributes(source).build();

            source.put(Attribute.FIRE, 50);

            assertEquals(Collections.singletonMap(Attribute.WATER, 100), actual.getAttributes());
        }

        @Test
        void constructor_shouldKeepAttributeOrder() {
            Map<Attribute, Integer> source = new LinkedHashMap<Attribute, Integer>();
            source.put(Attribute.DARK, 1);
            source.put(Attribute.FIRE, 2);

            ItemSnapshot actual = minimal().attributes(source).build();

            List<Attribute> expected = new ArrayList<Attribute>(source.keySet());
            assertEquals(
                    expected, new ArrayList<Attribute>(actual.getAttributes().keySet()));
        }
    }

    @Nested
    class ValueSemantics {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            ItemSnapshot original = full();

            ItemSnapshot copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }

        @Test
        void equals_shouldDiffer_whenCountDiffers() {
            assertNotEquals(full(), full().toBuilder().count(2L).build());
        }

        @Test
        void equals_shouldDiffer_whenAttributesNotReportedOrNone() {
            ItemSnapshot notReported = minimal().build();
            ItemSnapshot none = minimal().attributes(Collections.emptyMap()).build();

            assertNotEquals(notReported, none);
        }

        @Test
        void equals_shouldNotDependOnTheMapImplementation() {
            Map<Attribute, Integer> enumMap = new EnumMap<Attribute, Integer>(Attribute.class);
            enumMap.put(Attribute.FIRE, 150);

            assertEquals(
                    minimal().attributes(enumMap).build(),
                    minimal()
                            .attributes(Collections.singletonMap(Attribute.FIRE, 150))
                            .build());
        }

        @Test
        void toString_shouldExposeAllFields() {
            String actual = full().toString();

            assertTrue(actual.contains("itemId=268500001"));
            assertTrue(actual.contains("itemTemplateId=6577"));
            assertTrue(actual.contains("count=3"));
            assertTrue(actual.contains("enchantLevel=7"));
            assertTrue(actual.contains("FIRE=150"));
            assertTrue(actual.contains("option1Id=16341"));
        }
    }

    @Nested
    class Wire {

        @Test
        void gson_shouldRoundtripAllFields() {
            ItemSnapshot original = full();

            assertEquals(original, GSON.fromJson(GSON.toJson(original), ItemSnapshot.class));
        }

        @Test
        void gson_shouldTellNotReportedFromNone() {
            String notReported = GSON.toJson(minimal().build());
            String none =
                    GSON.toJson(minimal().attributes(Collections.emptyMap()).build());

            assertFalse(notReported.contains("attributes"));
            assertTrue(none.contains("\"attributes\":{}"));
        }

        @Test
        void gson_shouldKeyAttributesByElementName() {
            String actual = GSON.toJson(minimal()
                    .attributes(Collections.singletonMap(Attribute.FIRE, 150))
                    .build());

            assertTrue(actual.contains("\"attributes\":{\"FIRE\":150}"));
        }
    }
}
