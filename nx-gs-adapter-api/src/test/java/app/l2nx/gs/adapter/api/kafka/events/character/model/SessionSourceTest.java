package app.l2nx.gs.adapter.api.kafka.events.character.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SessionSourceTest {

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
