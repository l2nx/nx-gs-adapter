package app.l2nx.gs.adapter.api.kafka.events.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MailAcceptedEventTest {

    private static MailAcceptedEvent.Builder valid() {
        return MailAcceptedEvent.builder()
                .eventId(UUID.randomUUID())
                .mailId(42L)
                .claimedByCharacterId(268437521L);
    }

    private static Stream<MailAcceptedEvent.Builder> withoutAttachments() {
        return Stream.of(valid(), valid().attachments(null));
    }

    @ParameterizedTest
    @MethodSource("withoutAttachments")
    void getAttachments_shouldReturnEmptyList_whenBuilderOmitsOrPassesNull(MailAcceptedEvent.Builder builder) {
        assertTrue(builder.build().getAttachments().isEmpty());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeAttachmentsList() {
        List<MailItemMovement> source = new ArrayList<MailItemMovement>();
        source.add(stub(1L, 2L));

        MailAcceptedEvent event = valid().attachments(source).build();

        source.add(stub(3L, 4L));

        assertEquals(1, event.getAttachments().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.getAttachments().add(stub(3L, 4L)));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        MailAcceptedEvent original = MailAcceptedEvent.builder()
                .eventId(eventId)
                .mailId(42L)
                .claimedByCharacterId(268437521L)
                .attachments(Arrays.asList(stub(1L, 2L), stub(3L, 4L)))
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        MailAcceptedEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(42L, copy.getMailId());
        assertEquals(268437521L, copy.getClaimedByCharacterId());
        assertEquals(Arrays.asList(stub(1L, 2L), stub(3L, 4L)), copy.getAttachments());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MailAcceptedEvent.class).verify();
    }

    private static MailItemMovement stub(long itemId, long newItemId) {
        return MailItemMovement.builder()
                .item(ItemSnapshot.builder().itemId(itemId).itemTemplateId(57L).build())
                .newItemId(newItemId)
                .count(1L)
                .build();
    }
}
