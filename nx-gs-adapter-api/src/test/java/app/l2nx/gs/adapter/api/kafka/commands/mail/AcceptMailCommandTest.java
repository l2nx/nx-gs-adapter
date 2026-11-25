package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AcceptMailCommandTest {

    private static final Instant DEADLINE = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");

    private static AcceptMailCommand.Builder valid() {
        return AcceptMailCommand.builder()
                .receiverCharacterId(1L)
                .mailId(10L)
                .expectedSenderCharacterId(2L)
                .expectedCodAdena(0L)
                .idempotencyKey(KEY)
                .deadline(DEADLINE)
                .initiator(TestInitiators.USER);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of(
                        "receiverCharacterId", IllegalArgumentException.class, (Consumer<AcceptMailCommand.Builder>)
                                b -> b.receiverCharacterId(null)),
                Arguments.of("mailId", IllegalArgumentException.class, (Consumer<AcceptMailCommand.Builder>)
                        b -> b.mailId(null)),
                Arguments.of("expectedSenderCharacterId", IllegalArgumentException.class, (Consumer<
                                AcceptMailCommand.Builder>)
                        b -> b.expectedSenderCharacterId(null)),
                Arguments.of("expectedCodAdena", IllegalArgumentException.class, (Consumer<AcceptMailCommand.Builder>)
                        b -> b.expectedCodAdena(null)),
                Arguments.of("idempotencyKey", NullPointerException.class, (Consumer<AcceptMailCommand.Builder>)
                        b -> b.idempotencyKey(null)),
                Arguments.of("deadline", NullPointerException.class, (Consumer<AcceptMailCommand.Builder>)
                        b -> b.deadline(null)),
                Arguments.of("expectedCodAdena", IllegalArgumentException.class, (Consumer<AcceptMailCommand.Builder>)
                        b -> b.expectedCodAdena(-1L)),
                Arguments.of("acceptedFeeAdena", IllegalArgumentException.class, (Consumer<AcceptMailCommand.Builder>)
                        b -> b.acceptedFeeAdena(-1L)));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.mail.AcceptMailCommandTest#invalidFields")
        void constructor_shouldReject_whenFieldInvalid(
                String field, Class<? extends Throwable> expected, Consumer<AcceptMailCommand.Builder> change) {
            AcceptMailCommand.Builder builder = valid();
            change.accept(builder);

            Throwable actual = assertThrows(expected, builder::build);
            assertTrue(actual.getMessage().contains(field));
        }

        @Test
        void constructor_shouldAccept_whenAcceptedFeeAbsentOrZero() {
            assertNull(valid().build().getAcceptedFeeAdena());
            assertEquals(Long.valueOf(0L), valid().acceptedFeeAdena(0L).build().getAcceptedFeeAdena());
        }
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        AcceptMailCommand original = valid().acceptedFeeAdena(500L)
                .expectedCodAdena(900L)
                .ownerVerified(true)
                .build();

        AcceptMailCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertTrue(copy.isOwnerVerified());
        assertEquals(1L, original.getReceiverCharacterId());
        assertEquals(10L, original.getMailId());
        assertEquals(2L, original.getExpectedSenderCharacterId());
        assertEquals(900L, original.getExpectedCodAdena());
        assertEquals(KEY, original.getIdempotencyKey());
        assertEquals(DEADLINE, original.getDeadline());
        assertEquals(TestInitiators.USER, original.getInitiator());
    }
}
