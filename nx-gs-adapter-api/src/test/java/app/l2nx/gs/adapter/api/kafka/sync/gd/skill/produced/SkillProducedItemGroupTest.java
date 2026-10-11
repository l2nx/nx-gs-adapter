package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.produced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SkillProducedItemGroupTest {

    @Test
    void build_shouldRejectMissingItems() {
        assertThrows(
                NullPointerException.class,
                () -> SkillProducedItemGroup.builder().build());
    }

    @Test
    void build_shouldRejectEmptyItems() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkillProducedItemGroup.builder()
                        .items(new ArrayList<SkillProducedItem>())
                        .build());
    }

    @Test
    void build_shouldAcceptSingleItem() {
        SkillProducedItemGroup actual = SkillProducedItemGroup.builder()
                .items(Collections.singletonList(
                        SkillProducedItem.builder().itemTemplateId(5001).build()))
                .build();

        assertEquals(1, actual.getItems().size());
    }
}
