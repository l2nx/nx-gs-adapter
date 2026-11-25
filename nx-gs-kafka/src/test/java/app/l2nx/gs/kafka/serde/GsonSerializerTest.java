package app.l2nx.gs.kafka.serde;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GsonSerializerTest {

    private final GsonSerializer serializer = new GsonSerializer();

    @Test
    void serialize_shouldReturnJsonBytes() {
        TestEvent event = new TestEvent("player1", 100);

        byte[] result = serializer.serialize("test-topic", event);

        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"player1\""));
        assertTrue(json.contains("\"value\":100"));
    }

    @Test
    void serialize_shouldReturnNull_whenDataIsNull() {
        byte[] result = serializer.serialize("test-topic", null);

        assertNull(result);
    }

    @Test
    void serialize_shouldHandleString() {
        byte[] result = serializer.serialize("test-topic", "hello");

        assertEquals("\"hello\"", new String(result, StandardCharsets.UTF_8));
    }

    static class TestEvent {
        final String name;
        final int value;

        TestEvent(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
