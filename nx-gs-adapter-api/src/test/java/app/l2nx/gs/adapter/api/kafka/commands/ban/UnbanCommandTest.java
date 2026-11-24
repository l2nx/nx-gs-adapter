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

@SuppressWarnings("deprecation")
class UnbanCommandTest {

    private static final String REST = "'banType':'CHAT','initiator':" + TestInitiators.USER_JSON + "}";

    private static UnbanCommand.Builder valid() {
        return UnbanCommand.builder()
                .subjectType(WellKnownBanSubjectTypes.ACCOUNT)
                .subjectValue("login1")
                .banType("CHAT")
                .initiator(TestInitiators.USER);
    }

    private static UnbanCommand of(String subjectType, String subjectValue, String targetType, String targetValue) {
        return new UnbanCommand(subjectType, subjectValue, targetType, targetValue, "CHAT", TestInitiators.USER);
    }

    static Stream<Arguments> wireForms() {
        return Stream.of(
                Arguments.of("old form", "{'targetType':'ACCOUNT','targetValue':'login1'," + REST),
                Arguments.of("new form", "{'subjectType':'ACCOUNT','subjectValue':'login1'," + REST),
                Arguments.of(
                        "both forms",
                        "{'subjectType':'ACCOUNT','subjectValue':'login1','targetType':'ACCOUNT','targetValue':'login1',"
                                + REST),
                Arguments.of(
                        "both forms, pairs differ",
                        "{'subjectType':'ACCOUNT','subjectValue':'login1','targetType':'IP','targetValue':'10.0.0.1',"
                                + REST));
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
        void constructor_shouldAcceptEitherPairAlone() {
            assertEquals(valid().build(), of("ACCOUNT", "login1", null, null));
            assertEquals(valid().build(), of(null, null, "ACCOUNT", "login1"));
        }

        @Test
        void constructor_shouldReject_whenSubjectPairIsIncompleteAndTargetPairIsFull() {
            assertThrows(IllegalArgumentException.class, () -> of("ACCOUNT", null, "ACCOUNT", "login1"));
        }

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, UnbanCommand.class.getDeclaredConstructors().length);
        }
    }

    @Nested
    class Builder {

        @Test
        void build_shouldWriteBothPairs_whenSetByEitherName() {
            UnbanCommand byOldName =
                    valid().targetType("HARD").targetValue("268435457").build();
            UnbanCommand byNewName =
                    valid().subjectType("HARD").subjectValue("268435457").build();

            assertEquals(byNewName, byOldName);
            assertWritesBothPairs(byOldName);
            assertWritesBothPairs(byNewName);
        }

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            UnbanCommand original = valid().build();

            UnbanCommand copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }

        private void assertWritesBothPairs(UnbanCommand actual) {
            JsonObject wire = GSON.toJsonTree(actual).getAsJsonObject();
            assertEquals("HARD", wire.get("subjectType").getAsString());
            assertEquals("268435457", wire.get("subjectValue").getAsString());
            assertEquals("HARD", wire.get("targetType").getAsString());
            assertEquals("268435457", wire.get("targetValue").getAsString());
        }
    }

    @Nested
    class Json {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanCommandTest#wireForms")
        void gson_shouldReadSubjectThroughBothNames(String form, String wire) {
            assertReadsValid(GSON.fromJson(json(wire), UnbanCommand.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanCommandTest#wireForms")
        void jackson_shouldReadSubjectThroughBothNames(String form, String wire) throws Exception {
            assertReadsValid(JACKSON.readValue(json(wire), UnbanCommand.class));
        }

        @Test
        void jackson_shouldWriteBothPairs() throws Exception {
            String wire = JACKSON.writeValueAsString(valid().build());

            JsonObject actual = GSON.fromJson(wire, JsonObject.class);
            assertEquals("ACCOUNT", actual.get("subjectType").getAsString());
            assertEquals("login1", actual.get("subjectValue").getAsString());
            assertEquals("ACCOUNT", actual.get("targetType").getAsString());
            assertEquals("login1", actual.get("targetValue").getAsString());
            assertReadsValid(GSON.fromJson(wire, UnbanCommand.class));
        }

        @Test
        void gson_shouldNotFillSubjectValueFromTargetPair_whenSubjectTypeIsSet() {
            UnbanCommand actual = GSON.fromJson(
                    json("{'subjectType':'HWID','targetType':'HWID','targetValue':'0'," + REST), UnbanCommand.class);

            assertEquals("HWID", actual.getSubjectType());
            assertNull(actual.getSubjectValue());
            assertNull(actual.getTargetValue());
        }

        private void assertReadsValid(UnbanCommand actual) {
            UnbanCommand expected = valid().build();
            assertEquals("ACCOUNT", actual.getSubjectType());
            assertEquals("login1", actual.getSubjectValue());
            assertEquals("ACCOUNT", actual.getTargetType());
            assertEquals("login1", actual.getTargetValue());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.toString(), actual.toString());
            assertEquals(expected, actual.toBuilder().build());
        }
    }
}
