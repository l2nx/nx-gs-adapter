package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemLocation;
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
        CreateItemCommand expected = new CreateItemCommand(42L, 57L, 5L, 3L, ItemLocation.WH, TestInitiators.USER);
        CreateItemCommand original =
                valid().count(5L).enchantLevel(3L).location(ItemLocation.WH).build();

        assertEquals(expected, original);
        assertEquals(expected, original.toBuilder().build());
        assertEquals(42L, original.getCharacterId());
        assertEquals(57L, original.getItemTemplateId());
        assertEquals(TestInitiators.USER, original.getInitiator());
        assertEquals(5L, original.getCount());
        assertEquals(3L, original.getEnchantLevel());
        assertEquals(ItemLocation.WH, original.getLocation());
    }

    @Test
    void gson_shouldCarryCharacterId() {
        JsonObject actual = new Gson().toJsonTree(valid().build()).getAsJsonObject();

        assertEquals(42L, actual.get("characterId").getAsLong());
        assertFalse(actual.has("charId"));
    }
}
