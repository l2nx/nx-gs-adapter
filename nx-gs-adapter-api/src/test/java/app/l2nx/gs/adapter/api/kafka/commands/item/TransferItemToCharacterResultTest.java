package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test
    void constructor_shouldReject_whenItemNull() {
        TransferItemToCharacterResult.Builder builder = valid().item(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("item"));
    }

    @ParameterizedTest(name = "countTransferred={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenCountTransferredNotPositive(long countTransferred) {
        TransferItemToCharacterResult.Builder builder = valid().countTransferred(countTransferred);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenFromCharacterIdNull() {
        TransferItemToCharacterResult.Builder builder = valid().fromCharacterId(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("fromCharacterId"));
    }

    @Test
    void constructor_shouldReject_whenToCharacterIdNull() {
        TransferItemToCharacterResult.Builder builder = valid().toCharacterId(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("toCharacterId"));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TransferItemToCharacterResult original = valid().build();

        TransferItemToCharacterResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
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
