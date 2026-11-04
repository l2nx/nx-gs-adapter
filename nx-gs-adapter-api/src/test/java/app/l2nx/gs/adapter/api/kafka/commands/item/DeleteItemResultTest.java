package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import org.junit.jupiter.api.Test;

class DeleteItemResultTest {

    private static DeleteItemResult.Builder valid() {
        return DeleteItemResult.builder()
                .item(ItemSnapshot.builder()
                        .itemId(1234567L)
                        .itemTemplateId(57L)
                        .count(10L)
                        .build())
                .countDeleted(10L)
                .fullyDeleted(true);
    }

    @Test
    void constructor_shouldReject_whenItemNull() {
        DeleteItemResult.Builder builder = valid().item(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("item"));
    }

    @Test
    void constructor_shouldReject_whenCountDeletedNegative() {
        DeleteItemResult.Builder builder = valid().countDeleted(-1L);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldAccept_whenNothingWasDeleted() {
        assertEquals(Long.valueOf(0L), valid().countDeleted(0L).build().getCountDeleted());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        DeleteItemResult original = valid().build();

        DeleteItemResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishFullyDeleted() {
        assertNotEquals(valid().build(), valid().fullyDeleted(false).build());
    }
}
