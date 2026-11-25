package app.l2nx.gs.adapter.api.kafka.events.raid.kill;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RaidDropItemTest {

    @Test
    void getEnchantLevel_shouldBeNullable() {
        RaidDropItem drop =
                RaidDropItem.builder().itemTemplateId(57L).count(100_000_000L).build();

        assertNull(drop.getEnchantLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 8})
    void toBuilder_shouldRoundtripAllFields(int enchantLevel) {
        RaidDropItem original = RaidDropItem.builder()
                .itemTemplateId(6660L)
                .count(1L)
                .enchantLevel(enchantLevel)
                .build();

        RaidDropItem copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(6660L, copy.getItemTemplateId());
        assertEquals(1L, copy.getCount());
        assertEquals(Integer.valueOf(enchantLevel), copy.getEnchantLevel());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(RaidDropItem.class).verify();
    }
}
