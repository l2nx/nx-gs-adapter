package app.l2nx.gs.adapter.api.spi.model;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class RuntimeRowTest {

    @Test
    void constructor_shouldDefaultStateStampToZero_whenNotGiven() {
        assertEquals(0L, new RuntimeRow<String>(1L, "dto").getStateStamp());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(RuntimeRow.class).verify();
    }
}
