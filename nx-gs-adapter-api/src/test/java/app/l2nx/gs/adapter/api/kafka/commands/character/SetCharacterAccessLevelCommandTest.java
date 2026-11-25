package app.l2nx.gs.adapter.api.kafka.commands.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SetCharacterAccessLevelCommandTest {

    @Test
    void builder_shouldRoundtripFields() {
        SetCharacterAccessLevelCommand cmd = SetCharacterAccessLevelCommand.builder()
                .characterId(42L)
                .accessLevel("7")
                .staffNotes("promoted for event duty")
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, cmd.getCharacterId().longValue());
        assertEquals("7", cmd.getAccessLevel());
        assertEquals("promoted for event duty", cmd.getStaffNotes());
        assertEquals(cmd, cmd.toBuilder().build());
        assertEquals(TestInitiators.USER, cmd.getInitiator());
    }

    @Test
    void constructor_shouldAcceptNullStaffNotes() {
        SetCharacterAccessLevelCommand cmd = new SetCharacterAccessLevelCommand(1L, "0", null, TestInitiators.USER);

        assertNull(cmd.getStaffNotes());
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                missing("characterId", () -> new SetCharacterAccessLevelCommand(null, "7", null, TestInitiators.USER)),
                missing("accessLevel", () -> new SetCharacterAccessLevelCommand(1L, null, null, TestInitiators.USER)));
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
        EqualsVerifier.forClass(SetCharacterAccessLevelCommand.class).verify();
    }
}
