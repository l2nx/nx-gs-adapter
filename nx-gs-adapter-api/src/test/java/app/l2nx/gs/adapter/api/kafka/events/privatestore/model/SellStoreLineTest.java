package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import org.junit.jupiter.api.Test;

class SellStoreLineTest {

    private static SellStoreLine.Builder valid() {
        return SellStoreLine.builder()
                .item(ItemSnapshot.builder()
                        .itemId(555L)
                        .itemTemplateId(6364L)
                        .count(5L)
                        .build())
                .count(2L)
                .unitPrice(1000L)
                .currencyItemTemplateId(57L);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        SellStoreLine.Builder builder = valid().item(null);

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void getCount_shouldStayApartFromTheStackSizeOfTheItem() {
        SellStoreLine actual = valid().build();

        assertEquals(2L, actual.getCount());
        assertEquals(Long.valueOf(5L), actual.getItem().getCount());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        SellStoreLine original = valid().build();

        SellStoreLine copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishUnitPrice() {
        assertNotEquals(valid().build(), valid().unitPrice(1001L).build());
    }
}
