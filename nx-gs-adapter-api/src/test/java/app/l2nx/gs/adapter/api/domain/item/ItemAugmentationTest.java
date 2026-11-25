package app.l2nx.gs.adapter.api.domain.item;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ItemAugmentationTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        ItemAugmentation augmentation =
                ItemAugmentation.builder().option1Id(1000).option2Id(2000).build();

        assertEquals(1000, augmentation.getOption1Id());
        assertEquals(Integer.valueOf(2000), augmentation.getOption2Id());
        assertEquals(augmentation, augmentation.toBuilder().build());
    }

    @Test
    void option2Id_shouldBeNullable_whenItemHasSingleOption() {
        ItemAugmentation augmentation =
                ItemAugmentation.builder().option1Id(1000).build();

        assertEquals(1000, augmentation.getOption1Id());
        assertNull(augmentation.getOption2Id());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ItemAugmentation.class).verify();
    }
}
