package app.l2nx.gs.adapter.api.kafka.sync.db.ban;

import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.GSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.JACKSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.json;
import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import com.google.gson.JsonObject;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BanDbDtoTest {

    private static final String WIRE =
            "{'id':11,'subjectType':'CHARACTER','subjectValue':'268435457','subjectName':'Hero','banType':'CHAT',"
                    + "'expiresAt':'2026-12-01T00:00:00Z','publicReason':'spam','issuedBy':'gm'}";

    private static BanDbDto.Builder valid() {
        return BanDbDto.builder()
                .id(11L)
                .subjectType(WellKnownBanSubjectTypes.CHARACTER)
                .subjectValue("268435457")
                .subjectName("Hero")
                .banType("CHAT")
                .expiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .publicReason("spam")
                .issuedBy("gm");
    }

    @Nested
    class Constructor {

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, BanDbDto.class.getDeclaredConstructors().length);
        }
    }

    @Nested
    class Builder {

        @Test
        void build_shouldWriteSubjectAndPublicReasonOnly() {
            JsonObject wire = GSON.toJsonTree(valid().build()).getAsJsonObject();

            assertEquals("CHARACTER", wire.get("subjectType").getAsString());
            assertEquals("268435457", wire.get("subjectValue").getAsString());
            assertEquals("Hero", wire.get("subjectName").getAsString());
            assertEquals("spam", wire.get("publicReason").getAsString());
            for (String former : new String[] {"targetType", "targetValue", "targetName", "reason"}) {
                assertFalse(wire.has(former), former);
            }
        }

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            BanDbDto original = valid().build();

            BanDbDto copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldReadAllFields() {
            assertReadsValid(GSON.fromJson(json(WIRE), BanDbDto.class));
        }

        @Test
        void jackson_shouldReadAllFields() throws Exception {
            assertReadsValid(JACKSON.readValue(json(WIRE), BanDbDto.class));
        }

        @Test
        void jackson_shouldReadWhatGsonWrites() throws Exception {
            assertReadsValid(JACKSON.readValue(GSON.toJson(valid().build()), BanDbDto.class));
        }

        @Test
        void readers_shouldIgnoreFormerNames_whenAnOlderHostWritesBoth() {
            String wire = WIRE.substring(0, WIRE.length() - 1)
                    + ",'targetType':'IP','targetValue':'10.0.0.1','targetName':'pc','reason':'other'}";

            assertReadsValid(GSON.fromJson(json(wire), BanDbDto.class));
        }

        private void assertReadsValid(BanDbDto actual) {
            BanDbDto expected = valid().build();
            assertEquals("CHARACTER", actual.getSubjectType());
            assertEquals("268435457", actual.getSubjectValue());
            assertEquals("Hero", actual.getSubjectName());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.toString(), actual.toString());
            assertEquals(expected, actual.toBuilder().build());
        }
    }
}
