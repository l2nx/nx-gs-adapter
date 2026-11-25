package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import app.l2nx.gs.adapter.api.domain.item.AttributeTarget;
import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AttributeLimitTest {

    private static AttributeLimit.Builder valid() {
        return AttributeLimit.builder()
                .grade(ItemGrade.S)
                .type(AttributeItemType.STONE)
                .target(AttributeTarget.WEAPON)
                .maxPower(150);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLimits")
    void build_shouldReject_whenLimitIsInvalid(
            String name, Class<? extends Exception> expected, AttributeLimit.Builder builder) {
        assertThrows(expected, builder::build);
    }

    static Stream<Arguments> invalidLimits() {
        return Stream.of(
                Arguments.of("negative power", IllegalArgumentException.class, valid().maxPower(-1)),
                Arguments.of("null grade", NullPointerException.class, valid().grade(null)),
                Arguments.of("null type", NullPointerException.class, valid().type(null)),
                Arguments.of("null target", NullPointerException.class, valid().target(null)));
    }

    @Test
    void build_shouldAcceptZeroPower() {
        assertEquals(0, valid().maxPower(0).build().getMaxPower());
    }
}
