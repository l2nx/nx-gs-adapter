package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("item", (Consumer<DeleteItemResult.Builder>) b -> b.item(null)),
                Arguments.of("countDeleted", (Consumer<DeleteItemResult.Builder>) b -> b.countDeleted(-1L)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidFields")
    void constructor_shouldReject_whenFieldInvalid(String field, Consumer<DeleteItemResult.Builder> change) {
        DeleteItemResult.Builder builder = valid();
        change.accept(builder);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains(field));
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
        assertTrue(copy.isFullyDeleted());
        assertEquals(10L, copy.getCountDeleted());
        assertEquals(1234567L, copy.getItem().getItemId());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(DeleteItemResult.class).verify();
    }
}
