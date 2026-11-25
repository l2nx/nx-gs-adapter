package app.l2nx.gs.adapter.api.kafka.commands.ban;

import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.GSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.JACKSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.json;
import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnbanCommandTest {

    private static final String WIRE = "{'subjectType':'ACCOUNT','subjectValue':'login1','banType':'CHAT','initiator':"
            + TestInitiators.USER_JSON + "}";

    private static UnbanCommand.Builder valid() {
        return UnbanCommand.builder()
                .subjectType(WellKnownBanSubjectTypes.ACCOUNT)
                .subjectValue("login1")
                .banType("CHAT")
                .initiator(TestInitiators.USER);
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("subjectType", (Consumer<UnbanCommand.Builder>) b -> b.subjectType(null)),
                Arguments.of("subjectValue", (Consumer<UnbanCommand.Builder>) b -> b.subjectValue(null)),
                Arguments.of("banType", (Consumer<UnbanCommand.Builder>) b -> b.banType(null)));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanCommandTest#missingRequired")
        void constructor_shouldReject_whenRequiredFieldNull(String field, Consumer<UnbanCommand.Builder> nullOut) {
            UnbanCommand.Builder builder = valid();
            nullOut.accept(builder);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains(field), ex.getMessage());
        }

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, UnbanCommand.class.getDeclaredConstructors().length);
        }
    }

    @Nested
    class Builder {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            UnbanCommand original = valid().build();

            UnbanCommand copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldReadAllFields() {
            assertReadsValid(GSON.fromJson(json(WIRE), UnbanCommand.class));
        }

        @Test
        void jackson_shouldReadAllFields() throws Exception {
            assertReadsValid(JACKSON.readValue(json(WIRE), UnbanCommand.class));
        }

        @Test
        void jackson_shouldWriteSubjectOnly() throws Exception {
            String wire = JACKSON.writeValueAsString(valid().build());

            JsonObject actual = GSON.fromJson(wire, JsonObject.class);
            assertEquals("ACCOUNT", actual.get("subjectType").getAsString());
            assertEquals("login1", actual.get("subjectValue").getAsString());
            assertFalse(actual.has("targetType"));
            assertFalse(actual.has("targetValue"));
            assertReadsValid(GSON.fromJson(wire, UnbanCommand.class));
        }

        private void assertReadsValid(UnbanCommand actual) {
            UnbanCommand expected = valid().build();
            assertEquals("ACCOUNT", actual.getSubjectType());
            assertEquals("login1", actual.getSubjectValue());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.toString(), actual.toString());
            assertEquals(expected, actual.toBuilder().build());
        }
    }
}
