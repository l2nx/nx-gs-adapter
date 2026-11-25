package app.l2nx.gs.adapter.api.kafka.events.chat;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.ItemAugmentation;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChatMessageEventTest {

    private static final Gson GSON = new Gson();

    private static ItemSnapshot snapshot() {
        return ItemSnapshot.builder()
                .itemId(268500001L)
                .itemTemplateId(6577L)
                .enchantLevel(7)
                .attributes(Collections.singletonMap(Attribute.FIRE, 150))
                .augmentation(new ItemAugmentation(16341, 18000))
                .count(1L)
                .build();
    }

    private static final UUID EVENT_ID = UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");

    private static ChatMessageEvent.Builder base() {
        return ChatMessageEvent.builder()
                .eventId(EVENT_ID)
                .characterId(268437521L)
                .characterName("Vasya")
                .channel(ChatChannel.GENERAL)
                .text("hi");
    }

    @Test
    void roundtrip_shouldPreserveAllFields() {
        ChatMessageEvent minimal = base().build();
        ChatMessageEvent original = base().targetCharacterId(268437522L)
                .targetCharacterName("Petya")
                .metadata(Collections.singletonMap("k", "v"))
                .senderDisplayName("Vasya*")
                .recipientCharacterIds(Arrays.asList(1L, 2L))
                .items(Collections.singletonList(snapshot()))
                .clanId(7L)
                .allianceId(3L)
                .build();

        assertEquals("Vasya*", original.getSenderDisplayName());
        assertEquals(Arrays.asList(1L, 2L), original.getRecipientCharacterIds());
        assertEquals(Collections.singletonList(snapshot()), original.getItems());
        assertEquals(7L, original.getClanId());
        assertEquals(3L, original.getAllianceId());
        assertEquals(original, original.toBuilder().build());
        assertEquals(original, GSON.fromJson(GSON.toJson(original), ChatMessageEvent.class));
        assertEquals(minimal, minimal.toBuilder().build());
        assertEquals(minimal, GSON.fromJson(GSON.toJson(minimal), ChatMessageEvent.class));
        assertEquals(268437521L, original.getCharacterId());
        assertEquals("Vasya", original.getCharacterName());
        assertEquals(ChatChannel.GENERAL.name(), original.getChannel());
        assertEquals(268437522L, original.getTargetCharacterId());
        assertEquals("Petya", original.getTargetCharacterName());
        assertEquals(Collections.singletonMap("k", "v"), original.getMetadata());
        assertEquals(EVENT_ID, original.getEventId());
        assertEquals("hi", original.getText());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ChatMessageEvent.class).verify();
    }

    @Nested
    class NewFields {

        @Test
        void getters_shouldReturnNull_whenOmitted() {
            ChatMessageEvent event = base().build();

            assertNull(event.getSenderDisplayName());
            assertNull(event.getRecipientCharacterIds());
            assertNull(event.getItems());
        }

        @Test
        void lists_shouldBeDefensivelyCopiedAndUnmodifiable() {
            List<Long> recipients = new ArrayList<Long>(Arrays.asList(1L, 2L));
            ChatMessageEvent event = base().recipientCharacterIds(recipients)
                    .items(new ArrayList<ItemSnapshot>(Collections.singletonList(snapshot())))
                    .build();

            recipients.add(3L);

            assertEquals(2, event.getRecipientCharacterIds().size());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> event.getRecipientCharacterIds().add(4L));
            assertThrows(
                    UnsupportedOperationException.class, () -> event.getItems().add(snapshot()));
        }
    }

    @Nested
    class ClanFields {

        @Test
        void fromJson_shouldReadClanFieldsAsNull_whenAbsent() {
            String old = "{\"eventId\":\"" + UUID.randomUUID()
                    + "\",\"characterId\":5,\"characterName\":\"Vasya\",\"channel\":\"CLAN\",\"text\":\"hi\"}";

            ChatMessageEvent parsed = GSON.fromJson(old, ChatMessageEvent.class);

            assertNull(parsed.getClanId());
            assertNull(parsed.getAllianceId());
        }
    }

    @Nested
    class GsonRoundtrip {

        @Test
        void fromJson_shouldReadNewFieldsAsNull_whenOldPayload() {
            String old = "{\"eventId\":\"" + UUID.randomUUID()
                    + "\",\"characterId\":5,\"characterName\":\"Vasya\",\"channel\":\"CLAN\",\"text\":\"hi\"}";

            ChatMessageEvent parsed = GSON.fromJson(old, ChatMessageEvent.class);

            assertEquals("hi", parsed.getText());
            assertNull(parsed.getSenderDisplayName());
            assertNull(parsed.getRecipientCharacterIds());
            assertNull(parsed.getItems());
        }
    }
}
