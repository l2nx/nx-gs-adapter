package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrivateStoreFeesTest {

    @Test
    void requirePercent_shouldReturnValue_whenWithinRange() {
        assertEquals(0, PrivateStoreFees.requirePercent(0, "fee"));
        assertEquals(PrivateStoreFees.MAX_FEE_PERCENT, PrivateStoreFees.requirePercent(50, "fee"));
    }

    @Test
    void requirePercent_shouldThrowNamedMessage_whenNull() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> PrivateStoreFees.requirePercent(null, "fee"));
        assertEquals("fee is required", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 51, Integer.MAX_VALUE})
    void requirePercent_shouldThrow_whenOutOfRange(int value) {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> PrivateStoreFees.requirePercent(value, "fee"));
        assertEquals("fee must be in 0..50 (got " + value + ")", ex.getMessage());
    }

    @Test
    void checkPercent_shouldAcceptNull() {
        assertDoesNotThrow(() -> PrivateStoreFees.checkPercent(null, "fee"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 51})
    void checkPercent_shouldThrow_whenPresentAndOutOfRange(int value) {
        assertThrows(IllegalArgumentException.class, () -> PrivateStoreFees.checkPercent(value, "fee"));
    }
}
