package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    void getAttributes_shouldBeUnmodifiable() {
        BuyStoreLine actual = valid().attributes(Collections.singletonMap(Attribute.FIRE, 300))
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> actual.getAttributes().put(Attribute.WATER, 150));
    }

    @Test
    void constructor_shouldDefensivelyCopyAttributes() {
        Map<Attribute, Integer> source = new HashMap<Attribute, Integer>();
        source.put(Attribute.FIRE, 300);
        BuyStoreLine actual = valid().attributes(source).build();

        source.put(Attribute.WATER, 150);

        assertEquals(Collections.singletonMap(Attribute.FIRE, 300), actual.getAttributes());
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
    }

    @Test
    void equals_shouldDistinguishAttributes() {
        BuyStoreLine a = valid().attributes(Collections.singletonMap(Attribute.FIRE, 300))
                .build();
        BuyStoreLine b = valid().attributes(Collections.singletonMap(Attribute.WATER, 300))
                .build();

        assertNotEquals(a, b);
    }
}
