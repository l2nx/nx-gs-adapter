package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class SoldLineTest {

    private static SoldLine.Builder valid() {
        return SoldLine.builder()
                .item(ItemSnapshot.builder()
                        .itemId(268437521L)
                        .itemTemplateId(57L)
                        .enchantLevel(16)
                        .build())
                .count(3L)
                .unitPriceAdena(1_500_000L);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        SoldLine.Builder builder = valid().item(null);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        SoldLine original = valid().build();

        SoldLine copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(3L, original.getCount());
        assertEquals(1_500_000L, original.getUnitPriceAdena());
        assertEquals(268437521L, original.getItem().getItemId());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SoldLine.class).verify();
    }
}
