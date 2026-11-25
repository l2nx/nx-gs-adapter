package app.l2nx.gs.adapter.api.kafka.sync.db.alliance;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class AllianceDbDtoTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        byte[] icon = new byte[] {0x11, 0x22, 0x33};
        AllianceDbDto alliance =
                AllianceDbDto.builder().id(42L).name("Crusaders").icon(icon).build();

        assertEquals(42L, alliance.getId());
        assertEquals("Crusaders", alliance.getName());
        assertArrayEquals(icon, alliance.getIcon());
        assertEquals(alliance, alliance.toBuilder().build());
    }

    @Test
    void icon_shouldBeNull_whenTenantDoesNotSyncCrests() {
        AllianceDbDto alliance = AllianceDbDto.builder().id(1L).name("X").build();
        assertNull(alliance.getIcon());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(AllianceDbDto.class).withNonnullFields("name").verify();
    }

    @Test
    void build_shouldThrowNpe_whenNameIsNull() {
        AllianceDbDto.Builder b = AllianceDbDto.builder().id(1L);

        assertThrows(NullPointerException.class, b::build);
    }
}
