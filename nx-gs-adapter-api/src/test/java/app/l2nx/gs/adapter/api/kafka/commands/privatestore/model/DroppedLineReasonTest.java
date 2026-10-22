package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DroppedLineReasonTest {

    @Nested
    class FromToken {

        @ParameterizedTest(name = "{0}")
        @EnumSource(DroppedLineReason.class)
        void fromToken_shouldResolveConstant_whenTokenIsItsName(DroppedLineReason expected) {
            assertEquals(expected, DroppedLineReason.fromToken(expected.name()));
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", " ", "SOMETHING_NEW", "not_found", " NOT_FOUND"})
        void fromToken_shouldReturnNull_whenTokenUnknownOrBlank(String token) {
            assertNull(DroppedLineReason.fromToken(token));
        }
    }
}
