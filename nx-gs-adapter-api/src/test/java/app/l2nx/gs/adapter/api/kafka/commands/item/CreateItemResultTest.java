package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemLocation;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreateItemResultTest {

    private static CreateItemResult.Builder valid() {
        return CreateItemResult.builder()
                .item(ItemSnapshot.builder()
                        .itemId(1234567L)
                        .itemTemplateId(57L)
                        .count(150L)
                        .build())
                .countCreated(50L)
                .location(ItemLocation.INVENTORY);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        CreateItemResult.Builder builder = valid().item(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("item"));
    }

    @ParameterizedTest(name = "countCreated={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenCountCreatedNotPositive(long countCreated) {
        CreateItemResult.Builder builder = valid().countCreated(countCreated);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLocationNull() {
        CreateItemResult.Builder builder = valid().location(null);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void getCountCreated_shouldStayApartFromTheStackThatAbsorbedTheGrant() {
        CreateItemResult actual = valid().build();

        assertEquals(Long.valueOf(50L), actual.getCountCreated());
        assertEquals(Long.valueOf(150L), actual.getItem().getCount());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        CreateItemResult original = valid().build();

        CreateItemResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishLocation() {
        assertNotEquals(valid().build(), valid().location(ItemLocation.WH).build());
    }
}
