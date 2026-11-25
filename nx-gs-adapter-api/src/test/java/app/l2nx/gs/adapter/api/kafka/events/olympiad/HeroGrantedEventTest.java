package app.l2nx.gs.adapter.api.kafka.events.olympiad;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import java.util.Collections;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class HeroGrantedEventTest {

    @Test
    void build_shouldLeaveOptionalFieldsNull_whenAbsent() {
        HeroGrantedEvent event = newBuilder().clanId(null).build();

        assertNull(event.getClanId());
        assertNull(event.getClazz());
    }

    @Test
    void build_shouldThrowNpe_whenEventIdIsNull() {
        HeroGrantedEvent.Builder b =
                HeroGrantedEvent.builder().characterId(268437521L).classId(88).olympiadCycle(7);

        assertThrows(NullPointerException.class, b::build);
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        HeroGrantedEvent original = HeroGrantedEvent.builder()
                .eventId(eventId)
                .characterId(268437521L)
                .classId(88)
                .clazz(CharacterClass.DUELIST)
                .clanId(101L)
                .olympiadCycle(7)
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        HeroGrantedEvent copy = original.toBuilder().build();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(268437521L, copy.getCharacterId());
        assertEquals(88, copy.getClassId());
        assertEquals(CharacterClass.DUELIST, copy.getClazz());
        assertEquals(Long.valueOf(101L), copy.getClanId());
        assertEquals(7, copy.getOlympiadCycle());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(HeroGrantedEvent.class)
                .withNonnullFields("eventId")
                .verify();
    }

    private static HeroGrantedEvent.Builder newBuilder() {
        return HeroGrantedEvent.builder()
                .eventId(UUID.randomUUID())
                .characterId(268437521L)
                .classId(88)
                .clanId(101L)
                .olympiadCycle(7);
    }
}
