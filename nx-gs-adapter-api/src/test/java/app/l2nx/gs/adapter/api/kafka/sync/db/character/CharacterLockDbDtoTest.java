package app.l2nx.gs.adapter.api.kafka.sync.db.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class CharacterLockDbDtoTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        CharacterLockDbDto lock = CharacterLockDbDto.builder()
                .lockType(WellKnownCharacterLockTypes.IP)
                .lockValue("127.0.0.1")
                .build();

        assertEquals(WellKnownCharacterLockTypes.IP, lock.getLockType());
        assertEquals("127.0.0.1", lock.getLockValue());
        assertEquals(lock, lock.toBuilder().build());
    }

    @Test
    void lockValue_shouldBeNullable() {
        CharacterLockDbDto lock = CharacterLockDbDto.builder()
                .lockType(WellKnownCharacterLockTypes.HWID)
                .build();

        assertNull(lock.getLockValue());
    }

    @Test
    void build_shouldThrowNpe_whenLockTypeIsNull() {
        CharacterLockDbDto.Builder b = CharacterLockDbDto.builder().lockValue("x");

        assertThrows(NullPointerException.class, b::build);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterLockDbDto.class)
                .withNonnullFields("lockType")
                .verify();
    }
}
