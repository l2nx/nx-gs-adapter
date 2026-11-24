package app.l2nx.gs.adapter.api.kafka.sync.db.ban;

import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.GSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.JACKSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.json;
import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation")
class BanDbDtoTest {

    private static final String REST =
            "'banType':'CHAT','expiresAt':'2026-12-01T00:00:00Z','reason':'spam','issuedBy':'gm'}";
    private static final String SUBJECT = "'subjectType':'CHARACTER','subjectValue':'268435457','subjectName':'Hero',";
    private static final String TARGET = "'targetType':'CHARACTER','targetValue':'268435457','targetName':'Hero',";

    private static BanDbDto.Builder valid() {
        return BanDbDto.builder()
                .id(11L)
                .subjectType(WellKnownBanSubjectTypes.CHARACTER)
                .subjectValue("268435457")
                .subjectName("Hero")
                .banType("CHAT")
                .expiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .reason("spam")
                .issuedBy("gm");
    }

    static Stream<Arguments> wireForms() {
        return Stream.of(
                Arguments.of("old form", "{'id':11," + TARGET + REST),
                Arguments.of("new form", "{'id':11," + SUBJECT + REST),
                Arguments.of("both forms", "{'id':11," + SUBJECT + TARGET + REST),
                Arguments.of(
                        "both forms, pairs differ",
                        "{'id':11," + SUBJECT + "'targetType':'IP','targetValue':'10.0.0.1','targetName':'pc',"
                                + REST));
    }

    static Stream<Arguments> formsWithoutType() {
        return Stream.of(
                Arguments.of("old form", "{'id':11,'targetValue':'268435457','targetName':'Hero'}"),
                Arguments.of("new form", "{'id':11,'subjectValue':'268435457','subjectName':'Hero'}"));
    }

    @Nested
    class Constructor {

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, BanDbDto.class.getDeclaredConstructors().length);
        }

        @Test
        void constructor_shouldWriteBothPairs_whenGivenEither() {
            BanDbDto bySubject = new BanDbDto(1L, "IP", "10.0.0.1", "pc", null, null, null, null, null, null, null);
            BanDbDto byTarget = new BanDbDto(1L, null, null, null, "IP", "10.0.0.1", "pc", null, null, null, null);

            assertEquals(GSON.toJsonTree(bySubject), GSON.toJsonTree(byTarget));
            assertEquals(
                    "IP",
                    GSON.toJsonTree(bySubject)
                            .getAsJsonObject()
                            .get("targetType")
                            .getAsString());
        }
    }

    @Nested
    class Builder {

        @Test
        void build_shouldWriteBothPairs_whenSetByEitherName() {
            BanDbDto byOldName = valid().subjectType(null)
                    .subjectValue(null)
                    .subjectName(null)
                    .targetType("CHARACTER")
                    .targetValue("268435457")
                    .targetName("Hero")
                    .build();

            assertEquals(valid().build(), byOldName);
            assertWritesPair("subject", byOldName);
            assertWritesPair("target", byOldName);
            assertWritesPair("subject", valid().build());
            assertWritesPair("target", valid().build());
        }

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            BanDbDto original = valid().build();

            BanDbDto copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }

        private void assertWritesPair(String prefix, BanDbDto actual) {
            JsonObject wire = GSON.toJsonTree(actual).getAsJsonObject();
            assertEquals("CHARACTER", wire.get(prefix + "Type").getAsString());
            assertEquals("268435457", wire.get(prefix + "Value").getAsString());
            assertEquals("Hero", wire.get(prefix + "Name").getAsString());
        }
    }

    @Nested
    class Json {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDtoTest#wireForms")
        void gson_shouldReadSubjectThroughBothNames(String form, String wire) {
            assertReadsValid(GSON.fromJson(json(wire), BanDbDto.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDtoTest#wireForms")
        void jackson_shouldReadSubjectThroughBothNames(String form, String wire) throws Exception {
            assertReadsValid(JACKSON.readValue(json(wire), BanDbDto.class));
        }

        @Test
        void jackson_shouldReadWhatGsonWrites() throws Exception {
            assertReadsValid(JACKSON.readValue(GSON.toJson(valid().build()), BanDbDto.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDtoTest#formsWithoutType")
        void readers_shouldKeepValueAndName_whenTypeIsAbsent(String form, String wire) throws Exception {
            BanDbDto byGson = GSON.fromJson(json(wire), BanDbDto.class);
            BanDbDto byJackson = JACKSON.readValue(json(wire), BanDbDto.class);

            assertEquals("268435457", byGson.getSubjectValue());
            assertEquals("Hero", byGson.getSubjectName());
            assertEquals("268435457", byGson.getTargetValue());
            assertEquals("Hero", byGson.getTargetName());
            assertEquals(byGson, byJackson);
        }

        private void assertReadsValid(BanDbDto actual) {
            BanDbDto expected = valid().build();
            assertEquals("CHARACTER", actual.getSubjectType());
            assertEquals("268435457", actual.getSubjectValue());
            assertEquals("Hero", actual.getSubjectName());
            assertEquals("CHARACTER", actual.getTargetType());
            assertEquals("268435457", actual.getTargetValue());
            assertEquals("Hero", actual.getTargetName());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.toString(), actual.toString());
            assertEquals(expected, actual.toBuilder().build());
        }
    }

    @Nested
    class NulledPlaceholder {

        @Test
        void readers_shouldNotBringTheValueBack_whenOnlySubjectPairIsNulled() throws Exception {
            String wire =
                    json("{'id':11,'subjectType':'HWID','targetType':'HWID','targetValue':'0','targetName':'pc'}");

            BanDbDto byGson = GSON.fromJson(wire, BanDbDto.class);
            BanDbDto byJackson = JACKSON.readValue(wire, BanDbDto.class);

            assertEquals("HWID", byGson.getSubjectType());
            assertEquals("HWID", byGson.getTargetType());
            assertNull(byGson.getSubjectValue());
            assertNull(byGson.getSubjectName());
            assertNull(byGson.getTargetValue());
            assertNull(byGson.getTargetName());
            assertEquals(byGson, byJackson);
        }

        @Test
        void targetValue_shouldNullBothPairs_whenAnOlderDbSyncCoreNormalizes() {
            BanDbDto actual =
                    BanDbDto.builder()
                            .id(11L)
                            .subjectType(WellKnownBanSubjectTypes.HWID)
                            .subjectValue("0")
                            .build()
                            .toBuilder()
                            .targetValue(null)
                            .build();

            JsonObject wire = GSON.toJsonTree(actual).getAsJsonObject();
            assertNull(actual.getSubjectValue());
            assertFalse(wire.has("subjectValue"));
            assertFalse(wire.has("targetValue"));
            assertEquals("HWID", wire.get("targetType").getAsString());
        }
    }
}
