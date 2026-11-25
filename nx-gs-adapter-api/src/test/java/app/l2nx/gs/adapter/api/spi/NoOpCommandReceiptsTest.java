package app.l2nx.gs.adapter.api.spi;

import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import java.util.stream.Stream;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NoOpCommandReceiptsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("moneyWrites")
    void write_shouldThrow_becauseAMoneyWriteMustNeverBeDroppedSilently(String name, Executable write) {
        assertThrows(IllegalStateException.class, write);
    }

    static Stream<Arguments> moneyWrites() {
        return Stream.of(
                moneyWrite(
                        "claim",
                        () -> NoOpCommandReceipts.INSTANCE.claim("key", "CMD", ReceiptClaim.ofClaimJson("{}"))),
                moneyWrite("markOk", () -> NoOpCommandReceipts.INSTANCE.markOk(null, "key", 1L, "{}")));
    }

    private static Arguments moneyWrite(String name, Executable write) {
        return Arguments.of(name, write);
    }
}
