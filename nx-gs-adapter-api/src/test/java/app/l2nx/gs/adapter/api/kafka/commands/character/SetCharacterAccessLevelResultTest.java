package app.l2nx.gs.adapter.api.kafka.commands.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SetCharacterAccessLevelResultTest {

    @Test
    void builder_shouldRoundtripFields() {
        SetCharacterAccessLevelResult result = SetCharacterAccessLevelResult.builder()
                .characterId(42L)
                .accessLevel("7")
                .previousAccessLevel("0")
                .wasOnline(true)
                .build();

        assertEquals(42L, result.getCharacterId().longValue());
        assertEquals("7", result.getAccessLevel());
        assertEquals("0", result.getPreviousAccessLevel());
        assertTrue(result.isWasOnline());
        assertEquals(result, result.toBuilder().build());
    }

    @Test
    void constructor_shouldAcceptNullPreviousAccessLevel() {
        SetCharacterAccessLevelResult result = new SetCharacterAccessLevelResult(1L, "7", null, false);

        assertNull(result.getPreviousAccessLevel());
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                missing("characterId", () -> new SetCharacterAccessLevelResult(null, "7", null, false)),
                missing("accessLevel", () -> new SetCharacterAccessLevelResult(1L, null, null, false)));
    }

    private static Arguments missing(String field, Executable construct) {
        return Arguments.of(field, construct);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequired")
    void constructor_shouldReject_whenRequiredFieldIsNull(String field, Executable construct) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, construct);
        assertTrue(ex.getMessage().contains(field));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SetCharacterAccessLevelResult.class).verify();
    }
}
