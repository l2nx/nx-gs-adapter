package app.l2nx.gs.adapter.api.kafka.sync.db.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.ItemAugmentation;
import app.l2nx.gs.adapter.api.domain.item.ItemLocation;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ItemDbDtoTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        Map<Attribute, Integer> attrs = new LinkedHashMap<Attribute, Integer>();
        attrs.put(Attribute.FIRE, 150);
        attrs.put(Attribute.WATER, 60);
        ItemAugmentation augmentation =
                ItemAugmentation.builder().option1Id(1000).option2Id(2000).build();

        ItemDbDto item = ItemDbDto.builder()
                .id(98765L)
                .itemTemplateId(7575L)
                .ownerId(12345L)
                .count(1L)
                .enchantLevel(16)
                .location(ItemLocation.EQUIP)
                .attributes(attrs)
                .augmentation(augmentation)
                .build();

        assertEquals(98765L, item.getId());
        assertEquals(Long.valueOf(7575L), item.getItemTemplateId());
        assertEquals(Long.valueOf(12345L), item.getOwnerId());
        assertEquals(Long.valueOf(1L), item.getCount());
        assertEquals(Integer.valueOf(16), item.getEnchantLevel());
        assertEquals(ItemLocation.EQUIP, item.getLocation());
        assertEquals(attrs, item.getAttributes());
        assertEquals(augmentation, item.getAugmentation());
        assertEquals(item, item.toBuilder().build());
    }

    @Test
    void allOptionalFields_shouldBeNullable_whenTenantOmitsThem() {
        ItemDbDto item = ItemDbDto.builder().id(1L).build();

        assertEquals(1L, item.getId());
        assertNull(item.getItemTemplateId());
        assertNull(item.getOwnerId());
        assertNull(item.getCount());
        assertNull(item.getEnchantLevel());
        assertNull(item.getLocation());
        assertNull(item.getAttributes());
        assertNull(item.getAugmentation());
    }

    @Test
    void attributes_shouldBeEmptyMap_whenTenantSyncsThemButItemHasNone() {
        ItemDbDto item =
                ItemDbDto.builder().id(1L).attributes(Collections.emptyMap()).build();

        assertNotNull(item.getAttributes());
        assertTrue(item.getAttributes().isEmpty());
    }

    @Test
    void attributes_shouldBeAnUnmodifiableCopy() {
        Map<Attribute, Integer> source = new LinkedHashMap<Attribute, Integer>();
        source.put(Attribute.FIRE, 150);
        ItemDbDto item = ItemDbDto.builder().id(1L).attributes(source).build();

        source.put(Attribute.WATER, 60);

        assertEquals(Collections.singletonMap(Attribute.FIRE, 150), item.getAttributes());
        assertThrows(
                UnsupportedOperationException.class, () -> item.getAttributes().put(Attribute.DARK, 1));
    }

    @Test
    void gson_shouldKeyAttributesByElementName() {
        ItemDbDto item = ItemDbDto.builder()
                .id(1L)
                .attributes(Collections.singletonMap(Attribute.FIRE, 150))
                .build();

        assertTrue(new Gson().toJson(item).contains("\"attributes\":{\"FIRE\":150}"));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ItemDbDto.class).verify();
    }
}
