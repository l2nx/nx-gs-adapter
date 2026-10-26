package app.l2nx.gs.adapter.api.kafka.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
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

        @Test
        void build_shouldRejectUserSourceWithoutUserId() {
            assertThrows(
                    IllegalArgumentException.class, () -> user().userId(null).build());
        }

        @Test
        void build_shouldRejectServiceSourceWithUserId() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service().userId(USER_ID).build());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.CommandInitiatorTest#overLimit")
        void build_shouldRejectValueOverItsLimit(String field, UnaryOperator<CommandInitiator.Builder> change) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> change.apply(service()).build());
        }
    }

    static Stream<Arguments> overLimit() {
        return Stream.of(
                Arguments.of("source", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.source(repeat('s', CommandInitiator.SOURCE_MAX + 1))),
                Arguments.of("label", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.label(repeat('l', CommandInitiator.LABEL_MAX + 1))),
                Arguments.of("email", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.email(repeat('e', CommandInitiator.EMAIL_MAX + 1))),
                Arguments.of("ip", (UnaryOperator<CommandInitiator.Builder>)
                        b -> b.ip(repeat('1', CommandInitiator.IP_MAX + 1))));
    }

    @Nested
    class Value {

        @Test
        void toBuilder_shouldRoundtrip() {
            CommandInitiator original = user().email("a@b.c").ip("203.0.113.7").build();

            assertEquals(original, original.toBuilder().build());
            assertEquals(original.hashCode(), original.toBuilder().build().hashCode());
        }

        @Test
        void equals_shouldDistinguishEveryField() {
            CommandInitiator base =
                    service().label("job").email("a@b.c").ip("203.0.113.7").build();

            assertNotEquals(base, base.toBuilder().source("nx-social").build());
            assertNotEquals(base, base.toBuilder().label("other").build());
            assertNotEquals(base, base.toBuilder().email("x@b.c").build());
            assertNotEquals(base, base.toBuilder().ip("203.0.113.8").build());
            assertNotEquals(user().build(), user().userId(UUID.randomUUID()).build());
        }

        @Test
        void gson_shouldRoundtripAndOmitAbsentValues() {
            Gson gson = new Gson();

            assertEquals(TestInitiators.USER_JSON, gson.toJson(TestInitiators.USER));
            assertEquals(TestInitiators.USER, gson.fromJson(TestInitiators.USER_JSON, CommandInitiator.class));
        }
    }
}
