package app.l2nx.gs.adapter.api.kafka.events.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MailDeletedEventTest {

    private static final UUID EVENT_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");

    @ParameterizedTest
    @CsvSource({"42, SENDER", "7, RECEIVER"})
    void builder_shouldMatchConstructorAndRoundtrip(long mailId, MailDeletionSide side) {
        MailDeletedEvent fromBuilder = MailDeletedEvent.builder()
                .eventId(EVENT_ID)
                .mailId(mailId)
                .side(side)
                .build();
        MailDeletedEvent fromCtor = new MailDeletedEvent(EVENT_ID, mailId, side);

        assertEquals(fromCtor, fromBuilder);
        assertEquals(fromCtor.hashCode(), fromBuilder.hashCode());

        MailDeletedEvent copy = fromCtor.toBuilder().build();

        assertEquals(fromCtor, copy);
        assertEquals(EVENT_ID, copy.getEventId());
        assertEquals(mailId, copy.getMailId());
        assertEquals(side, copy.getSide());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MailDeletedEvent.class).verify();
    }

    @Test
    void readEvent_builder_shouldMatchConstructor() {
        MailReadEvent fromBuilder =
                MailReadEvent.builder().eventId(EVENT_ID).mailId(9L).build();
        MailReadEvent fromCtor = new MailReadEvent(EVENT_ID, 9L);

        assertEquals(fromCtor, fromBuilder);
        assertEquals(fromCtor.hashCode(), fromBuilder.hashCode());
        assertEquals(fromCtor, fromCtor.toBuilder().build());
        assertEquals(EVENT_ID, fromBuilder.getEventId());
        assertEquals(9L, fromBuilder.getMailId());
    }
}
