package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeleteItemCommandTest {

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        DeleteItemCommand original = DeleteItemCommand.builder()
                .characterId(42L)
                .itemId(1234567L)
                .count(7L)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, original.getCharacterId().longValue());
        assertEquals(1234567L, original.getItemId().longValue());
        assertEquals(7L, original.getCount().longValue());
        assertEquals(original, original.toBuilder().build());
        assertEquals(TestInitiators.USER, original.getInitiator());
    }

    @Test
    void builder_shouldDefaultCountToOne_whenNotSet() {
        DeleteItemCommand cmd = DeleteItemCommand.builder()
                .characterId(42L)
                .itemId(1234567L)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(1L, cmd.getCount().longValue());
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of(
                        "characterId", (Executable) () -> new DeleteItemCommand(null, 1L, 1L, TestInitiators.USER)),
                Arguments.of("itemId", (Executable) () -> new DeleteItemCommand(1L, null, 1L, TestInitiators.USER)),
                Arguments.of("count", (Executable) () -> new DeleteItemCommand(1L, 1L, null, TestInitiators.USER)),
                Arguments.of("count", (Executable) () -> new DeleteItemCommand(1L, 1L, 0L, TestInitiators.USER)),
                Arguments.of("count", (Executable) () -> new DeleteItemCommand(1L, 1L, -5L, TestInitiators.USER)),
                Arguments.of("characterId", (Executable) () -> DeleteItemCommand.builder()
                        .itemId(1L)
                        .initiator(TestInitiators.USER)
                        .build()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidFields")
    void constructor_shouldReject_whenFieldInvalid(String field, Executable construction) {
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, construction);
        assertTrue(actual.getMessage().contains(field));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(DeleteItemCommand.class).verify();
    }
}
