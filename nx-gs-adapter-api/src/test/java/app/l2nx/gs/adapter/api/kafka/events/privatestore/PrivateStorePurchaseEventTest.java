package app.l2nx.gs.adapter.api.kafka.events.privatestore;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.PrivateStoreSide;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.TradeLine;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PrivateStorePurchaseEventTest {

    private static PrivateStorePurchaseEvent.Builder valid() {
        return PrivateStorePurchaseEvent.builder()
                .eventId(UUID.randomUUID())
                .storeType(PrivateStoreSide.ASK)
                .sellerId(1L)
                .buyerId(2L);
    }

    private static Stream<PrivateStorePurchaseEvent.Builder> withoutLines() {
        return Stream.of(valid(), valid().lines(null));
    }

    @ParameterizedTest
    @MethodSource("withoutLines")
    void getLines_shouldReturnEmptyList_whenBuilderOmitsOrPassesNull(PrivateStorePurchaseEvent.Builder builder) {
        assertTrue(builder.build().getLines().isEmpty());
    }

    @Test
    void getSellerName_shouldBeNullable() {
        PrivateStorePurchaseEvent event = valid().build();

        assertNull(event.getSellerName());
        assertNull(event.getBuyerName());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeLinesList() {
        List<TradeLine> source = new ArrayList<TradeLine>();
        source.add(stubLine(1234L, 100L));

        PrivateStorePurchaseEvent event = valid().lines(source).build();

        source.add(stubLine(5678L, 200L));

        assertEquals(1, event.getLines().size());
        assertThrows(UnsupportedOperationException.class, () -> event.getLines().add(stubLine(5678L, 200L)));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        PrivateStorePurchaseEvent original = PrivateStorePurchaseEvent.builder()
                .eventId(eventId)
                .storeType(PrivateStoreSide.BID)
                .sellerId(268437521L)
                .sellerName("Hisho")
                .buyerId(268437522L)
                .buyerName("Shanon")
                .lines(Arrays.asList(stubLine(1234L, 100L), stubLine(5678L, 200L)))
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        PrivateStorePurchaseEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(PrivateStoreSide.BID, copy.getStoreType());
        assertEquals(268437521L, copy.getSellerId());
        assertEquals("Hisho", copy.getSellerName());
        assertEquals(268437522L, copy.getBuyerId());
        assertEquals("Shanon", copy.getBuyerName());
        assertEquals(Arrays.asList(stubLine(1234L, 100L), stubLine(5678L, 200L)), copy.getLines());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(PrivateStorePurchaseEvent.class).verify();
    }

    private static TradeLine stubLine(long itemId, long unitPrice) {
        return TradeLine.builder()
                .item(ItemSnapshot.builder()
                        .itemId(itemId)
                        .itemTemplateId(6364L)
                        .build())
                .count(1L)
                .unitPrice(unitPrice)
                .currencyItemTemplateId(57L)
                .build();
    }
}
