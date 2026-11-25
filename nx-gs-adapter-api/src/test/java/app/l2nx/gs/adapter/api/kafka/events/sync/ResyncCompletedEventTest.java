package app.l2nx.gs.adapter.api.kafka.events.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResyncCompletedEventTest {

    private static final UUID EVENT_ID = UUID.fromString("018f0000-0000-7000-8000-00000000000a");
    private static final UUID RESYNC_ID = UUID.fromString("018f0000-0000-7000-8000-00000000000b");
    private static final Instant STARTED = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-06-12T10:05:00Z");

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        ResyncCompletedEvent original = ResyncCompletedEvent.builder()
                .eventId(EVENT_ID)
                .resyncId(RESYNC_ID)
                .entityName("item")
                .cycleStartedAt(STARTED)
                .completedAt(COMPLETED)
                .build();

        assertEquals(EVENT_ID, original.getEventId());
        assertEquals(RESYNC_ID, original.getResyncId());
        assertEquals("item", original.getEntityName());
        assertEquals(STARTED, original.getCycleStartedAt());
        assertEquals(COMPLETED, original.getCompletedAt());
        assertEquals(original, original.toBuilder().build());
    }

    private static Stream<Arguments> constructorsWithNull() {
        return Stream.of(
                Arguments.of("eventId", (Executable)
                        () -> new ResyncCompletedEvent(null, RESYNC_ID, "item", STARTED, COMPLETED)),
                Arguments.of("resyncId", (Executable)
                        () -> new ResyncCompletedEvent(EVENT_ID, null, "item", STARTED, COMPLETED)),
                Arguments.of("entityName", (Executable)
                        () -> new ResyncCompletedEvent(EVENT_ID, RESYNC_ID, null, STARTED, COMPLETED)),
                Arguments.of("cycleStartedAt", (Executable)
                        () -> new ResyncCompletedEvent(EVENT_ID, RESYNC_ID, "item", null, COMPLETED)),
                Arguments.of("completedAt", (Executable)
                        () -> new ResyncCompletedEvent(EVENT_ID, RESYNC_ID, "item", STARTED, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constructorsWithNull")
    void constructor_shouldRejectNull(String field, Executable constructor) {
        assertThrows(NullPointerException.class, constructor);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ResyncCompletedEvent.class)
                .withNonnullFields("eventId", "resyncId", "entityName", "cycleStartedAt", "completedAt")
                .verify();
    }
}
