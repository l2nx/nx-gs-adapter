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

    private static ChatMessageEvent.Builder base() {
        return ChatMessageEvent.builder()
                .eventId(UUID.randomUUID())
                .characterId(268437521L)
                .characterName("Vasya")
                .channel(ChatChannel.GENERAL)
                .text("hi");
    }

    @Nested
    class NewFields {

        @Test
        void builder_shouldCarryNewFields() {
            ChatMessageEvent event = base().senderDisplayName("Vasya*")
                    .recipientCharacterIds(Arrays.asList(1L, 2L))
                    .items(Collections.singletonList(snapshot()))
                    .build();

            assertEquals("Vasya*", event.getSenderDisplayName());
            assertEquals(Arrays.asList(1L, 2L), event.getRecipientCharacterIds());
            assertEquals(Collections.singletonList(snapshot()), event.getItems());
        }

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

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            ChatMessageEvent original = base().senderDisplayName("Vasya*")
                    .recipientCharacterIds(Arrays.asList(1L, 2L))
                    .items(Collections.singletonList(snapshot()))
                    .build();

            assertEquals(original, original.toBuilder().build());
        }

        @Test
        void equals_shouldDistinguishSenderDisplayName() {
            UUID id = UUID.randomUUID();
            ChatMessageEvent a = base().eventId(id).senderDisplayName("Vasya").build();
            ChatMessageEvent b = base().eventId(id).senderDisplayName("Vasya*").build();

            assertNotEquals(a, b);
        }
    }

    @Nested
    class ClanFields {

        @Test
        void builder_shouldCarryClanAndAlliance() {
            ChatMessageEvent event = base().clanId(7L).allianceId(3L).build();

            assertEquals(7L, event.getClanId());
            assertEquals(3L, event.getAllianceId());
            assertEquals(event, event.toBuilder().build());
        }

        @Test
        void equals_shouldDistinguishClanId() {
            UUID id = UUID.randomUUID();

            assertNotEquals(
                    base().eventId(id).clanId(1L).build(),
                    base().eventId(id).clanId(2L).build());
        }

        @Test
        void roundtrip_shouldPreserveClanAndAlliance() {
            ChatMessageEvent original = base().clanId(7L).allianceId(3L).build();

            assertEquals(original, GSON.fromJson(GSON.toJson(original), ChatMessageEvent.class));
        }

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
        void roundtrip_shouldPreserveNewFields() {
            ChatMessageEvent original = base().senderDisplayName("Vasya*")
                    .recipientCharacterIds(Arrays.asList(1L, 2L))
                    .items(Collections.singletonList(snapshot()))
                    .build();

            ChatMessageEvent parsed = GSON.fromJson(GSON.toJson(original), ChatMessageEvent.class);

            assertEquals(original, parsed);
        }

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
