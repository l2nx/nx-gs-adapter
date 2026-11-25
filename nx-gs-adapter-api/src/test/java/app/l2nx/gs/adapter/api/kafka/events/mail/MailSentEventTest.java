package app.l2nx.gs.adapter.api.kafka.events.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MailSentEventTest {

    private static MailSentEvent.Builder valid() {
        return MailSentEvent.builder()
                .eventId(UUID.randomUUID())
                .mailId(42L)
                .senderCharacterId(0L)
                .receiverCharacterId(268437521L)
                .subject("Reward")
                .expiresAt(Instant.parse("2026-06-01T12:00:00Z"));
    }

    private static Stream<MailSentEvent.Builder> withoutAttachments() {
        return Stream.of(valid(), valid().attachments(null));
    }

    @ParameterizedTest
    @MethodSource("withoutAttachments")
    void getAttachments_shouldReturnEmptyList_whenBuilderOmitsOrPassesNull(MailSentEvent.Builder builder) {
        assertTrue(builder.build().getAttachments().isEmpty());
    }

    @Test
    void getSenderName_shouldBeNullable_forPlayerToPlayerMail() {
        MailSentEvent event =
                valid().senderCharacterId(268437520L).subject("Hi").build();

        assertNull(event.getSenderName());
    }

    @Test
    void getBody_shouldBeNullable_forSystemMailWithoutBody() {
        MailSentEvent event = valid().senderName("System").build();

        assertNull(event.getBody());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeAttachmentsList() {
        List<MailItemMovement> source = new ArrayList<MailItemMovement>();
        source.add(stub());

        MailSentEvent event = valid().attachments(source).build();

        source.add(stub());

        assertEquals(1, event.getAttachments().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.getAttachments().add(stub()));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        MailSentEvent original = MailSentEvent.builder()
                .eventId(eventId)
                .mailId(42L)
                .senderCharacterId(268437520L)
                .senderName("Olya")
                .receiverCharacterId(268437521L)
                .subject("Subject")
                .body("Body")
                .expiresAt(Instant.parse("2026-06-01T12:00:00Z"))
                .codAmount(1_000_000L)
                .attachments(Arrays.asList(stub(), stub()))
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        MailSentEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(42L, copy.getMailId());
        assertEquals(268437520L, copy.getSenderCharacterId());
        assertEquals("Olya", copy.getSenderName());
        assertEquals(268437521L, copy.getReceiverCharacterId());
        assertEquals("Subject", copy.getSubject());
        assertEquals("Body", copy.getBody());
        assertEquals(Instant.parse("2026-06-01T12:00:00Z"), copy.getExpiresAt());
        assertEquals(1_000_000L, copy.getCodAmount());
        assertEquals(Arrays.asList(stub(), stub()), copy.getAttachments());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MailSentEvent.class).verify();
    }

    private static MailItemMovement stub() {
        return MailItemMovement.builder()
                .item(ItemSnapshot.builder().itemId(1L).itemTemplateId(57L).build())
                .newItemId(1L)
                .count(1L)
                .build();
    }
}
