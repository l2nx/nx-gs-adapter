package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AttributeChanceTest {

    @ParameterizedTest(name = "{0} at {1} percent")
    @MethodSource("invalidChances")
    void build_shouldReject_whenPercentIsOutOfRangeOrTypeIsNull(
            AttributeItemType type, double chancePercent, Class<? extends Exception> expected) {
        assertThrows(
                expected,
                () -> AttributeChance.builder()
                        .type(type)
                        .chancePercent(chancePercent)
                        .build());
    }

    static Stream<Arguments> invalidChances() {
        return Stream.of(
                Arguments.of(AttributeItemType.STONE, -0.01, IllegalArgumentException.class),
                Arguments.of(AttributeItemType.STONE, 100.01, IllegalArgumentException.class),
                Arguments.of(AttributeItemType.STONE, Double.NaN, IllegalArgumentException.class),
                Arguments.of(null, 1.0, NullPointerException.class));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 42.5, 100})
    void build_shouldAcceptPercentInRange_withAnyGradeAndBothTargets(double chancePercent) {
        AttributeChance actual = AttributeChance.builder()
                .type(AttributeItemType.CRYSTAL)
                .chancePercent(chancePercent)
                .build();

        assertNull(actual.getGrade());
        assertNull(actual.getTarget());
        assertEquals(chancePercent, actual.getChancePercent());
    }
}
