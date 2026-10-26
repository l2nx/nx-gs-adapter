package app.l2nx.gs.adapter.api.kafka.commands.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import org.junit.jupiter.api.Test;

class KickCharacterCommandTest {

    @Test
    void builder_shouldRoundtripFields() {
        KickCharacterCommand cmd = KickCharacterCommand.builder()
                .charId(42L)
                .closeClient(true)
                .staffNotes("botting suspicion")
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, cmd.getCharId().longValue());
        assertTrue(cmd.isCloseClient());
        assertEquals("botting suspicion", cmd.getStaffNotes());
    }

    @Test
    void constructor_shouldAcceptNullStaffNotes() {
        KickCharacterCommand cmd = new KickCharacterCommand(1L, false, null, TestInitiators.USER);

        assertNull(cmd.getStaffNotes());
    }

    @Test
    void constructor_shouldRejectNullCharId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new KickCharacterCommand(null, true, null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("charId"));
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        KickCharacterCommand original = KickCharacterCommand.builder()
                .charId(42L)
                .closeClient(true)
                .staffNotes("note")
                .initiator(TestInitiators.USER)
                .build();

        KickCharacterCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void equals_shouldDistinguishOnCharId() {
        KickCharacterCommand a = KickCharacterCommand.builder()
                .charId(1L)
                .initiator(TestInitiators.USER)
                .build();
        KickCharacterCommand b = KickCharacterCommand.builder()
                .charId(2L)
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnCloseClient() {
        KickCharacterCommand a = KickCharacterCommand.builder()
                .charId(1L)
                .closeClient(true)
                .initiator(TestInitiators.USER)
                .build();
        KickCharacterCommand b = KickCharacterCommand.builder()
                .charId(1L)
                .closeClient(false)
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnStaffNotes() {
        KickCharacterCommand a = KickCharacterCommand.builder()
                .charId(1L)
                .staffNotes("a")
                .initiator(TestInitiators.USER)
                .build();
        KickCharacterCommand b = KickCharacterCommand.builder()
                .charId(1L)
                .staffNotes("b")
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void hashCode_shouldMatchEquals() {
        KickCharacterCommand a = KickCharacterCommand.builder()
                .charId(1L)
                .closeClient(true)
                .staffNotes("note")
                .initiator(TestInitiators.USER)
                .build();
        KickCharacterCommand b = KickCharacterCommand.builder()
                .charId(1L)
                .closeClient(true)
                .staffNotes("note")
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_shouldExposeFields() {
        KickCharacterCommand cmd = KickCharacterCommand.builder()
                .charId(42L)
                .closeClient(true)
                .staffNotes("note")
                .initiator(TestInitiators.USER)
                .build();

        String s = cmd.toString();

        assertTrue(s.contains("42"));
        assertTrue(s.contains("true"));
        assertTrue(s.contains("note"));
    }

    @Test
    void implementsNxCommandMarker() {
        KickCharacterCommand cmd = KickCharacterCommand.builder()
                .charId(1L)
                .initiator(TestInitiators.USER)
                .build();

        assertInstanceOf(app.l2nx.gs.adapter.api.kafka.commands.NxCommand.class, cmd);
    }
}
