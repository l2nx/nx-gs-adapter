package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class TransferItemToCharacterResultTest {

    private static TransferItemToCharacterResult.Builder valid() {
        return TransferItemToCharacterResult.builder()
                .item(ItemSnapshot.builder()
                        .itemId(1234567L)
                        .itemTemplateId(57L)
                        .count(10L)
                        .build())
                .countTransferred(4L)
                .fromCharacterId(1L)
                .toCharacterId(2L);
    }

    private static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("item", (Consumer<TransferItemToCharacterResult.Builder>) b -> b.item(null)),
                Arguments.of("fromCharacterId", (Consumer<TransferItemToCharacterResult.Builder>)
                        b -> b.fromCharacterId(null)),
                Arguments.of(
                        "toCharacterId", (Consumer<TransferItemToCharacterResult.Builder>) b -> b.toCharacterId(null)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("missingRequired")
    void constructor_shouldReject_whenRequiredFieldNull(
            String field, Consumer<TransferItemToCharacterResult.Builder> change) {
        TransferItemToCharacterResult.Builder builder = valid();
        change.accept(builder);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains(field));
    }

    @ParameterizedTest(name = "countTransferred={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenCountTransferredNotPositive(long countTransferred) {
        TransferItemToCharacterResult.Builder builder = valid().countTransferred(countTransferred);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TransferItemToCharacterResult original = valid().build();

        TransferItemToCharacterResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
        assertEquals(4L, original.getCountTransferred());
        assertEquals(1L, original.getFromCharacterId());
        assertEquals(2L, original.getToCharacterId());
        assertEquals(1234567L, original.getItem().getItemId());
    }

    @Test
    void gson_shouldNameTheEndpointsCharacter() {
        JsonObject actual = new Gson().toJsonTree(valid().build()).getAsJsonObject();

        assertEquals(1L, actual.get("fromCharacterId").getAsLong());
        assertEquals(2L, actual.get("toCharacterId").getAsLong());
        assertFalse(actual.has("fromCharId"));
        assertFalse(actual.has("toCharId"));
    }
}
