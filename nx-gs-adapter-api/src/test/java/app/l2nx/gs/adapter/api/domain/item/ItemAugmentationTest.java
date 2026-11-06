package app.l2nx.gs.adapter.api.domain.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ItemAugmentationTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        ItemAugmentation augmentation =
                ItemAugmentation.builder().option1Id(1000).option2Id(2000).build();

        assertEquals(1000, augmentation.getOption1Id());
        assertEquals(Integer.valueOf(2000), augmentation.getOption2Id());
    }

    @Test
    void option2Id_shouldBeNullable_whenItemHasSingleOption() {
        ItemAugmentation augmentation =
                ItemAugmentation.builder().option1Id(1000).build();

        assertEquals(1000, augmentation.getOption1Id());
        assertNull(augmentation.getOption2Id());
    }

    @Test
    void builder_andConstructor_shouldProduceEqualObjects() {
        ItemAugmentation fromBuilder =
                ItemAugmentation.builder().option1Id(1000).option2Id(2000).build();
        ItemAugmentation fromCtor = new ItemAugmentation(1000, 2000);

        assertEquals(fromCtor, fromBuilder);
        assertEquals(fromCtor.hashCode(), fromBuilder.hashCode());
    }

    @Test
    void equals_shouldDiffer_whenOption2IdDiffers() {
        ItemAugmentation singleOption =
                ItemAugmentation.builder().option1Id(1000).build();
        ItemAugmentation dualOption =
                ItemAugmentation.builder().option1Id(1000).option2Id(2000).build();

        assertNotEquals(singleOption, dualOption);
        assertNotEquals(singleOption.hashCode(), dualOption.hashCode());
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        ItemAugmentation original = new ItemAugmentation(1000, 2000);

        assertEquals(original, original.toBuilder().build());
    }
}
