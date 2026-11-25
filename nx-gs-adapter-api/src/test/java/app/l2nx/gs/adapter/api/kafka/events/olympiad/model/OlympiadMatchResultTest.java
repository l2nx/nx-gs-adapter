package app.l2nx.gs.adapter.api.kafka.events.olympiad.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OlympiadMatchResultTest {

    @Test
    void values_shouldExposeExactWireNames() {
        assertArrayEquals(
                new String[] {"WIN", "LOSS", "DRAW"},
                Arrays.stream(OlympiadMatchResult.values()).map(Enum::name).toArray(String[]::new));
    }
}
