package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
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
                .receiverCharId(1L)
                .mailId(10L)
                .expectedSenderCharId(2L)
                .expectedCodAdena(0L)
                .idempotencyKey(KEY)
                .deadline(DEADLINE);
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                Arguments.of("receiverCharId", (Consumer<AcceptMailCommand.Builder>) b -> b.receiverCharId(null)),
                Arguments.of("mailId", (Consumer<AcceptMailCommand.Builder>) b -> b.mailId(null)),
                Arguments.of("expectedSenderCharId", (Consumer<AcceptMailCommand.Builder>)
                        b -> b.expectedSenderCharId(null)),
                Arguments.of("expectedCodAdena", (Consumer<AcceptMailCommand.Builder>) b -> b.expectedCodAdena(null)));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.mail.AcceptMailCommandTest#missingRequired")
        void constructor_shouldReject_whenRequiredFieldNull(String field, Consumer<AcceptMailCommand.Builder> nullOut) {
            AcceptMailCommand.Builder builder = valid();
            nullOut.accept(builder);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains(field));
        }

        @Test
        void constructor_shouldReject_whenIdempotencyKeyOrDeadlineNull() {
            assertThrows(
                    NullPointerException.class,
                    () -> valid().idempotencyKey(null).build());
            assertThrows(
                    NullPointerException.class, () -> valid().deadline(null).build());
        }

        @Test
        void constructor_shouldReject_whenCodNegative() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().expectedCodAdena(-1L).build());
        }

        @Test
        void constructor_shouldReject_whenAcceptedFeeNegative() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().acceptedFeeAdena(-1L).build());
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

        assertEquals(original, original.toBuilder().build());
        assertEquals(original.hashCode(), original.toBuilder().build().hashCode());
    }

    @Test
    void command_shouldBeOwnerVerifiedNxCommand() {
        AcceptMailCommand command = valid().ownerVerified(true).build();

        assertTrue(assertInstanceOf(OwnerVerified.class, command).isOwnerVerified());
        assertInstanceOf(NxCommand.class, command);
    }
}
