package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.WantedLine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StartPrivateStoreBuyCommandTest {

    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final Instant DEADLINE = Instant.parse("2026-10-22T12:00:00Z");
    private static final WantedLine LINE_1 = new WantedLine(57L, 10L, 100L);
    private static final WantedLine LINE_2 = new WantedLine(58L, 1L, 5_000L);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static StartPrivateStoreBuyCommand.Builder valid() {
        return StartPrivateStoreBuyCommand.builder()
                .characterId(1)
                .title("WTB")
                .lines(Arrays.asList(LINE_1, LINE_2))
                .buyerFeePercent(5)
                .offlineAllowed(true)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY)
                .initiator(TestInitiators.USER);
    }

    @Test
    void getBuyerFeePercent_shouldBeNull_whenAbsent() {
        assertNull(valid().buyerFeePercent(null).build().getBuyerFeePercent());
    }

    @Test
    void constructor_shouldKeepBuyerFeePercent_whenAboveFiftyBecausePlatformDecidesTheRate() {
        assertEquals(Integer.valueOf(75), valid().buyerFeePercent(75).build().getBuyerFeePercent());
    }

    @ParameterizedTest(name = "buyerFeePercent={0}")
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void constructor_shouldReject_whenBuyerFeePercentNegative(int buyerFeePercent) {
        StartPrivateStoreBuyCommand.Builder builder = valid().buyerFeePercent(buyerFeePercent);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of("lines null", valid().lines(null), IllegalArgumentException.class),
                Arguments.of(
                        "lines empty",
                        valid().lines(Collections.<WantedLine>emptyList()),
                        IllegalArgumentException.class),
                Arguments.of(
                        "lines contain null",
                        valid().lines(Arrays.asList(LINE_1, null)),
                        IllegalArgumentException.class),
                Arguments.of(
                        "template repeats",
                        valid().lines(Arrays.asList(LINE_1, new WantedLine(57L, 2L, 1L))),
                        IllegalArgumentException.class),
                Arguments.of("idempotencyKey null", valid().idempotencyKey(null), NullPointerException.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    void constructor_shouldReject_whenCommandInvalid(
            String name, StartPrivateStoreBuyCommand.Builder builder, Class<? extends RuntimeException> expected) {
        assertThrows(expected, builder::build);
    }

    @Test
    void getLines_shouldBeAnUnmodifiableCopy() {
        List<WantedLine> src = new ArrayList<>(Collections.singletonList(LINE_1));
        StartPrivateStoreBuyCommand actual = valid().lines(src).build();
        src.add(LINE_2);

        assertEquals(Collections.singletonList(LINE_1), actual.getLines());
        assertThrows(
                UnsupportedOperationException.class, () -> actual.getLines().add(LINE_2));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(StartPrivateStoreBuyCommand.class)
                .usingGetClass()
                .verify();
    }

    @Test
    void roundtrip_shouldKeepAllFieldsAndUseTheWireFieldNames() {
        StartPrivateStoreBuyCommand original = valid().build();
        StartPrivateStoreBuyCommand copy = original.toBuilder().build();
        String expected = "{\"characterId\":1,\"title\":\"WTB\","
                + "\"lines\":[{\"itemTemplateId\":57,\"count\":10,\"priceAdena\":100},"
                + "{\"itemTemplateId\":58,\"count\":1,\"priceAdena\":5000}],\"buyerFeePercent\":5,"
                + "\"offlineAllowed\":true,\"ownerVerified\":true,\"deadline\":\"2026-10-22T12:00:00Z\","
                + "\"idempotencyKey\":\"5b1f3c2e-0000-4000-8000-000000000001\",\"initiator\":"
                + TestInitiators.USER_JSON + "}";

        assertEquals(1, original.getCharacterId());
        assertEquals("WTB", original.getTitle());
        assertEquals(Arrays.asList(LINE_1, LINE_2), original.getLines());
        assertEquals(Integer.valueOf(5), original.getBuyerFeePercent());
        assertEquals(Boolean.TRUE, original.getOfflineAllowed());
        assertTrue(original.isOwnerVerified());
        assertEquals(DEADLINE, original.getDeadline());
        assertEquals(KEY, original.getIdempotencyKey());
        assertEquals(TestInitiators.USER, original.getInitiator());
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(JsonParser.parseString(expected), GSON.toJsonTree(original));
        assertEquals(original, GSON.fromJson(GSON.toJson(original), StartPrivateStoreBuyCommand.class));
    }
}
