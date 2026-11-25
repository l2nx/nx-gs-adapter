package app.l2nx.gs.adapter.api.kafka.events.character.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SessionSourceTest {

    @Test
    void values_shouldExposeExactWireNames() {
        assertArrayEquals(
                new String[] {"CLIENT", "APP"},
                Arrays.stream(SessionSource.values()).map(Enum::name).toArray(String[]::new));
    }

    @ParameterizedTest
    @EnumSource(SessionSource.class)
    void fromToken_shouldResolveConstant_whenTokenIsItsName(SessionSource expected) {
        assertEquals(expected, SessionSource.fromToken(expected.name()));
    }

    @Test
    void fromToken_shouldReturnNull_whenTokenUnknownOrNull() {
        assertNull(SessionSource.fromToken("in_game"));
        assertNull(SessionSource.fromToken(null));
    }
}
