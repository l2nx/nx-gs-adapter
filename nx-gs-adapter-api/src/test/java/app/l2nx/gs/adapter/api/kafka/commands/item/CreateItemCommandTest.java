package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class CreateItemCommandTest {

    private static CreateItemCommand.Builder valid() {
        return CreateItemCommand.builder().characterId(42L).itemTemplateId(57L).initiator(TestInitiators.USER);
    }

    @Test
    void constructor_shouldReject_whenCharacterIdNull() {
        CreateItemCommand.Builder builder = valid().characterId(null);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains("characterId"));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        CreateItemCommand original = valid().count(5L).enchantLevel(3L).build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void gson_shouldCarryCharacterId() {
        JsonObject actual = new Gson().toJsonTree(valid().build()).getAsJsonObject();

        assertEquals(42L, actual.get("characterId").getAsLong());
        assertFalse(actual.has("charId"));
    }
}
