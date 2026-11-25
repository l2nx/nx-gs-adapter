package app.l2nx.gs.adapter.api.kafka.events.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RecurringSlotTest {

    private static Stream<Arguments> slots() {
        return Stream.of(
                Arguments.of(EnumSet.of(DayOfWeek.TUESDAY), OffsetTime.parse("19:00:00+03:00")),
                Arguments.of(EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), OffsetTime.parse("21:45:00+03:00")));
    }

    @ParameterizedTest
    @MethodSource("slots")
    void builder_shouldBuildEqualToConstructor_andRoundtrip(Set<DayOfWeek> daysOfWeek, OffsetTime time) {
        RecurringSlot built = RecurringSlot.builder()
                .daysOfWeek(daysOfWeek)
                .time(time)
                .jitterMinutes(30)
                .build();

        RecurringSlot ctor = new RecurringSlot(daysOfWeek, time, 30);

        assertEquals(ctor, built);
        assertEquals(ctor.hashCode(), built.hashCode());
        assertEquals(daysOfWeek, built.getDaysOfWeek());
        assertEquals(time, built.getTime());
        assertEquals(30, built.getJitterMinutes());
        assertEquals(ctor, ctor.toBuilder().build());
    }

    @Test
    void daysOfWeek_shouldBeEmptySet_whenNull() {
        RecurringSlot slot = new RecurringSlot(null, OffsetTime.parse("12:00:00Z"), 0);

        assertNotNull(slot.getDaysOfWeek());
        assertTrue(slot.getDaysOfWeek().isEmpty());
    }

    @Test
    void daysOfWeek_shouldBeAnUnmodifiableCopy() {
        EnumSet<DayOfWeek> source = EnumSet.of(DayOfWeek.MONDAY);
        RecurringSlot slot = new RecurringSlot(source, OffsetTime.parse("12:00:00Z"), 0);

        source.add(DayOfWeek.FRIDAY);

        assertEquals(EnumSet.of(DayOfWeek.MONDAY), slot.getDaysOfWeek());
        assertThrows(
                UnsupportedOperationException.class, () -> slot.getDaysOfWeek().add(DayOfWeek.SUNDAY));
    }
}
