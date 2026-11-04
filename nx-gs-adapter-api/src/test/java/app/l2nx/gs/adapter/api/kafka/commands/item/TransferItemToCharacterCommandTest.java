package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class TransferItemToCharacterCommandTest {

    private static TransferItemToCharacterCommand.Builder valid() {
        return TransferItemToCharacterCommand.builder()
                .fromCharacterId(1L)
                .toCharacterId(2L)
                .itemId(1234567L)
                .initiator(TestInitiators.USER);
    }

    @Test
    void constructor_shouldReject_whenFromCharacterIdNull() {
        TransferItemToCharacterCommand.Builder builder = valid().fromCharacterId(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("fromCharacterId"));
    }

    @Test
    void constructor_shouldReject_whenToCharacterIdNull() {
        TransferItemToCharacterCommand.Builder builder = valid().toCharacterId(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("toCharacterId"));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TransferItemToCharacterCommand original = valid().count(5L).build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void gson_shouldCarryTheCharacterPair() {
        JsonObject actual = new Gson().toJsonTree(valid().build()).getAsJsonObject();

        assertEquals(1L, actual.get("fromCharacterId").getAsLong());
        assertEquals(2L, actual.get("toCharacterId").getAsLong());
        assertFalse(actual.has("charIdFrom"));
        assertFalse(actual.has("charIdTo"));
    }
}
