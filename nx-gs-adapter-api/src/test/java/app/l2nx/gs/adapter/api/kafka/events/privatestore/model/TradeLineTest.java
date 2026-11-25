package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class TradeLineTest {

    private static TradeLine.Builder valid() {
        return TradeLine.builder()
                .item(ItemSnapshot.builder()
                        .itemId(268437521L)
                        .itemTemplateId(6364L)
                        .enchantLevel(16)
                        .count(5L)
                        .build())
                .count(1L)
                .unitPrice(1_500_000_000L)
                .currencyItemTemplateId(57L);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        TradeLine.Builder builder = valid().item(null);

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void getCount_shouldStayApartFromTheStackSizeOfTheItem() {
        TradeLine actual = valid().build();

        assertEquals(1L, actual.getCount());
        assertEquals(Long.valueOf(5L), actual.getItem().getCount());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TradeLine original = valid().build();

        TradeLine copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(original.getItem(), copy.getItem());
        assertEquals(268437521L, copy.getItem().getItemId());
        assertEquals(1L, copy.getCount());
        assertEquals(1_500_000_000L, copy.getUnitPrice());
        assertEquals(57L, copy.getCurrencyItemTemplateId());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(TradeLine.class).verify();
    }
}
