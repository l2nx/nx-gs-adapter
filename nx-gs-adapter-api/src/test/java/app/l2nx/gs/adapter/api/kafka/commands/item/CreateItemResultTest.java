package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemLocation;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    private static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("item", (Consumer<CreateItemResult.Builder>) b -> b.item(null)),
                Arguments.of("location", (Consumer<CreateItemResult.Builder>) b -> b.location(null)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("missingRequired")
    void constructor_shouldReject_whenRequiredFieldNull(String field, Consumer<CreateItemResult.Builder> change) {
        CreateItemResult.Builder builder = valid();
        change.accept(builder);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains(field));
    }

    @ParameterizedTest(name = "countCreated={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenCountCreatedNotPositive(long countCreated) {
        CreateItemResult.Builder builder = valid().countCreated(countCreated);

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
        assertEquals(ItemLocation.INVENTORY, original.getLocation());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CreateItemResult.class).verify();
    }
}
