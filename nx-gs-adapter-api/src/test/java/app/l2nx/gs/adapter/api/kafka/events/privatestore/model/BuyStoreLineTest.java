package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class BuyStoreLineTest {

    private static BuyStoreLine.Builder valid() {
        return BuyStoreLine.builder()
                .itemTemplateId(6364L)
                .count(2L)
                .unitPrice(1000L)
                .currencyItemTemplateId(57L);
    }

    @Test
    void getEnchantLevel_shouldDefaultToNull() {
        assertNull(valid().build().getEnchantLevel());
    }

    @Test
    void getAttributes_shouldReturnEmptyMap_whenBuilderOmits() {
        assertTrue(valid().build().getAttributes().isEmpty());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeAttributes() {
        Map<Attribute, Integer> source = new HashMap<Attribute, Integer>();
        source.put(Attribute.FIRE, 300);
        BuyStoreLine actual = valid().attributes(source).build();

        source.put(Attribute.WATER, 150);

        assertEquals(Collections.singletonMap(Attribute.FIRE, 300), actual.getAttributes());
        assertThrows(
                UnsupportedOperationException.class,
                () -> actual.getAttributes().put(Attribute.WATER, 150));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BuyStoreLine original = valid().enchantLevel(16)
                .attributes(Collections.singletonMap(Attribute.HOLY, 150))
                .build();

        BuyStoreLine copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
        assertEquals(6364L, copy.getItemTemplateId());
        assertEquals(Integer.valueOf(16), copy.getEnchantLevel());
        assertEquals(Collections.singletonMap(Attribute.HOLY, 150), copy.getAttributes());
        assertEquals(2L, copy.getCount());
        assertEquals(1000L, copy.getUnitPrice());
        assertEquals(57L, copy.getCurrencyItemTemplateId());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(BuyStoreLine.class).verify();
    }
}
