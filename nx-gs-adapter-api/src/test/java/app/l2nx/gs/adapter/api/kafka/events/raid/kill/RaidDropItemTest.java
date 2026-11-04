package app.l2nx.gs.adapter.api.kafka.events.raid.kill;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RaidDropItemTest {

    @Test
    void getEnchantLevel_shouldBeNullable() {
        RaidDropItem drop =
                RaidDropItem.builder().itemTemplateId(57L).count(100_000_000L).build();

        assertNull(drop.getEnchantLevel());
    }

    @Test
    void builder_shouldPopulateAllFields() {
        RaidDropItem drop = RaidDropItem.builder()
                .itemTemplateId(6660L)
                .count(1L)
                .enchantLevel(5)
                .build();

        assertEquals(6660L, drop.getItemTemplateId());
        assertEquals(1L, drop.getCount());
        assertEquals(Integer.valueOf(5), drop.getEnchantLevel());
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        RaidDropItem original = RaidDropItem.builder()
                .itemTemplateId(6660L)
                .count(1L)
                .enchantLevel(8)
                .build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void equals_shouldDistinguishEnchantLevel() {
        RaidDropItem a = RaidDropItem.builder()
                .itemTemplateId(1L)
                .count(1L)
                .enchantLevel(0)
                .build();
        RaidDropItem b = RaidDropItem.builder()
                .itemTemplateId(1L)
                .count(1L)
                .enchantLevel(1)
                .build();

        assertNotEquals(a, b);
    }
}
