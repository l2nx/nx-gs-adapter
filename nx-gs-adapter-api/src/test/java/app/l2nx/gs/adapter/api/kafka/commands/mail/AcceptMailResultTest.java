package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.events.mail.MailItemMovement;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptMailResultTest {

    private static MailItemMovement movement() {
        return new MailItemMovement(57L, 100L, 200L, 5000L, null, null);
    }

    @Test
    void builder_shouldRoundtripAllFields() {
        AcceptMailResult original = AcceptMailResult.builder()
                .mailId(10243L)
                .items(Collections.singletonList(movement()))
                .codPaidAdena(1000L)
                .receiveFeeAdena(500L)
                .build();

        assertEquals(original, original.toBuilder().build());
        assertEquals(1000L, original.getCodPaidAdena());
        assertEquals(500L, original.getReceiveFeeAdena());
    }

    @Test
    void getItems_shouldBeEmptyAndUnmodifiable_whenOmitted() {
        AcceptMailResult result = AcceptMailResult.builder().mailId(1L).build();
        List<MailItemMovement> items = result.getItems();

        assertTrue(items.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> items.add(movement()));
    }

    @Test
    void constructor_shouldDefaultAmountsToZero_whenBuilderOmits() {
        AcceptMailResult result = AcceptMailResult.builder().mailId(1L).build();

        assertEquals(0L, result.getCodPaidAdena());
        assertEquals(0L, result.getReceiveFeeAdena());
    }

    @Test
    void constructor_shouldRejectNegativeCodPaid() {
        assertThrows(IllegalArgumentException.class, () -> new AcceptMailResult(1L, null, -1L, 0L));
    }

    @Test
    void constructor_shouldRejectNegativeReceiveFee() {
        assertThrows(IllegalArgumentException.class, () -> new AcceptMailResult(1L, null, 0L, -1L));
    }
}
