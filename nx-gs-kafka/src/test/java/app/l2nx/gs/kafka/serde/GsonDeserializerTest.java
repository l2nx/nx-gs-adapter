package app.l2nx.gs.kafka.serde;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GsonDeserializerTest {

    private final GsonDeserializer<TestEvent> deserializer = new GsonDeserializer<>(TestEvent.class);

    @Test
    void deserialize_shouldReturnTypedObject() {
        byte[] json = "{\"name\":\"player1\",\"value\":100}".getBytes(StandardCharsets.UTF_8);

        TestEvent result = deserializer.deserialize("test-topic", json);

        assertEquals("player1", result.name);
        assertEquals(100, result.value);
    }

    @Test
    void deserialize_shouldReturnNull_whenDataIsNull() {
        TestEvent result = deserializer.deserialize("test-topic", null);

        assertNull(result);
    }

    static class TestEvent {
        String name;
        int value;
    }
}
