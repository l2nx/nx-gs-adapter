package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttributeChanceTest {

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, Double.NaN})
    void build_shouldRejectPercentOutOfRange(double chancePercent) {
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeChance.builder()
                        .type(AttributeItemType.STONE)
                        .chancePercent(chancePercent)
                        .build());
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

    @Test
    void build_shouldRejectNullType() {
        assertThrows(
                NullPointerException.class,
                () -> AttributeChance.builder().chancePercent(1).build());
    }
}
