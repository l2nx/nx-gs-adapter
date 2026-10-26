package app.l2nx.gs.adapter.api.spi.model.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

        @Test
        void build_shouldRejectABlankAccountName() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().accountName(" ").build());
        }

        @Test
        void build_shouldRejectASpotPoolLongerThanTheColumn() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().spotPool(repeat('p', 33)).build());
        }

        @Test
        void build_shouldRejectANonPositivePlannedMs() {
            assertThrows(
                    IllegalArgumentException.class, () -> valid().plannedMs(0L).build());
        }

        @Test
        void build_shouldRejectAHeadingOutsideTheCircle() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().originHeading(65536).build());
        }
    }

    @Test
    void offlineSession_shouldRequireEndedAtAndEndReasonTogether() {
        OfflineSession.Builder open = OfflineSession.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .activity(ActivityType.OFFLINE_FISHING)
                .accountName("acc")
                .spotPool("harbor")
                .startedAt(Instant.EPOCH);

        assertThrows(
                IllegalArgumentException.class,
                () -> open.endedAt(Instant.EPOCH).build());
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
