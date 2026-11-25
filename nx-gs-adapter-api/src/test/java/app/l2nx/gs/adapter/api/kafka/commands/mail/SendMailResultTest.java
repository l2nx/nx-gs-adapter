package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.mail.model.ItemDeliveryError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class SendMailResultTest {

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        ItemDeliveryError err = ItemDeliveryError.builder()
                .itemTemplateId(57L)
                .count(1L)
                .reason("x")
                .build();

        SendMailResult original = SendMailResult.builder()
                .createdMailIds(Arrays.asList(101L, 102L))
                .itemErrors(Collections.singletonList(err))
                .build();

        assertEquals(Arrays.asList(101L, 102L), original.getCreatedMailIds());
        assertEquals(Collections.singletonList(err), original.getItemErrors());
        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void constructor_shouldDefaultListsToEmpty_whenNull() {
        SendMailResult result = new SendMailResult(null, null);

        assertTrue(result.getCreatedMailIds().isEmpty());
        assertTrue(result.getItemErrors().isEmpty());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeLists() {
        ItemDeliveryError err =
                ItemDeliveryError.builder().itemTemplateId(57L).count(1L).build();
        List<Long> mailIds = new ArrayList<>(Arrays.asList(1L, 2L));
        List<ItemDeliveryError> itemErrors = new ArrayList<>(Collections.singletonList(err));
        SendMailResult result = new SendMailResult(mailIds, itemErrors);

        mailIds.add(3L);
        itemErrors.add(err);

        assertEquals(2, result.getCreatedMailIds().size());
        assertEquals(1, result.getItemErrors().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getCreatedMailIds().add(3L));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getItemErrors().add(err));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SendMailResult.class).verify();
    }
}
