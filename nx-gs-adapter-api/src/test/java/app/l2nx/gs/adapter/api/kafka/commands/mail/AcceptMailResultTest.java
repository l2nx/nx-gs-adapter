package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailItemMovement;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AcceptMailResultTest {

    private static MailItemMovement movement() {
        return new MailItemMovement(
                ItemSnapshot.builder().itemId(100L).itemTemplateId(57L).build(), 200L, 5000L);
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
        assertEquals(10243L, original.getMailId());
        assertEquals(Collections.singletonList(movement()), original.getItems());
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

    @ParameterizedTest(name = "codPaidAdena={0}, receiveFeeAdena={1}")
    @CsvSource({"-1, 0", "0, -1"})
    void constructor_shouldReject_whenAmountNegative(long codPaidAdena, long receiveFeeAdena) {
        assertThrows(
                IllegalArgumentException.class, () -> new AcceptMailResult(1L, null, codPaidAdena, receiveFeeAdena));
    }
}
