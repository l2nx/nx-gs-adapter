package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BuyLineTest {

    private static ItemSnapshot.Builder item() {
        return ItemSnapshot.builder().itemId(1L).itemTemplateId(57L);
    }

    private static BuyLine.Builder valid() {
        return BuyLine.builder().item(item().build()).count(1L).unitPriceAdena(100L);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        BuyLine.Builder builder = valid().item(null);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "count={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenCountNotPositive(long count) {
        BuyLine.Builder builder = valid().count(count);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenUnitPriceAdenaNegative() {
        BuyLine.Builder builder = valid().unitPriceAdena(-1L);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenCountTimesUnitPriceOverflows() {
        BuyLine.Builder builder = valid().count(Long.MAX_VALUE).unitPriceAdena(2L);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "enchantLevel={0}")
    @CsvSource({"-1", "128"})
    void constructor_shouldReject_whenEnchantLevelOutOfRange(int enchantLevel) {
        BuyLine.Builder builder = valid().item(item().enchantLevel(enchantLevel).build());
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "enchantLevel={0}")
    @CsvSource({"0", "127"})
    void constructor_shouldAccept_whenEnchantLevelAtBounds(int enchantLevel) {
        BuyLine line = valid().item(item().enchantLevel(enchantLevel).build()).build();
        assertEquals(Integer.valueOf(enchantLevel), line.getItem().getEnchantLevel());
    }

    @Test
    void getCount_shouldStayApartFromTheStackSizeOfTheItem() {
        BuyLine line = valid().item(item().count(50L).build()).count(3L).build();

        assertEquals(3L, line.getCount());
        assertEquals(Long.valueOf(50L), line.getItem().getCount());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BuyLine original = BuyLine.builder()
                .item(item().itemId(268437521L)
                        .enchantLevel(16)
                        .attributes(Collections.singletonMap(Attribute.FIRE, 300))
                        .build())
                .count(3L)
                .unitPriceAdena(1_500_000L)
                .build();

        BuyLine copy = original.toBuilder().build();
        assertEquals(original, copy);
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishItem() {
        BuyLine a = valid().item(item().itemId(1L).build()).build();
        BuyLine b = valid().item(item().itemId(2L).build()).build();
        assertNotEquals(a, b);
    }
}
