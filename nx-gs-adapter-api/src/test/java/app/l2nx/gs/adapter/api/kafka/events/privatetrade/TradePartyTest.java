package app.l2nx.gs.adapter.api.kafka.events.privatetrade;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradePartyTest {

    @Test
    void getItems_shouldReturnEmptyList_whenBuilderOmits() {
        TradeParty party = TradeParty.builder().characterId(268437521L).build();

        assertTrue(party.getItems().isEmpty());
    }

    @Test
    void getItems_shouldReturnEmptyList_whenBuilderPassesNull() {
        TradeParty party = TradeParty.builder().characterId(268437521L).items(null).build();

        assertTrue(party.getItems().isEmpty());
    }

    @Test
    void getItems_shouldBeUnmodifiable() {
        TradeParty party = TradeParty.builder()
                .characterId(268437521L)
                .items(Collections.singletonList(stub(1L)))
                .build();

        assertThrows(UnsupportedOperationException.class, () -> party.getItems().add(stub(2L)));
    }

    @Test
    void constructor_shouldDefensivelyCopyItemsList() {
        List<TradeItemMovement> source = new ArrayList<TradeItemMovement>();
        source.add(stub(1L));

        TradeParty party = TradeParty.builder().characterId(268437521L).items(source).build();

        source.add(stub(2L));

        assertEquals(1, party.getItems().size());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TradeParty original = TradeParty.builder()
                .characterId(268437521L)
                .items(Arrays.asList(stub(1L), stub(2L)))
                .build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void equals_shouldDistinguishCharacterId() {
        TradeParty a = TradeParty.builder().characterId(1L).build();
        TradeParty b = TradeParty.builder().characterId(2L).build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishItems() {
        TradeParty a = TradeParty.builder()
                .characterId(1L)
                .items(Collections.singletonList(stub(1L)))
                .build();
        TradeParty b = TradeParty.builder()
                .characterId(1L)
                .items(Collections.singletonList(stub(2L)))
                .build();

        assertNotEquals(a, b);
    }

    private static TradeItemMovement stub(long itemId) {
        return TradeItemMovement.builder()
                .item(ItemSnapshot.builder().itemId(itemId).itemTemplateId(57L).build())
                .newItemId(itemId)
                .count(1L)
                .build();
    }
}
