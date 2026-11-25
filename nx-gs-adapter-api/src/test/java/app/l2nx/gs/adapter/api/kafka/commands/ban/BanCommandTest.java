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

class BanCommandTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z");
    private static final String WIRE =
            "{'subjectType':'HWID','subjectValue':'a1b2c3','banType':'GAME_LOGIN','permanent':false,'expiresAt':'2026-12-01T00:00:00Z',"
                    + "'publicReason':'bot','issuedBy':'gm','initiator':" + TestInitiators.USER_JSON + "}";

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
        void constructor_shouldAcceptPermanentBan_whenNoExpiry() {
            BanCommand actual = valid().expiresAt(null).permanent(true).build();

            assertTrue(actual.isPermanent());
            assertNull(actual.getExpiresAt());
        }

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, BanCommand.class.getDeclaredConstructors().length);
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldReadAllFields() {
            assertReadsValid(GSON.fromJson(json(WIRE), BanCommand.class));
        }

        @Test
        void jackson_shouldReadAllFields() throws Exception {
            assertReadsValid(JACKSON.readValue(json(WIRE), BanCommand.class));
        }

        @Test
        void jackson_shouldWriteSubjectOnly() throws Exception {
            String wire = JACKSON.writeValueAsString(valid().build());

            JsonObject actual = GSON.fromJson(wire, JsonObject.class);
            assertEquals("HWID", actual.get("subjectType").getAsString());
            assertEquals("a1b2c3", actual.get("subjectValue").getAsString());
            assertFalse(actual.has("targetType"));
            assertFalse(actual.has("targetValue"));
            assertEquals("bot", actual.get("publicReason").getAsString());
            assertFalse(actual.has("reason"));
            assertReadsValid(GSON.fromJson(wire, BanCommand.class));
        }

        private void assertReadsValid(BanCommand actual) {
            BanCommand expected = valid().build();
            assertEquals("HWID", actual.getSubjectType());
            assertEquals("a1b2c3", actual.getSubjectValue());
            assertEquals("GAME_LOGIN", actual.getBanType());
            assertEquals(EXPIRES_AT, actual.getExpiresAt());
            assertEquals("bot", actual.getPublicReason());
            assertEquals("gm", actual.getIssuedBy());
            assertEquals(TestInitiators.USER, actual.getInitiator());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.toString(), actual.toString());
            BanCommand copy = actual.toBuilder().build();
            assertEquals(expected, copy);
            assertEquals(expected.hashCode(), copy.hashCode());
            assertNotSame(actual, copy);
        }
    }
}
