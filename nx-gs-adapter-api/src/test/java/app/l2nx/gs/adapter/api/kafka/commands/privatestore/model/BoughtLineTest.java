package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import org.junit.jupiter.api.Test;

class BoughtLineTest {

    private static BoughtLine.Builder valid() {
        return BoughtLine.builder()
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
        BoughtLine.Builder builder = valid().item(null);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BoughtLine original = valid().build();

        BoughtLine copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishUnitPriceAdena() {
        assertNotEquals(valid().build(), valid().unitPriceAdena(101L).build());
    }
}
