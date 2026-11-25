package app.l2nx.gs.adapter.api.kafka.events.raid.kill;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class RaidActorTest {

    @Test
    void nullableFields_shouldBeNullByDefault() {
        RaidActor a = RaidActor.builder().characterId(42L).build();

        assertNull(a.getClanId());
        assertNull(a.getAllyId());
        assertNull(a.getPartyId());
        assertNull(a.getCommandChannelId());
        assertEquals(0L, a.getDamageDealt());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID partyId = UUID.randomUUID();
        UUID ccId = UUID.randomUUID();
        RaidActor original = RaidActor.builder()
                .characterId(268415943L)
                .clanId(268440117L)
                .allyId(268455901L)
                .partyId(partyId)
                .commandChannelId(ccId)
                .partyLeader(true)
                .commandChannelLeader(true)
                .damageDealt(145820L)
                .build();

        RaidActor copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(268415943L, copy.getCharacterId());
        assertEquals(Long.valueOf(268440117L), copy.getClanId());
        assertEquals(Long.valueOf(268455901L), copy.getAllyId());
        assertEquals(partyId, copy.getPartyId());
        assertEquals(ccId, copy.getCommandChannelId());
        assertTrue(copy.isPartyLeader());
        assertTrue(copy.isCommandChannelLeader());
        assertEquals(145820L, copy.getDamageDealt());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(RaidActor.class).verify();
    }
}
