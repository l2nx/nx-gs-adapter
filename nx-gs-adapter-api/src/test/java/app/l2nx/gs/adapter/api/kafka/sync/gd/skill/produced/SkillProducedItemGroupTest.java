package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.produced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SkillProducedItemGroupTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidItems")
    void build_shouldReject_whenItemsAreMissingOrEmpty(
            String name, Class<? extends Exception> expected, SkillProducedItemGroup.Builder builder) {
        assertThrows(expected, builder::build);
    }

    static Stream<Arguments> invalidItems() {
        return Stream.of(
                Arguments.of("missing", NullPointerException.class, SkillProducedItemGroup.builder()),
                Arguments.of(
                        "empty",
                        IllegalArgumentException.class,
                        SkillProducedItemGroup.builder().items(new ArrayList<SkillProducedItem>())));
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
