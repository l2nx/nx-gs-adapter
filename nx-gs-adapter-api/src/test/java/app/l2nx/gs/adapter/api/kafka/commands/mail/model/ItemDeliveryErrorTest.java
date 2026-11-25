package app.l2nx.gs.adapter.api.kafka.commands.mail.model;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ItemDeliveryErrorTest {

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        ItemDeliveryError original = ItemDeliveryError.builder()
                .itemTemplateId(57L)
                .count(2L)
                .reason("template not found")
                .build();

        assertEquals(57L, original.getItemTemplateId().longValue());
        assertEquals(2L, original.getCount().longValue());
        assertEquals("template not found", original.getReason());
        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void constructor_shouldAllowAllNullsExceptStoredReason() {
        ItemDeliveryError err = new ItemDeliveryError(null, null, null);

        assertNull(err.getItemTemplateId());
        assertNull(err.getCount());
        assertEquals("", err.getReason());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ItemDeliveryError.class).verify();
    }
}
