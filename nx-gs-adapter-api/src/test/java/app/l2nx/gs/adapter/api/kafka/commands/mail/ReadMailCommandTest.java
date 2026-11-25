package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import com.google.gson.Gson;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReadMailCommandTest {

    private static ReadMailCommand.Builder valid() {
        return ReadMailCommand.builder().characterId(1L).mailId(10L).initiator(TestInitiators.USER);
    }

    private static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("characterId", (Consumer<ReadMailCommand.Builder>) b -> b.characterId(null)),
                Arguments.of("mailId", (Consumer<ReadMailCommand.Builder>) b -> b.mailId(null)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("missingRequired")
    void constructor_shouldReject_whenRequiredFieldNull(String field, Consumer<ReadMailCommand.Builder> change) {
        ReadMailCommand.Builder builder = valid();
        change.accept(builder);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(actual.getMessage().contains(field));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        ReadMailCommand original = valid().build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ReadMailCommand.class).verify();
    }

    @Test
    void gson_shouldRoundtripCommand() {
        Gson gson = new Gson();
        ReadMailCommand original = valid().build();

        ReadMailCommand copy = gson.fromJson(gson.toJson(original), ReadMailCommand.class);

        assertEquals(original, copy);
        assertEquals(1L, original.getCharacterId());
        assertEquals(10L, original.getMailId());
        assertEquals(TestInitiators.USER, original.getInitiator());
    }
}
