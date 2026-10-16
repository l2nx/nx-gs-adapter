package app.l2nx.gs.adapter.api.spi;

import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import org.junit.jupiter.api.Test;

class NoOpCommandReceiptsTest {

    @Test
    void claim_shouldThrow_becauseAMoneyWriteMustNeverBeDroppedSilently() {
        assertThrows(
                IllegalStateException.class,
                () -> NoOpCommandReceipts.INSTANCE.claim("key", "CMD", ReceiptClaim.ofClaimJson("{}")));
    }

    @Test
    void markOk_shouldThrow_insteadOfReportingASettle() {
        assertThrows(IllegalStateException.class, () -> NoOpCommandReceipts.INSTANCE.markOk(null, "key", 1L, "{}"));
    }
}
