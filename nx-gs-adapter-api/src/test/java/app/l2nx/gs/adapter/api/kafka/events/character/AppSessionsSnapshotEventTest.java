package app.l2nx.gs.adapter.api.kafka.events.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppSessionsSnapshotEventTest {

    private static final UUID EVENT_ID = UUID.fromString("0192f000-0000-7000-8000-000000000001");
    private static final Instant TAKEN_AT = Instant.parse("2026-10-26T10:00:00Z");

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of(
                        "a session id is null",
                        AppSessionsSnapshotEvent.builder()
                                .eventId(EVENT_ID)
                                .takenAt(TAKEN_AT)
                                .sessionIds(Arrays.asList(UUID.randomUUID(), null))),
                Arguments.of(
                        "takenAt missing", AppSessionsSnapshotEvent.builder().eventId(EVENT_ID)),
                Arguments.of(
                        "eventId missing", AppSessionsSnapshotEvent.builder().takenAt(TAKEN_AT)));
    }

    @Nested
    class Construct {

        @Test
        void build_shouldGiveAnEmptyList_whenSessionIdsAreNull() {
            AppSessionsSnapshotEvent actual = AppSessionsSnapshotEvent.builder()
                    .eventId(EVENT_ID)
                    .takenAt(TAKEN_AT)
                    .build();

            assertTrue(actual.getSessionIds().isEmpty());
        }

        @Test
        void build_shouldCopyTheSessionIds() {
            List<UUID> ids = new ArrayList<UUID>(Arrays.asList(UUID.randomUUID(), UUID.randomUUID()));
            AppSessionsSnapshotEvent actual = AppSessionsSnapshotEvent.builder()
                    .eventId(EVENT_ID)
                    .takenAt(TAKEN_AT)
                    .sessionIds(ids)
                    .build();
            List<UUID> expected = new ArrayList<UUID>(ids);

            ids.clear();

            assertEquals(expected, actual.getSessionIds());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> actual.getSessionIds().clear());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.events.character.AppSessionsSnapshotEventTest#invalidEvents")
        void build_shouldReject_whenEventInvalid(String name, AppSessionsSnapshotEvent.Builder builder) {
            assertThrows(NullPointerException.class, builder::build);
        }
    }
}
