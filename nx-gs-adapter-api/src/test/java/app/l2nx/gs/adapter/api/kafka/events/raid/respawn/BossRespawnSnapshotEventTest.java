package app.l2nx.gs.adapter.api.kafka.events.raid.respawn;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.schedule.RecurringSchedule;
import app.l2nx.gs.adapter.api.kafka.events.schedule.RecurringSlot;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetTime;
import java.util.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class BossRespawnSnapshotEventTest {

    private static UUID id() {
        return UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");
    }

    private static BossRespawnEntry deadBoss() {
        return BossRespawnEntry.builder()
                .npcId(29020)
                .level(75)
                .status(WellKnownBossStatuses.DEAD)
                .nextRespawnAt(Instant.parse("2026-06-01T12:00:00Z"))
                .build();
    }

    @Test
    void constructor_shouldRejectNullEventId() {
        assertThrows(
                NullPointerException.class,
                () -> BossRespawnSnapshotEvent.builder().build());
    }

    @Test
    void getBosses_shouldReturnEmptyList_whenBuilderOmits() {
        BossRespawnSnapshotEvent event =
                BossRespawnSnapshotEvent.builder().eventId(id()).build();

        assertTrue(event.getBosses().isEmpty());
        assertNull(event.getMetadata());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeBossesList() {
        List<BossRespawnEntry> source = new ArrayList<>();
        source.add(deadBoss());

        BossRespawnSnapshotEvent event =
                BossRespawnSnapshotEvent.builder().eventId(id()).bosses(source).build();

        source.add(deadBoss());

        assertEquals(1, event.getBosses().size());
        assertThrows(
                UnsupportedOperationException.class, () -> event.getBosses().add(deadBoss()));
    }

    @Test
    void getMetadata_shouldBeUnmodifiable_whenPresent() {
        BossRespawnSnapshotEvent event = BossRespawnSnapshotEvent.builder()
                .eventId(id())
                .metadata(Collections.singletonMap("source", "raidbossmanager"))
                .build();

        assertThrows(
                UnsupportedOperationException.class, () -> event.getMetadata().put("k", "v"));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BossRespawnSnapshotEvent original = BossRespawnSnapshotEvent.builder()
                .eventId(id())
                .bosses(Collections.singletonList(deadBoss()))
                .metadata(Collections.singletonMap("source", "raidbossmanager"))
                .build();

        BossRespawnSnapshotEvent copy = original.toBuilder().build();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(id(), copy.getEventId());
        assertEquals(Collections.singletonList(deadBoss()), copy.getBosses());
        assertEquals(Collections.singletonMap("source", "raidbossmanager"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(BossRespawnSnapshotEvent.class).verify();
    }

    @Test
    void entry_aliveBoss_shouldCarryNullRespawn() {
        BossRespawnEntry alive = BossRespawnEntry.builder()
                .npcId(25035)
                .level(40)
                .status(WellKnownBossStatuses.ALIVE)
                .build();

        assertEquals(WellKnownBossStatuses.ALIVE, alive.getStatus());
        assertNull(alive.getNextRespawnAt());
        assertNull(alive.getMetadata());
    }

    @Test
    void entry_metadata_shouldBeUnmodifiableAndDefensivelyCopied() {
        Map<String, String> source = new HashMap<>();
        source.put("zone", "antharas-lair");

        BossRespawnEntry entry = BossRespawnEntry.builder()
                .npcId(29019)
                .status(WellKnownBossStatuses.DEAD)
                .nextRespawnAt(Instant.parse("2026-06-02T00:00:00Z"))
                .metadata(source)
                .build();

        source.put("late", "value");

        assertEquals(1, entry.getMetadata().size());
        assertThrows(
                UnsupportedOperationException.class, () -> entry.getMetadata().put("k", "v"));
    }

    @Test
    void entry_toBuilder_shouldRoundtripAllFields() {
        RecurringSchedule schedule = new RecurringSchedule(Collections.singletonList(
                new RecurringSlot(EnumSet.of(DayOfWeek.TUESDAY), OffsetTime.parse("19:00:00+03:00"), 30)));
        BossRespawnEntry original = deadBoss().toBuilder()
                .metadata(Collections.singletonMap("zone", "antharas-lair"))
                .schedule(schedule)
                .build();
        BossRespawnEntry copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
        assertEquals(29020, copy.getNpcId());
        assertEquals(Integer.valueOf(75), copy.getLevel());
        assertEquals(WellKnownBossStatuses.DEAD, copy.getStatus());
        assertEquals(Instant.parse("2026-06-01T12:00:00Z"), copy.getNextRespawnAt());
        assertEquals(Collections.singletonMap("zone", "antharas-lair"), copy.getMetadata());
        assertEquals(schedule, copy.getSchedule());
    }
}
