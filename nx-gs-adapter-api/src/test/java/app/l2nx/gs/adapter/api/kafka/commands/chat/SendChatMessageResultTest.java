package app.l2nx.gs.adapter.api.kafka.commands.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SendChatMessageResultTest {

    private static final Gson GSON = new Gson();

    @Test
    void fromJson_shouldReadNoTargetInGame_whenAnOlderHostOmitsIt() {
        SendChatMessageResult actual = GSON.fromJson("{\"linesSent\":1,\"recipients\":0}", SendChatMessageResult.class);

        assertNull(actual.getTargetInGame());
    }

    @Test
    void toJson_shouldOmitTargetInGame_whenNotAWhisper() {
        String actual = GSON.toJson(new SendChatMessageResult(2, 5, null));

        assertFalse(actual.contains("targetInGame"), actual);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fromJson_shouldReadTargetInGame_whenSent(boolean targetInGame) {
        SendChatMessageResult expected = SendChatMessageResult.builder()
                .linesSent(1)
                .recipients(targetInGame ? 1 : 0)
                .targetInGame(targetInGame)
                .build();

        SendChatMessageResult actual = GSON.fromJson(GSON.toJson(expected), SendChatMessageResult.class);

        assertEquals(expected, actual);
    }

    @Test
    void equals_shouldTellTargetInGameApart() {
        SendChatMessageResult original = new SendChatMessageResult(1, 1, true);

        assertEquals(original, original.toBuilder().build());
        assertNotEquals(original, original.toBuilder().targetInGame(false).build());
        assertNotEquals(original, original.toBuilder().targetInGame(null).build());
    }
}
