package app.l2nx.gs.adapter.api.kafka.commands.ban;

import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.GSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.JACKSON;
import static app.l2nx.gs.adapter.api.kafka.commands.ban.BanJson.json;
import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRef;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanSource;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnbanResultTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-17T10:15:30.123Z");
    private static final BanRef STANDARD = new BanRef(BanSource.STANDARD.name(), 11L);
    private static final BanRef SHADOW = new BanRef(BanSource.SHADOW.name(), 268435457L);
    private static final String WIRE =
            "{'unbannedBans':[{'source':'STANDARD','id':11},{'source':'SHADOW','id':268435457}],"
                    + "'observedAt':'2026-11-17T10:15:30.123Z'}";

    private static UnbanResult.Builder valid() {
        return UnbanResult.builder()
                .unbannedBans(Arrays.asList(STANDARD, SHADOW))
                .observedAt(OBSERVED_AT);
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(
                invalid("unbannedBans null", b -> b.unbannedBans(null)),
                invalid("unbannedBans hold null", b -> b.unbannedBans(Arrays.asList(STANDARD, null))),
                invalid("observedAt null", b -> b.observedAt(null)));
    }

    private static Arguments invalid(String name, UnaryOperator<UnbanResult.Builder> change) {
        return Arguments.of(name, change);
    }

    @Nested
    class Constructor {

        @Test
        void constructor_shouldAcceptEmptyUnbannedBans_whenNothingMatched() {
            UnbanResult actual =
                    valid().unbannedBans(Collections.<BanRef>emptyList()).build();

            assertEquals(Collections.<BanRef>emptyList(), actual.getUnbannedBans());
        }

        @Test
        void constructor_shouldBeTheOnlyOne() {
            assertEquals(1, UnbanResult.class.getDeclaredConstructors().length);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanResultTest#invalidArguments")
        void constructor_shouldReject_whenArgumentIsInvalid(String name, UnaryOperator<UnbanResult.Builder> change) {
            UnbanResult.Builder builder = change.apply(valid());

            assertThrows(IllegalArgumentException.class, builder::build);
        }
    }

    @Nested
    class GetUnbannedBans {

        @Test
        void getUnbannedBans_shouldBeUnmodifiableCopy() {
            List<BanRef> source = new ArrayList<BanRef>(Collections.singletonList(STANDARD));
            UnbanResult actual = valid().unbannedBans(source).build();

            source.clear();

            assertEquals(Collections.singletonList(STANDARD), actual.getUnbannedBans());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> actual.getUnbannedBans().add(SHADOW));
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldReadClearedRows() {
            assertReadsValid(GSON.fromJson(json(WIRE), UnbanResult.class));
        }

        @Test
        void jackson_shouldReadClearedRows() throws Exception {
            assertReadsValid(JACKSON.readValue(json(WIRE), UnbanResult.class));
        }

        @Test
        void jackson_shouldReadWhatGsonWrites() throws Exception {
            assertReadsValid(JACKSON.readValue(GSON.toJson(valid().build()), UnbanResult.class));
        }

        private void assertReadsValid(UnbanResult actual) {
            UnbanResult expected = valid().build();
            assertEquals(Arrays.asList(STANDARD, SHADOW), actual.getUnbannedBans());
            assertEquals(OBSERVED_AT, actual.getObservedAt());
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            UnbanResult copy = actual.toBuilder().build();
            assertEquals(expected, copy);
            assertEquals(expected.hashCode(), copy.hashCode());
            assertNotSame(actual, copy);
        }

        @Test
        void gson_shouldRoundtripAllFields() {
            UnbanResult original = valid().build();

            UnbanResult actual = GSON.fromJson(GSON.toJson(original), UnbanResult.class);

            assertEquals(original, actual);
            assertEquals(OBSERVED_AT, actual.getObservedAt());
        }

        @Test
        void gson_shouldWriteSourceTokenAndId() {
            JsonObject actual = GSON.toJsonTree(valid().build()).getAsJsonObject();

            assertEquals(OBSERVED_AT.toString(), actual.get("observedAt").getAsString());
            assertFalse(actual.has("removedBans"));
            JsonObject shadow = actual.getAsJsonArray("unbannedBans").get(1).getAsJsonObject();
            assertEquals("SHADOW", shadow.get("source").getAsString());
            assertEquals(268435457L, shadow.get("id").getAsLong());
        }
    }
}
