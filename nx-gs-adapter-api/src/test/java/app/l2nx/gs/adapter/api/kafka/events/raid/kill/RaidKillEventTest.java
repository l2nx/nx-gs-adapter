package app.l2nx.gs.adapter.api.kafka.events.raid.kill;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaidKillEventTest {

    @Test
    void constructor_shouldThrow_whenEventIdNull() {
        assertThrows(
                NullPointerException.class,
                () -> RaidKillEvent.builder().bossNpcId(29028).build());
    }

    @Test
    void getParticipantsAndDrops_shouldReturnEmptyList_whenBuilderOmits() {
        RaidKillEvent event = RaidKillEvent.builder()
                .eventId(UUID.randomUUID())
                .bossNpcId(29028)
                .build();

        assertTrue(event.getParticipants().isEmpty());
        assertTrue(event.getDrops().isEmpty());
    }

    @Test
    void nullableFields_shouldBeNullByDefault() {
        RaidKillEvent event = RaidKillEvent.builder()
                .eventId(UUID.randomUUID())
                .bossNpcId(29028)
                .build();

        assertNull(event.getBossName());
        assertNull(event.getBossLevel());
        assertNull(event.getInstanceId());
        assertNull(event.getLastHit());
        assertNull(event.getDropOwner());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeParticipantsList() {
        List<RaidActor> source = new ArrayList<>();
        source.add(RaidActor.builder().characterId(1L).damageDealt(100L).build());

        RaidKillEvent event = RaidKillEvent.builder()
                .eventId(UUID.randomUUID())
                .bossNpcId(29028)
                .participants(source)
                .build();

        source.add(RaidActor.builder().characterId(2L).damageDealt(200L).build());

        assertEquals(1, event.getParticipants().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.getParticipants().add(null));
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeDropsList() {
        List<RaidDropItem> source = new ArrayList<>();
        source.add(RaidDropItem.builder().itemTemplateId(57L).count(1L).build());

        RaidKillEvent event = RaidKillEvent.builder()
                .eventId(UUID.randomUUID())
                .bossNpcId(29028)
                .drops(source)
                .build();

        source.add(RaidDropItem.builder().itemTemplateId(6660L).count(1L).build());

        assertEquals(1, event.getDrops().size());
        assertThrows(UnsupportedOperationException.class, () -> event.getDrops().add(null));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        RaidActor killer = RaidActor.builder()
                .characterId(268415943L)
                .clanId(268440117L)
                .partyId(UUID.randomUUID())
                .commandChannelId(UUID.randomUUID())
                .damageDealt(145820L)
                .build();
        RaidActor dropOwner =
                RaidActor.builder().characterId(268415944L).damageDealt(1L).build();
        RaidDropItem drop =
                RaidDropItem.builder().itemTemplateId(6660L).count(1L).build();
        RaidKillEvent original = RaidKillEvent.builder()
                .eventId(eventId)
                .bossNpcId(29028)
                .bossName("Valakas")
                .bossLevel(85)
                .instanceId(7L)
                .lastHit(killer)
                .dropOwner(dropOwner)
                .participants(Collections.singletonList(killer))
                .drops(Collections.singletonList(drop))
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        RaidKillEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(29028, copy.getBossNpcId());
        assertEquals("Valakas", copy.getBossName());
        assertEquals(Integer.valueOf(85), copy.getBossLevel());
        assertEquals(Long.valueOf(7L), copy.getInstanceId());
        assertEquals(killer, copy.getLastHit());
        assertEquals(dropOwner, copy.getDropOwner());
        assertEquals(Collections.singletonList(killer), copy.getParticipants());
        assertEquals(Collections.singletonList(drop), copy.getDrops());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }
}
