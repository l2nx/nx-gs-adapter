package app.l2nx.gs.adapter.api.kafka.events.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MailReturnedEventTest {

    private static MailReturnedEvent.Builder valid() {
        return MailReturnedEvent.builder()
                .eventId(UUID.randomUUID())
                .mailId(42L)
                .returnedToSenderId(268437520L);
    }

    private static Stream<MailReturnedEvent.Builder> withoutAttachments() {
        return Stream.of(valid(), valid().attachments(null));
    }

    @ParameterizedTest
    @MethodSource("withoutAttachments")
    void getAttachments_shouldReturnEmptyList_whenBuilderOmitsOrPassesNull(MailReturnedEvent.Builder builder) {
        assertTrue(builder.build().getAttachments().isEmpty());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeAttachmentsList() {
        List<MailItemMovement> source = new ArrayList<MailItemMovement>();
        source.add(stub(1L));

        MailReturnedEvent event = valid().attachments(source).build();

        source.add(stub(2L));

        assertEquals(1, event.getAttachments().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.getAttachments().add(stub(2L)));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        MailReturnedEvent original = MailReturnedEvent.builder()
                .eventId(eventId)
                .mailId(42L)
                .returnedToSenderId(268437520L)
                .attachments(Arrays.asList(stub(1L), stub(2L)))
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        MailReturnedEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(42L, copy.getMailId());
        assertEquals(268437520L, copy.getReturnedToSenderId());
        assertEquals(Arrays.asList(stub(1L), stub(2L)), copy.getAttachments());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MailReturnedEvent.class).verify();
    }

    private static MailItemMovement stub(long itemId) {
        return MailItemMovement.builder()
                .item(ItemSnapshot.builder().itemId(itemId).itemTemplateId(57L).build())
                .newItemId(itemId)
                .count(1L)
                .build();
    }
}
