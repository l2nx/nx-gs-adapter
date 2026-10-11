package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkillStatModifierTest {

    @Test
    void build_shouldRejectMissingStatRaw() {
        assertThrows(
                NullPointerException.class,
                () -> SkillStatModifier.builder().operationRaw("ADD").build());
    }

    @Test
    void build_shouldRejectMissingOperationRaw() {
        assertThrows(
                NullPointerException.class,
                () -> SkillStatModifier.builder().statRaw("MAX_HP").build());
    }

    @Test
    void build_shouldKeepRawOnly_whenNoExactCanonicalMeaning() {
        SkillStatModifier actual = SkillStatModifier.builder()
                .statRaw("CUSTOM_STAT")
                .operationRaw("MUL")
                .value(1.1)
                .build();

        assertNull(actual.getStat());
        assertNull(actual.getOperation());
        assertEquals("CUSTOM_STAT", actual.getStatRaw());
    }
}
