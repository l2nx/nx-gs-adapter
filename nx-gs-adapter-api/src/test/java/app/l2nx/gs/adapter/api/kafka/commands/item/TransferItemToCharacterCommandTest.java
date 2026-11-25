package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransferItemToCharacterCommandTest {

    private static TransferItemToCharacterCommand.Builder valid() {
        return TransferItemToCharacterCommand.builder()
                .fromCharacterId(1L)
                .toCharacterId(2L)
                .itemId(1234567L)
                .initiator(TestInitiators.USER);
    }

    private static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("fromCharacterId", (Consumer<TransferItemToCharacterCommand.Builder>)
                        b -> b.fromCharacterId(null)),
                Arguments.of("toCharacterId", (Consumer<TransferItemToCharacterCommand.Builder>)
                        b -> b.toCharacterId(null)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("missingRequired")
    void constructor_shouldReject_whenRequiredFieldNull(
            String field, Consumer<TransferItemToCharacterCommand.Builder> change) {
        TransferItemToCharacterCommand.Builder builder = valid();
        change.accept(builder);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains(field));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TransferItemToCharacterCommand expected =
                new TransferItemToCharacterCommand(1L, 2L, 1234567L, 5L, TestInitiators.USER);
        TransferItemToCharacterCommand original = valid().count(5L).build();

        assertEquals(expected, original);
        assertEquals(expected, original.toBuilder().build());
        assertEquals(1L, original.getFromCharacterId());
        assertEquals(2L, original.getToCharacterId());
        assertEquals(1234567L, original.getItemId());
        assertEquals(TestInitiators.USER, original.getInitiator());
        assertEquals(5L, original.getCount());
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
