package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StartPrivateStoreCommandsTest {

    private static final SellLine LINE_1 = new SellLine(1, 1L, 100L);
    private static final SellLine LINE_2 = new SellLine(2, 1L, 100L);
    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final Instant DEADLINE = Instant.parse("2026-08-11T12:00:00Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    static Stream<Arguments> constructors() {
        Function<List<SellLine>, Object> sell = lines ->
                new StartPrivateStoreSellCommand(1, null, lines, null, null, false, null, KEY, TestInitiators.USER);
        Function<List<SellLine>, Object> packageSell = lines -> new StartPrivateStorePackageSellCommand(
                1, null, lines, null, null, false, null, KEY, TestInitiators.USER);
        return Stream.of(Arguments.of("sell", sell), Arguments.of("package-sell", packageSell));
    }

    @Nested
    class SellerFeePercent {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreCommandsTest#constructors")
        void getSellerFeePercent_shouldBeNull_whenAbsent(String name, Function<List<SellLine>, Object> constructor) {
            StartPrivateStoreSellingCommand actual =
                    (StartPrivateStoreSellingCommand) constructor.apply(Collections.singletonList(LINE_1));

            assertNull(actual.getSellerFeePercent());
        }

        @Test
        void constructor_shouldKeepSellerFeePercent_whenAboveFifty() {
            StartPrivateStoreSellCommand actual = new StartPrivateStoreSellCommand(
                    1, null, Collections.singletonList(LINE_1), 75, null, false, null, KEY, TestInitiators.USER);

            assertEquals(Integer.valueOf(75), actual.getSellerFeePercent());
        }

        @Test
        void constructor_shouldKeepSellerFeePercent_whenHundred() {
            StartPrivateStoreSellCommand actual = new StartPrivateStoreSellCommand(
                    1, null, Collections.singletonList(LINE_1), 100, null, false, null, KEY, TestInitiators.USER);

            assertEquals(Integer.valueOf(100), actual.getSellerFeePercent());
        }

        @ParameterizedTest(name = "sellerFeePercent={0}")
        @ValueSource(ints = {-1, Integer.MIN_VALUE, 101, Integer.MAX_VALUE})
        void constructor_shouldReject_whenSellerFeePercentOutOfRange(int sellerFeePercent) {
            List<SellLine> lines = Collections.singletonList(LINE_1);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StartPrivateStorePackageSellCommand(
                            1, null, lines, sellerFeePercent, null, false, null, KEY, TestInitiators.USER));
        }
    }

    @Nested
    class Lines {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreCommandsTest#constructors")
        void constructor_shouldReject_whenItemIdRepeats(String name, Function<List<SellLine>, Object> constructor) {
            List<SellLine> lines = Arrays.asList(LINE_1, new SellLine(1, 2L, 50L));

            assertThrows(IllegalArgumentException.class, () -> constructor.apply(lines));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreCommandsTest#constructors")
        void constructor_shouldReject_whenLinesEmpty(String name, Function<List<SellLine>, Object> constructor) {
            assertThrows(IllegalArgumentException.class, () -> constructor.apply(Collections.<SellLine>emptyList()));
        }

        @Test
        void constructor_shouldKeepDistinctLines() {
            StartPrivateStoreSellCommand actual = new StartPrivateStoreSellCommand(
                    1, null, Arrays.asList(LINE_1, LINE_2), null, null, false, null, KEY, TestInitiators.USER);

            assertEquals(Arrays.asList(LINE_1, LINE_2), actual.getLines());
        }
    }

    @Nested
    class Wire {

        private static final String SELL_JSON = "{\"charId\":1,\"title\":\"t\","
                + "\"lines\":[{\"itemId\":1,\"count\":1,\"priceAdena\":100}],\"sellerFeePercent\":5,"
                + "\"offlineAllowed\":true,\"ownerVerified\":true,\"deadline\":\"2026-08-11T12:00:00Z\","
                + "\"idempotencyKey\":\"5b1f3c2e-0000-4000-8000-000000000001\",\"initiator\":"
                + TestInitiators.USER_JSON + "}";

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreCommandsTest#fullCommands")
        void toJson_shouldKeepTheSellWireShape(String name, StartPrivateStoreSellingCommand command) {
            assertEquals(JsonParser.parseString(SELL_JSON), GSON.toJsonTree(command));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreCommandsTest#fullCommands")
        void fromJson_shouldReadTheSellWireShape(String name, StartPrivateStoreSellingCommand command) {
            assertEquals(command, GSON.fromJson(SELL_JSON, command.getClass()));
        }
    }

    static Stream<Arguments> fullCommands() {
        List<SellLine> lines = Collections.singletonList(LINE_1);
        return Stream.of(
                Arguments.of(
                        "sell",
                        new StartPrivateStoreSellCommand(
                                1, "t", lines, 5, true, true, DEADLINE, KEY, TestInitiators.USER)),
                Arguments.of(
                        "package-sell",
                        new StartPrivateStorePackageSellCommand(
                                1, "t", lines, 5, true, true, DEADLINE, KEY, TestInitiators.USER)));
    }
}
