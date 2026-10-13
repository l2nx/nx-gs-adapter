package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    static Stream<Arguments> constructors() {
        Function<List<SellLine>, Object> sell =
                lines -> new StartPrivateStoreSellCommand(1, null, lines, null, null, false, null);
        Function<List<SellLine>, Object> packageSell =
                lines -> new StartPrivateStorePackageSellCommand(1, null, lines, null, null, false, null);
        return Stream.of(Arguments.of("sell", sell), Arguments.of("package-sell", packageSell));
    }

    @Nested
    class SellerFeePercent {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreCommandsTest#constructors")
        void getSellerFeePercent_shouldBeNull_whenAbsent(String name, Function<List<SellLine>, Object> constructor) {
            StartPrivateStoreCommand actual =
                    (StartPrivateStoreCommand) constructor.apply(Collections.singletonList(LINE_1));

            assertNull(actual.getSellerFeePercent());
        }

        @Test
        void constructor_shouldKeepSellerFeePercent_whenAtMax() {
            StartPrivateStoreSellCommand actual = new StartPrivateStoreSellCommand(
                    1, null, Collections.singletonList(LINE_1), PrivateStoreFees.MAX_FEE_PERCENT, null, false, null);

            assertEquals(Integer.valueOf(50), actual.getSellerFeePercent());
        }

        @ParameterizedTest(name = "sellerFeePercent={0}")
        @ValueSource(ints = {-1, 51})
        void constructor_shouldReject_whenSellerFeePercentOutOfRange(int sellerFeePercent) {
            List<SellLine> lines = Collections.singletonList(LINE_1);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StartPrivateStorePackageSellCommand(1, null, lines, sellerFeePercent, null, false, null));
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
            StartPrivateStoreSellCommand actual =
                    new StartPrivateStoreSellCommand(1, null, Arrays.asList(LINE_1, LINE_2), null, null, false, null);

            assertEquals(Arrays.asList(LINE_1, LINE_2), actual.getLines());
        }
    }
}
