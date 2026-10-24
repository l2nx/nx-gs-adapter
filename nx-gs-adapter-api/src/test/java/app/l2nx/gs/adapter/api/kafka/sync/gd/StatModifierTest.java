package app.l2nx.gs.adapter.api.kafka.sync.gd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StatModifierTest {

    @Test
    void build_shouldRejectMissingStatRaw() {
        assertThrows(
                NullPointerException.class,
                () -> StatModifier.builder().operationRaw("ADD").build());
    }

    @Test
    void build_shouldRejectMissingOperationRaw() {
        assertThrows(
                NullPointerException.class,
                () -> StatModifier.builder().statRaw("MAX_HP").build());
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
