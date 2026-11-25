package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SkillLearnItemSetTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidItems")
    void build_shouldReject_whenItemsAreMissingOrEmpty(
            String name, Class<? extends Exception> expected, SkillLearnItemSet.Builder builder) {
        assertThrows(expected, builder::build);
    }

    static Stream<Arguments> invalidItems() {
        return Stream.of(
                Arguments.of("missing", NullPointerException.class, SkillLearnItemSet.builder()),
                Arguments.of(
                        "empty",
                        IllegalArgumentException.class,
                        SkillLearnItemSet.builder().items(new ArrayList<SkillLearnItem>())));
    }
}
