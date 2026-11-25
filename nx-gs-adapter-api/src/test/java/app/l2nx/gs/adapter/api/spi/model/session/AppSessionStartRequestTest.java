package app.l2nx.gs.adapter.api.spi.model.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppSessionStartRequestTest {

    private static AppSessionStartRequest.Builder valid() {
        return AppSessionStartRequest.builder()
                .idempotencyKey(UUID.randomUUID())
                .characterId(1)
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("acc")
                .spotPool("harbor")
                .spotId(3)
                .plannedMs(60_000L);
    }

    static Stream<Arguments> invalidSessions() {
        OfflineSession.Builder open = OfflineSession.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("acc")
                .spotPool("harbor")
                .startedAt(Instant.EPOCH);
        return Stream.of(
                invalid("blank account name", () -> valid().accountName(" ").build()),
                invalid(
                        "spot pool longer than the column",
                        () -> valid().spotPool(repeat('p', 33)).build()),
                invalid("plannedMs 0", () -> valid().plannedMs(0L).build()),
                invalid(
                        "heading outside the circle",
                        () -> valid().originHeading(65536).build()),
                invalid(
                        "offline session with endedAt and no endReason",
                        () -> open.endedAt(Instant.EPOCH).build()));
    }

    private static Arguments invalid(String name, Executable build) {
        return Arguments.of(name, build);
    }

    @Nested
    class Normalization {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void build_shouldTurnBlankOptionalsIntoNull(String blank) {
            AppSessionStartRequest actual =
                    valid().hwid(blank).initiatorIp(blank).typeState(blank).build();

            assertNull(actual.getHwid());
            assertNull(actual.getInitiatorIp());
            assertNull(actual.getTypeState());
        }

        @Test
        void build_shouldTrimStoredStrings() {
            AppSessionStartRequest actual =
                    valid().accountName(" acc ").hwid(" HW ").build();

            assertEquals("acc", actual.getAccountName());
            assertEquals("HW", actual.getHwid());
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest
        @EnumSource(
                value = ActivityType.class,
                names = {"OFFLINE_FISHING", "OFFLINE_PRIVATE_STORE"},
                mode = EnumSource.Mode.EXCLUDE)
        void build_shouldRejectAnActivityThatIsNoSessionType(ActivityType activity) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().activity(activity).build());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartRequestTest#invalidSessions")
        void build_shouldReject_whenValueIsInvalid(String name, Executable build) {
            assertThrows(IllegalArgumentException.class, build);
        }
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
