package app.l2nx.gs.adapter.api.kafka.events.privatetrade;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TradePartyTest {

    private static Stream<TradeParty.Builder> withoutItems() {
        return Stream.of(
                TradeParty.builder().characterId(268437521L),
                TradeParty.builder().characterId(268437521L).items(null));
    }

    @ParameterizedTest
    @MethodSource("withoutItems")
    void getItems_shouldReturnEmptyList_whenBuilderOmitsOrPassesNull(TradeParty.Builder builder) {
        assertTrue(builder.build().getItems().isEmpty());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeItemsList() {
        List<TradeItemMovement> source = new ArrayList<TradeItemMovement>();
        source.add(stub(1L));

        TradeParty party =
                TradeParty.builder().characterId(268437521L).items(source).build();

        source.add(stub(2L));

        assertEquals(1, party.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> party.getItems().add(stub(2L)));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        TradeParty original = TradeParty.builder()
                .characterId(268437521L)
                .items(Arrays.asList(stub(1L), stub(2L)))
                .build();

        TradeParty copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(268437521L, copy.getCharacterId());
        assertEquals(Arrays.asList(stub(1L), stub(2L)), copy.getItems());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(TradeParty.class).verify();
    }

    private static TradeItemMovement stub(long itemId) {
        return TradeItemMovement.builder()
                .item(ItemSnapshot.builder().itemId(itemId).itemTemplateId(57L).build())
                .newItemId(itemId)
                .count(1L)
                .build();
    }
}
