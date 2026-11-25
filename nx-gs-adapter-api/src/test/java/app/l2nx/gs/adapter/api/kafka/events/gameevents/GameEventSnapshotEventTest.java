package app.l2nx.gs.adapter.api.kafka.events.gameevents;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.schedule.RecurringSchedule;
import app.l2nx.gs.adapter.api.kafka.events.schedule.RecurringSlot;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameEventSnapshotEventTest {

    private static UUID id() {
        return UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");
    }

    private static GameEventEntry tvt() {
        return GameEventEntry.builder()
                .code("1")
                .name("Team vs Team")
                .enabled(true)
                .status(WellKnownGameEventStatuses.WAITING)
                .nextStartAt(Instant.parse("2026-05-30T20:00:00Z"))
                .metadata(Collections.singletonMap(
                        WellKnownGameEventMetadata.EVENT_KIND, WellKnownGameEventMetadata.EVENT_KIND_TVT))
                .build();
    }

    @Test
    void constructor_shouldRejectNullEventId() {
        assertThrows(
                NullPointerException.class,
                () -> GameEventSnapshotEvent.builder().build());
    }

    @Test
    void getEvents_shouldReturnEmptyList_whenBuilderOmits() {
        GameEventSnapshotEvent event =
                GameEventSnapshotEvent.builder().eventId(id()).build();

        assertTrue(event.getEvents().isEmpty());
        assertNull(event.getMetadata());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeEventsList() {
        List<GameEventEntry> source = new ArrayList<>();
        source.add(tvt());

        GameEventSnapshotEvent event =
                GameEventSnapshotEvent.builder().eventId(id()).events(source).build();

        source.add(tvt());

        assertEquals(1, event.getEvents().size());
        assertThrows(
                UnsupportedOperationException.class, () -> event.getEvents().add(tvt()));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        GameEventSnapshotEvent original = GameEventSnapshotEvent.builder()
                .eventId(id())
                .events(Collections.singletonList(tvt()))
                .metadata(Collections.singletonMap("source", "fighteventmanager"))
                .build();

        GameEventSnapshotEvent copy = original.toBuilder().build();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(id(), copy.getEventId());
        assertEquals(Collections.singletonList(tvt()), copy.getEvents());
        assertEquals(Collections.singletonMap("source", "fighteventmanager"), copy.getMetadata());
    }

    @Test
    void entry_tvt_shouldCarryCanonicalKind() {
        GameEventEntry entry = tvt();

        assertEquals(
                WellKnownGameEventMetadata.EVENT_KIND_TVT,
                entry.getMetadata().get(WellKnownGameEventMetadata.EVENT_KIND));
        assertTrue(entry.isEnabled());
        assertEquals(WellKnownGameEventStatuses.WAITING, entry.getStatus());
        assertEquals("1", entry.getCode());
    }

    @Test
    void entry_unscheduled_shouldCarryNullNextStart() {
        GameEventEntry disabled = GameEventEntry.builder()
                .code("2")
                .name("Capture the Flag")
                .enabled(false)
                .status(WellKnownGameEventStatuses.WAITING)
                .build();

        assertNull(disabled.getNextStartAt());
        assertNull(disabled.getMetadata());
    }

    @Test
    void entry_toBuilder_shouldRoundtripAllFields() {
        RecurringSchedule schedule = new RecurringSchedule(Collections.singletonList(
                new RecurringSlot(EnumSet.of(DayOfWeek.TUESDAY), OffsetTime.parse("19:00:00+03:00"), 30)));
        GameEventEntry original = tvt().toBuilder().schedule(schedule).build();
        GameEventEntry copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
        assertEquals("1", copy.getCode());
        assertEquals("Team vs Team", copy.getName());
        assertTrue(copy.isEnabled());
        assertEquals(WellKnownGameEventStatuses.WAITING, copy.getStatus());
        assertEquals(Instant.parse("2026-05-30T20:00:00Z"), copy.getNextStartAt());
        assertEquals(
                Collections.singletonMap(
                        WellKnownGameEventMetadata.EVENT_KIND, WellKnownGameEventMetadata.EVENT_KIND_TVT),
                copy.getMetadata());
        assertEquals(schedule, copy.getSchedule());
    }

    @Test
    void entry_metadata_shouldBeUnmodifiable() {
        GameEventEntry entry = tvt();
        assertThrows(
                UnsupportedOperationException.class, () -> entry.getMetadata().put("k", "v"));
    }
}
