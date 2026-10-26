package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
                .charId(1)
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
    void builder_shouldKeepAllFields() {
        StartPrivateStoreBuyCommand actual = valid().build();

        assertEquals(1, actual.getCharId());
        assertEquals("WTB", actual.getTitle());
        assertEquals(Arrays.asList(LINE_1, LINE_2), actual.getLines());
        assertEquals(Integer.valueOf(5), actual.getBuyerFeePercent());
        assertEquals(Boolean.TRUE, actual.getOfflineAllowed());
        assertTrue(actual.isOwnerVerified());
        assertEquals(DEADLINE, actual.getDeadline());
        assertEquals(KEY, actual.getIdempotencyKey());
        assertEquals(TestInitiators.USER, actual.getInitiator());
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

    @Test
    void constructor_shouldReject_whenLinesNull() {
        StartPrivateStoreBuyCommand.Builder builder = valid().lines(null);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesEmpty() {
        StartPrivateStoreBuyCommand.Builder builder = valid().lines(Collections.<WantedLine>emptyList());
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesContainNull() {
        StartPrivateStoreBuyCommand.Builder builder = valid().lines(Arrays.asList(LINE_1, null));
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenTemplateRepeats() {
        StartPrivateStoreBuyCommand.Builder builder = valid().lines(Arrays.asList(LINE_1, new WantedLine(57L, 2L, 1L)));
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenIdempotencyKeyNull() {
        StartPrivateStoreBuyCommand.Builder builder = valid().idempotencyKey(null);
        assertThrows(NullPointerException.class, builder::build);
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
    void toBuilder_shouldRoundtrip() {
        StartPrivateStoreBuyCommand original = valid().build();

        assertEquals(original, original.toBuilder().build());
        assertEquals(original.hashCode(), original.toBuilder().build().hashCode());
    }

    @Test
    void equals_shouldDistinguishLinesAndBuyerFee() {
        StartPrivateStoreBuyCommand base = valid().build();

        assertNotEquals(base, valid().lines(Collections.singletonList(LINE_1)).build());
        assertNotEquals(base, valid().buyerFeePercent(6).build());
        assertNotEquals(base, valid().idempotencyKey(UUID.randomUUID()).build());
    }

    @Test
    void toString_shouldNameTheStoreFields() {
        String actual = valid().build().toString();

        assertTrue(actual.startsWith("StartPrivateStoreBuyCommand[charId=1, title=WTB, lines=["), actual);
        assertTrue(actual.contains("buyerFeePercent=5, offlineAllowed=true"), actual);
    }

    @Test
    void gson_shouldRoundtripAndUseTheWireFieldNames() {
        StartPrivateStoreBuyCommand original = valid().build();
        String expected = "{\"charId\":1,\"title\":\"WTB\","
                + "\"lines\":[{\"itemTemplateId\":57,\"count\":10,\"priceAdena\":100},"
                + "{\"itemTemplateId\":58,\"count\":1,\"priceAdena\":5000}],\"buyerFeePercent\":5,"
                + "\"offlineAllowed\":true,\"ownerVerified\":true,\"deadline\":\"2026-10-22T12:00:00Z\","
                + "\"idempotencyKey\":\"5b1f3c2e-0000-4000-8000-000000000001\",\"initiator\":"
                + TestInitiators.USER_JSON + "}";

        assertEquals(JsonParser.parseString(expected), GSON.toJsonTree(original));
        assertEquals(original, GSON.fromJson(GSON.toJson(original), StartPrivateStoreBuyCommand.class));
    }
}
