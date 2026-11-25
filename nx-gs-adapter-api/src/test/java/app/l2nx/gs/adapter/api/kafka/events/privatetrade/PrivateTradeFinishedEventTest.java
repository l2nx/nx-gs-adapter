package app.l2nx.gs.adapter.api.kafka.events.privatetrade;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.Collections;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class PrivateTradeFinishedEventTest {

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        TradeParty a = TradeParty.builder()
                .characterId(268437521L)
                .items(Collections.singletonList(stub(57L, 1L, 1L, 1_000_000L)))
                .build();
        TradeParty b = TradeParty.builder()
                .characterId(268437522L)
                .items(Collections.singletonList(stub(6611L, 11L, 22L, 1L)))
                .build();

        PrivateTradeFinishedEvent original = PrivateTradeFinishedEvent.builder()
                .eventId(eventId)
                .tradeId(tradeId)
                .partyA(a)
                .partyB(b)
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        PrivateTradeFinishedEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(tradeId, copy.getTradeId());
        assertEquals(a, copy.getPartyA());
        assertEquals(b, copy.getPartyB());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(PrivateTradeFinishedEvent.class).verify();
    }

    @Test
    void giftTrade_shouldRepresentEmptySideAsEmptyItemsList() {
        TradeParty gifter = TradeParty.builder()
                .characterId(1L)
                .items(Collections.singletonList(stub(6611L, 11L, 22L, 1L)))
                .build();
        TradeParty receiver = TradeParty.builder().characterId(2L).build();

        PrivateTradeFinishedEvent event = PrivateTradeFinishedEvent.builder()
                .eventId(UUID.randomUUID())
                .tradeId(UUID.randomUUID())
                .partyA(gifter)
                .partyB(receiver)
                .build();

        assertEquals(1, event.getPartyA().getItems().size());
        assertTrue(event.getPartyB().getItems().isEmpty());
    }

    private static TradeItemMovement stub(long template, long itemId, long newItemId, long count) {
        return TradeItemMovement.builder()
                .item(ItemSnapshot.builder()
                        .itemId(itemId)
                        .itemTemplateId(template)
                        .build())
                .newItemId(newItemId)
                .count(count)
                .build();
    }
}
