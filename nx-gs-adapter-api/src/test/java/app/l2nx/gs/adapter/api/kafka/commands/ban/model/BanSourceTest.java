package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BanSourceTest {

    @Nested
    class FromToken {

        @ParameterizedTest(name = "{0}")
        @EnumSource(BanSource.class)
        void fromToken_shouldResolveConstant_whenTokenIsItsName(BanSource expected) {
            assertEquals(expected, BanSource.fromToken(expected.name()));
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", " ", "SOMETHING_NEW", "shadow", " SHADOW"})
        void fromToken_shouldReturnNull_whenTokenUnknownOrBlank(String token) {
            assertNull(BanSource.fromToken(token));
        }
    }
}
