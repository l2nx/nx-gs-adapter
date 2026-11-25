package app.l2nx.gs.adapter.api.kafka.sync.gd.experiencelevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExperienceLevelTest {

    @ParameterizedTest
    @CsvSource({"80, 4174867851", "40, 6733999"})
    void toBuilder_shouldProduceEqualCopy(int level, long requiredExp) {
        ExperienceLevel original =
                ExperienceLevel.builder().level(level).requiredExp(requiredExp).build();

        ExperienceLevel copy = original.toBuilder().build();

        assertEquals(level, original.getLevel());
        assertEquals(requiredExp, original.getRequiredExp());
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ExperienceLevel.class).verify();
    }
}
