package app.l2nx.gs.adapter.api.kafka.commands.ban;

import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.GSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.JACKSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.json;
import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
class BanCommandTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z");
    private static final String REST = "'banType':'GAME_LOGIN','permanent':false,'expiresAt':'2026-12-01T00:00:00Z',"
            + "'publicReason':'bot','reason':'bot','issuedBy':'gm','initiator':" + TestInitiators.USER_JSON + "}";

    private static BanCommand.Builder valid() {
        return BanCommand.builder()
                .subjectType(WellKnownBanSubjectTypes.HWID)
                .subjectValue("a1b2c3")
                .banType("GAME_LOGIN")
                .expiresAt(EXPIRES_AT)
                .publicReason("bot")
                .issuedBy("gm")
                .initiator(TestInitiators.USER);
    }

    private static BanCommand of(String subjectType, String subjectValue, String targetType, String targetValue) {
        return new BanCommand(
                subjectType,
                subjectValue,
                targetType,
                targetValue,
                "GAME_LOGIN",
                false,
                EXPIRES_AT,
                "bot",
                null,
                "gm",
                TestInitiators.USER);
    }

    static Stream<Arguments> wireForms() {
        return Stream.of(
                Arguments.of("old form", "{'targetType':'HWID','targetValue':'a1b2c3'," + REST),
                Arguments.of("new form", "{'subjectType':'HWID','subjectValue':'a1b2c3'," + REST),
                Arguments.of(
                        "both forms",
                        "{'subjectType':'HWID','subjectValue':'a1b2c3','targetType':'HWID','targetValue':'a1b2c3',"
                                + REST),
                Arguments.of(
                        "both forms, pairs differ",
                        "{'subjectType':'HWID','subjectValue':'a1b2c3','targetType':'IP','targetValue':'10.0.0.1',"
                                + REST));
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("subjectType", (Consumer<BanCommand.Builder>) b -> b.subjectType(null)),
                Arguments.of("subjectValue", (Consumer<BanCommand.Builder>) b -> b.subjectValue(null)),
                Arguments.of("banType", (Consumer<BanCommand.Builder>) b -> b.banType(null)),
                Arguments.of("expiresAt is required", (Consumer<BanCommand.Builder>) b -> b.expiresAt(null)),
                Arguments.of("expiresAt must be null", (Consumer<BanCommand.Builder>) b -> b.permanent(true)));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommandTest#missingRequired")
        void constructor_shouldReject_whenFieldsAreMissingOrContradict(
                String message, Consumer<BanCommand.Builder> breakIt) {
            BanCommand.Builder builder = valid();
            breakIt.accept(builder);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains(message), ex.getMessage());
        }

        @Test
        void constructor_shouldAcceptEitherPairAlone() {
            assertEquals(valid().build(), of("HWID", "a1b2c3", null, null));
            assertEquals(valid().build(), of(null, null, "HWID", "a1b2c3"));
        }

        @Test
        void constructor_shouldReject_whenSubjectPairIsIncompleteAndTargetPairIsFull() {
            assertThrows(IllegalArgumentException.class, () -> of("HWID", null, "HWID", "a1b2c3"));
        }

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, BanCommand.class.getDeclaredConstructors().length);
        }
    }

    @Nested
    class Builder {

        @Test
        void build_shouldWriteBothPairs_whenSetByEitherName() {
            BanCommand byOldName =
                    valid().targetType("HARD").targetValue("268435457").build();
            BanCommand byNewName =
                    valid().subjectType("HARD").subjectValue("268435457").build();

            assertEquals(byNewName, byOldName);
            assertWritesBothPairs(byOldName);
            assertWritesBothPairs(byNewName);
        }

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            BanCommand original = valid().build();

            BanCommand copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }

        private void assertWritesBothPairs(BanCommand actual) {
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
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommandTest#wireForms")
        void gson_shouldReadSubjectThroughBothNames(String form, String wire) {
            assertReadsValid(GSON.fromJson(json(wire), BanCommand.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommandTest#wireForms")
        void jackson_shouldReadSubjectThroughBothNames(String form, String wire) throws Exception {
            assertReadsValid(JACKSON.readValue(json(wire), BanCommand.class));
        }

        @Test
        void jackson_shouldWriteBothPairs() throws Exception {
            String wire = JACKSON.writeValueAsString(valid().build());

            JsonObject actual = GSON.fromJson(wire, JsonObject.class);
            assertEquals("HWID", actual.get("subjectType").getAsString());
            assertEquals("a1b2c3", actual.get("subjectValue").getAsString());
            assertEquals("HWID", actual.get("targetType").getAsString());
            assertEquals("a1b2c3", actual.get("targetValue").getAsString());
            assertReadsValid(GSON.fromJson(wire, BanCommand.class));
        }

        @Test
        void gson_shouldNotFillSubjectValueFromTargetPair_whenSubjectTypeIsSet() {
            BanCommand actual = GSON.fromJson(
                    json("{'subjectType':'HWID','targetType':'HWID','targetValue':'0'," + REST), BanCommand.class);

            assertEquals("HWID", actual.getSubjectType());
            assertNull(actual.getSubjectValue());
            assertNull(actual.getTargetValue());
        }

        private void assertReadsValid(BanCommand actual) {
            BanCommand expected = valid().build();
            assertEquals("HWID", actual.getSubjectType());
            assertEquals("a1b2c3", actual.getSubjectValue());
            assertEquals("HWID", actual.getTargetType());
            assertEquals("a1b2c3", actual.getTargetValue());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.toString(), actual.toString());
            assertEquals(expected, actual.toBuilder().build());
        }
    }

    @Nested
    class PublicReason {

        @ParameterizedTest
        @ValueSource(strings = {"'reason':'%1$s'", "'publicReason':'%1$s'", "'publicReason':'%1$s','reason':'other'"})
        void readers_shouldReadItThroughBothNames(String field) throws Exception {
            String wire = json("{'subjectType':'HWID','subjectValue':'a1b2c3','banType':'GAME_LOGIN','permanent':true,"
                    + String.format(field, "bot") + ",'initiator':" + TestInitiators.USER_JSON + "}");

            BanCommand byGson = GSON.fromJson(wire, BanCommand.class);
            BanCommand byJackson = JACKSON.readValue(wire, BanCommand.class);

            assertEquals("bot", byGson.getPublicReason());
            assertEquals("bot", byGson.getReason());
            assertEquals(byGson, byJackson);
        }

        @Test
        void writers_shouldWriteBothNames_whenSetByEither() throws Exception {
            BanCommand byOldName = valid().reason("bot").build();
            BanCommand byNewName = valid().publicReason("bot").build();

            assertEquals(byNewName, byOldName);
            assertWritesBothNames(GSON.toJson(byOldName));
            assertWritesBothNames(JACKSON.writeValueAsString(byNewName));
        }

        private void assertWritesBothNames(String wire) {
            JsonObject actual = GSON.fromJson(wire, JsonObject.class);
            assertEquals("bot", actual.get("publicReason").getAsString());
            assertEquals("bot", actual.get("reason").getAsString());
        }
    }
}
