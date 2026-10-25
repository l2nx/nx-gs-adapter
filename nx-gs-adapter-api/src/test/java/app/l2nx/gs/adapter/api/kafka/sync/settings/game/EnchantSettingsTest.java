package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnchantSettingsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeCaps")
    void build_shouldRejectNegativeCap(String name, UnaryOperator<EnchantSettings.Builder> change) {
        assertThrows(
                IllegalArgumentException.class,
                () -> change.apply(EnchantSettings.builder()).build());
    }

    static Stream<Arguments> negativeCaps() {
        return Stream.of(
                Arguments.of("maxWeapon", (UnaryOperator<EnchantSettings.Builder>) b -> b.maxWeapon(-1)),
                Arguments.of("maxArmor", (UnaryOperator<EnchantSettings.Builder>) b -> b.maxArmor(-1)),
                Arguments.of("maxJewelry", (UnaryOperator<EnchantSettings.Builder>) b -> b.maxJewelry(-1)));
    }

    @Test
    void build_shouldAcceptNullAndZeroCaps() {
        EnchantSettings actual = EnchantSettings.builder().maxWeapon(0).build();

        assertEquals(0, actual.getMaxWeapon());
        assertNull(actual.getMaxArmor());
        assertNull(actual.getMaxJewelry());
    }
}
