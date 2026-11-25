package app.l2nx.gs.adapter.api.kafka.events.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class MailItemMovementTest {

    private static ItemSnapshot item() {
        return ItemSnapshot.builder()
                .itemId(268437521L)
                .itemTemplateId(6611L)
                .count(10L)
                .enchantLevel(16)
                .build();
    }

    private static MailItemMovement.Builder valid() {
        return MailItemMovement.builder().item(item()).newItemId(268437522L).count(5L);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        MailItemMovement.Builder builder = valid().item(null);

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void getCount_shouldStayApartFromTheStackSizeOfTheItem() {
        MailItemMovement actual = valid().build();

        assertEquals(5L, actual.getCount());
        assertEquals(Long.valueOf(10L), actual.getItem().getCount());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        MailItemMovement original = valid().build();

        MailItemMovement copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
        assertEquals(item(), copy.getItem());
        assertEquals(268437521L, copy.getItem().getItemId());
        assertEquals(268437522L, copy.getNewItemId());
        assertEquals(5L, copy.getCount());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MailItemMovement.class).verify();
    }
}
