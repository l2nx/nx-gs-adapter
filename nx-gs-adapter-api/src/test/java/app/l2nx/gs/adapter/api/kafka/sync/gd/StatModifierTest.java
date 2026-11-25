package app.l2nx.gs.adapter.api.kafka.sync.gd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StatModifierTest {

    private static Stream<Arguments> withoutRequiredField() {
        return Stream.of(
                Arguments.of("statRaw", StatModifier.builder().operationRaw("ADD")),
                Arguments.of("operationRaw", StatModifier.builder().statRaw("MAX_HP")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withoutRequiredField")
    void build_shouldReject_whenRequiredFieldMissing(String missing, StatModifier.Builder builder) {
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void build_shouldKeepRawOnly_whenNoExactCanonicalMeaning() {
        StatModifier actual = StatModifier.builder()
                .statRaw("CUSTOM_STAT")
                .operationRaw("MUL")
                .value(1.1)
                .build();

        assertNull(actual.getStat());
        assertNull(actual.getOperation());
        assertEquals("CUSTOM_STAT", actual.getStatRaw());
    }
}
