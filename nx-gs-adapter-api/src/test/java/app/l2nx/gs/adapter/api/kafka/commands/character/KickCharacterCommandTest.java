package app.l2nx.gs.adapter.api.kafka.commands.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class KickCharacterCommandTest {

    @Test
    void builder_shouldRoundtripFields() {
        KickCharacterCommand cmd = KickCharacterCommand.builder()
                .characterId(42L)
                .closeClient(true)
                .staffNotes("botting suspicion")
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, cmd.getCharacterId().longValue());
        assertTrue(cmd.isCloseClient());
        assertEquals("botting suspicion", cmd.getStaffNotes());
        assertEquals(cmd, cmd.toBuilder().build());
        assertEquals(TestInitiators.USER, cmd.getInitiator());
    }

    @Test
    void constructor_shouldAcceptNullStaffNotes() {
        KickCharacterCommand cmd = new KickCharacterCommand(1L, false, null, TestInitiators.USER);

        assertNull(cmd.getStaffNotes());
    }

    @Test
    void constructor_shouldRejectNullCharacterId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new KickCharacterCommand(null, true, null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("characterId"));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(KickCharacterCommand.class).verify();
    }
}
