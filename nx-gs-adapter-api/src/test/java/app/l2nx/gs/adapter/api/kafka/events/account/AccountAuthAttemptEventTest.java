package app.l2nx.gs.adapter.api.kafka.events.account;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AccountAuthAttemptEventTest {

    private static final String SAMPLE_EVENT_ID = UUID.randomUUID().toString();
    private static final String SAMPLE_SERVER_ID = UUID.randomUUID().toString();
    private static final Instant SAMPLE_AT = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        AccountAuthAttemptEvent event = AccountAuthAttemptEvent.builder()
                .eventId(SAMPLE_EVENT_ID)
                .serverId(SAMPLE_SERVER_ID)
                .accountName("olya")
                .clientIp("10.0.0.1")
                .hwid("hw-abc")
                .outcome(AuthOutcomes.WRONG_PASSWORD)
                .attemptedAt(SAMPLE_AT)
                .failureDetail("third try")
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        assertEquals(SAMPLE_EVENT_ID, event.getEventId());
        assertEquals(SAMPLE_SERVER_ID, event.getServerId());
        assertEquals("olya", event.getAccountName());
        assertEquals("10.0.0.1", event.getClientIp());
        assertEquals("hw-abc", event.getHwid());
        assertEquals("WRONG_PASSWORD", event.getOutcome());
        assertEquals(SAMPLE_AT, event.getAttemptedAt());
        assertEquals("third try", event.getFailureDetail());
        assertEquals(Collections.singletonMap("k", "v"), event.getMetadata());
        assertEquals(event, event.toBuilder().build());
        assertEquals(minimal().build(), minimal().build().toBuilder().build());
    }

    @Test
    void getters_shouldReturnNull_whenOptionalFieldsOmitted() {
        AccountAuthAttemptEvent event = minimal().outcome(AuthOutcomes.SUCCESS).build();

        assertNull(event.getHwid());
        assertNull(event.getFailureDetail());
    }

    static Stream<Arguments> missingRequiredFields() {
        return Stream.of(
                Arguments.of("eventId", minimal().eventId(null)),
                Arguments.of("serverId", minimal().serverId(null)),
                Arguments.of("accountName", minimal().accountName(null)),
                Arguments.of("clientIp", minimal().clientIp(null)),
                Arguments.of("outcome", minimal().outcome(null)),
                Arguments.of("attemptedAt", minimal().attemptedAt(null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequiredFields")
    void constructor_shouldReject_whenRequiredFieldNull(String name, AccountAuthAttemptEvent.Builder builder) {
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(AccountAuthAttemptEvent.class).verify();
    }

    @Test
    void getOutcome_shouldAcceptUnknownString() {
        AccountAuthAttemptEvent event =
                minimal().outcome("CORE_VERSION_MISMATCH").build();

        assertEquals("CORE_VERSION_MISMATCH", event.getOutcome());
    }

    private static AccountAuthAttemptEvent.Builder minimal() {
        return AccountAuthAttemptEvent.builder()
                .eventId(SAMPLE_EVENT_ID)
                .serverId(SAMPLE_SERVER_ID)
                .accountName("acc")
                .clientIp("1.1.1.1")
                .outcome(AuthOutcomes.SUCCESS)
                .attemptedAt(SAMPLE_AT);
    }
}
