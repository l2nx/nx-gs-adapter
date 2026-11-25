package app.l2nx.gs.adapter.api.kafka.events.olympiad.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OlympiadMatchReasonTest {

    @Test
    void values_shouldExposeExactWireNames() {
        assertArrayEquals(
                new String[] {
                    "NORMAL",
                    "OPPONENT_DEFAULTED",
                    "SELF_DEFAULTED",
                    "BOTH_DEFAULTED",
                    "OPPONENT_DISCONNECTED",
                    "SELF_DISCONNECTED",
                    "BOTH_DISCONNECTED",
                    "BOTH_OFFLINE",
                    "TIMEOUT"
                },
                Arrays.stream(OlympiadMatchReason.values()).map(Enum::name).toArray(String[]::new));
    }
}
