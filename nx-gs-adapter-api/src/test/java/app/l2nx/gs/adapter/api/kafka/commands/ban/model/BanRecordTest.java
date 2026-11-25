package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BanRecordTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static BanDbDto row() {
        return BanDbDto.builder()
                .id(11L)
                .subjectType("CHARACTER")
                .subjectValue("268435457")
                .subjectName("Hero")
                .banType("CHAT")
                .expiresAt(EXPIRES_AT)
                .publicReason("spam")
                .issuedBy("gm")
                .build();
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of(null, row()),
                Arguments.of("", row()),
                Arguments.of(" ", row()),
                Arguments.of("SHADOW", null));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "source=[{0}], row={1}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRecordTest#invalidArguments")
        void constructor_shouldReject_whenSourceMissingOrBlankOrRowNull(String source, BanDbDto row) {
            BanRecord.Builder builder = BanRecord.builder().source(source).row(row);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldKeepToken_whenSourceUnknownToThisApi() {
            BanRecord actual = new BanRecord("SOMETHING_NEW", row());

            assertEquals("SOMETHING_NEW", actual.getSource());
            assertNull(BanSource.fromToken(actual.getSource()));
        }
    }

    @Nested
    class Builder {

        @Test
        void source_shouldWriteConstantName_whenGivenAsEnum() {
            BanRecord actual =
                    BanRecord.builder().source(BanSource.SHADOW).row(row()).build();

            assertEquals("SHADOW", actual.getSource());
            assertEquals(BanSource.SHADOW, BanSource.fromToken(actual.getSource()));
        }
    }

    @Nested
    class ValueSemantics {

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(BanRecord.class).verify();
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldRoundtripAllFields() {
            BanRecord original =
                    BanRecord.builder().source(BanSource.STANDARD).row(row()).build();

            BanRecord actual = GSON.fromJson(GSON.toJson(original), BanRecord.class);

            assertEquals(original, actual);
            assertEquals(EXPIRES_AT, actual.getRow().getExpiresAt());

            BanRecord copy = original.toBuilder().build();
            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }

        @Test
        void gson_shouldKeepToken_whenSourceUnknownToThisApi() {
            BanRecord actual = GSON.fromJson("{\"source\":\"SOMETHING_NEW\",\"row\":{\"id\":11}}", BanRecord.class);

            assertEquals("SOMETHING_NEW", actual.getSource());
            assertNull(BanSource.fromToken(actual.getSource()));
            assertEquals(11L, actual.getRow().getId());
        }
    }
}
