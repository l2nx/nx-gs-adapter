package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PrivateStoreSideTest {

    @Test
    void values_shouldExposeExactWireNames() {
        // Pins the enum-literal wire format so a constant rename fails here, not on the platform.
        assertArrayEquals(
                new String[] {"ASK", "BID"},
                Arrays.stream(PrivateStoreSide.values()).map(Enum::name).toArray(String[]::new));
    }
}
