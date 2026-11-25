package app.l2nx.gs.adapter.api.kafka.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CommandInitiatorTest {

    private static final UUID USER_ID = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");

    private static CommandInitiator.Builder user() {
        return CommandInitiator.builder().source("user").userId(USER_ID);
    }

    private static CommandInitiator.Builder service() {
        return CommandInitiator.builder().source("nx-gameservers");
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Nested
    class Normalize {

        @Test
        void build_shouldTrimAndLowercaseSourceAndEmail() {
            CommandInitiator actual = CommandInitiator.builder()
                    .source("  USER ")
                    .userId(USER_ID)
                    .email(" Player@Example.COM ")
                    .label(" Daily Job ")
                    .ip(" 203.0.113.7 ")
                    .build();

            assertEquals("user", actual.getSource());
            assertEquals("player@example.com", actual.getEmail());
            assertEquals("Daily Job", actual.getLabel());
            assertEquals("203.0.113.7", actual.getIp());
            assertTrue(actual.isUser());
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void build_shouldTurnBlankOptionalsIntoNull(String blank) {
            CommandInitiator actual =
                    service().label(blank).email(blank).ip(blank).build();

            assertNull(actual.getLabel());
            assertNull(actual.getEmail());
            assertNull(actual.getIp());
            assertFalse(actual.isUser());
        }

        @Test
        void build_shouldAcceptValuesAtTheirLimits() {
            CommandInitiator actual = CommandInitiator.builder()
                    .source(repeat('s', CommandInitiator.SOURCE_MAX))
                    .label(repeat('l', CommandInitiator.LABEL_MAX))
                    .email(repeat('e', CommandInitiator.EMAIL_MAX))
                    .ip(repeat('1', CommandInitiator.IP_MAX))
                    .build();

            assertEquals(CommandInitiator.SOURCE_MAX, actual.getSource().length());
        }
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void build_shouldRejectBlankSource(String blank) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service().source(blank).build());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.CommandInitiatorTest#invalidValues")
        void build_shouldRejectInvalidValue(String name, UnaryOperator<CommandInitiator.Builder> change) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> change.apply(service()).build());
        }
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("user source without userId", (UnaryOperator<CommandInitiator.Builder>)
                        b -> user().userId(null)),
                Arguments.of(
                        "service source with userId", (UnaryOperator<CommandInitiator.Builder>) b -> b.userId(USER_ID)),
                Arguments.of("source over limit", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.source(repeat('s', CommandInitiator.SOURCE_MAX + 1))),
                Arguments.of("label over limit", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.label(repeat('l', CommandInitiator.LABEL_MAX + 1))),
                Arguments.of("email over limit", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.email(repeat('e', CommandInitiator.EMAIL_MAX + 1))),
                Arguments.of("ip over limit", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.ip(repeat('1', CommandInitiator.IP_MAX + 1))));
    }

    @Nested
    class Value {

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(CommandInitiator.class)
                    .withNonnullFields("source")
                    .verify();
        }

        @Test
        void gson_shouldRoundtripAndOmitAbsentValues() {
            Gson gson = new Gson();
            CommandInitiator full =
                    user().label("job").email("a@b.c").ip("203.0.113.7").build();

            assertEquals(full, full.toBuilder().build());
            assertEquals(full.hashCode(), full.toBuilder().build().hashCode());
            assertEquals(TestInitiators.USER_JSON, gson.toJson(TestInitiators.USER));
            assertEquals(TestInitiators.USER, gson.fromJson(TestInitiators.USER_JSON, CommandInitiator.class));
            assertEquals(USER_ID, full.getUserId());
        }
    }
}
