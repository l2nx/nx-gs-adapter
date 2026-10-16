package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class ReadMailCommandTest {

    private static ReadMailCommand.Builder valid() {
        return ReadMailCommand.builder().charId(1L).mailId(10L);
    }

    @Test
    void constructor_shouldReject_whenCharIdNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> valid().charId(null).build());
        assertTrue(ex.getMessage().contains("charId"));
    }

    @Test
    void constructor_shouldReject_whenMailIdNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> valid().mailId(null).build());
        assertTrue(ex.getMessage().contains("mailId"));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        ReadMailCommand original = valid().build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void equals_shouldDistinguishMailId() {
        assertNotEquals(valid().build(), valid().mailId(11L).build());
    }

    @Test
    void gson_shouldRoundtripCommand() {
        Gson gson = new Gson();
        ReadMailCommand original = valid().build();

        ReadMailCommand copy = gson.fromJson(gson.toJson(original), ReadMailCommand.class);

        assertEquals(original, copy);
    }
}
