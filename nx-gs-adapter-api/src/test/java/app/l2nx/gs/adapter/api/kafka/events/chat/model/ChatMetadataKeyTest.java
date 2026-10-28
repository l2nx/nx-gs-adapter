package app.l2nx.gs.adapter.api.kafka.events.chat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ChatMetadataKeyTest {

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "SOURCE, source",
        "SHADOWED, shadowed",
        "RAW_TYPE, rawType",
        "SERVER_WIDE, serverWide",
        "UNDELIVERED, undelivered"
    })
    void key_shouldKeepTheShippedWireString(ChatMetadataKey key, String expected) {
        assertEquals(expected, key.key());
    }

    @Test
    void values_shouldNameOnlyTheShippedKeys() {
        assertEquals(5, ChatMetadataKey.values().length);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ChatMetadataKey.class)
    void fromToken_shouldReadTheKeyNotTheName(ChatMetadataKey key) {
        assertSame(key, ChatMetadataKey.fromToken(key.key()));
        if (!key.name().equals(key.key())) {
            assertNull(ChatMetadataKey.fromToken(key.name()));
        }
    }
}
