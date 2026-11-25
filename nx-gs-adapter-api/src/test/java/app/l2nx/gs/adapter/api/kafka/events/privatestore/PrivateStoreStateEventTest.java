package app.l2nx.gs.adapter.api.kafka.events.privatestore;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.character.CharacterPrivateStore;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.BuyStoreLine;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.SellStoreLine;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.StoreState;
import com.google.gson.Gson;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrivateStoreStateEventTest {

    private static final UUID ID = UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");

    private static SellStoreLine sellLine(long itemTemplateId, long count) {
        return SellStoreLine.builder()
                .item(ItemSnapshot.builder()
                        .itemId(555L)
                        .itemTemplateId(itemTemplateId)
                        .enchantLevel(3)
                        .attributes(Collections.singletonMap(Attribute.FIRE, 20))
                        .build())
                .count(count)
                .unitPrice(1000L)
                .currencyItemTemplateId(57L)
                .build();
    }

    private static BuyStoreLine buyLine(long itemTemplateId, long count) {
        return BuyStoreLine.builder()
                .itemTemplateId(itemTemplateId)
                .count(count)
                .unitPrice(1000L)
                .currencyItemTemplateId(57L)
                .build();
    }

    private static StoreState store() {
        return StoreState.builder()
                .type(CharacterPrivateStore.SELL)
                .title("selling")
                .offline(true)
                .sellLines(Arrays.asList(sellLine(10L, 2L), sellLine(11L, 1L)))
                .build();
    }

    private static Stream<StoreState.Builder> wrongSideStores() {
        return Stream.of(
                StoreState.builder()
                        .type(CharacterPrivateStore.SELL)
                        .buyLines(Collections.singletonList(buyLine(1L, 1L))),
                StoreState.builder()
                        .type(CharacterPrivateStore.PACKAGE_SELL)
                        .buyLines(Collections.singletonList(buyLine(1L, 1L))),
                StoreState.builder()
                        .type(CharacterPrivateStore.BUY)
                        .sellLines(Collections.singletonList(sellLine(1L, 1L))));
    }

    @Nested
    class StoreStateRules {

        @ParameterizedTest
        @NullSource
        @EnumSource(
                value = CharacterPrivateStore.class,
                names = {"SELL", "PACKAGE_SELL", "BUY"},
                mode = EnumSource.Mode.EXCLUDE)
        void constructor_shouldReject_whenTypeNullOrNotMarket(CharacterPrivateStore type) {
            Class<? extends RuntimeException> expected =
                    type == null ? NullPointerException.class : IllegalArgumentException.class;

            assertThrows(expected, () -> StoreState.builder().type(type).build());
        }

        @Test
        void getLines_shouldBeEmptyAndUnmodifiable_whenNull() {
            StoreState state =
                    StoreState.builder().type(CharacterPrivateStore.BUY).build();

            assertTrue(state.getSellLines().isEmpty());
            assertTrue(state.getBuyLines().isEmpty());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> state.getSellLines().add(sellLine(1L, 1L)));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> state.getBuyLines().add(buyLine(1L, 1L)));
        }

        @ParameterizedTest
        @MethodSource("app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStoreStateEventTest#wrongSideStores")
        void constructor_shouldReject_whenLinesOfTheOtherSide(StoreState.Builder builder) {
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void getBuyLines_shouldCarryTheLines_whenBuyStore() {
            StoreState state = StoreState.builder()
                    .type(CharacterPrivateStore.BUY)
                    .buyLines(Collections.singletonList(buyLine(1L, 1L)))
                    .build();

            assertEquals(Collections.singletonList(buyLine(1L, 1L)), state.getBuyLines());
            assertTrue(state.getSellLines().isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void constructor_shouldNormalizeBlankTitleToNull(String title) {
            StoreState state = StoreState.builder()
                    .type(CharacterPrivateStore.SELL)
                    .title(title)
                    .build();

            assertNull(state.getTitle());
        }

        @Test
        void getLines_shouldBeEmpty_whenLinesKeyAbsentInGson() {
            StoreState state = new Gson().fromJson("{\"type\":\"SELL\",\"offline\":false}", StoreState.class);

            assertNotNull(state.getSellLines());
            assertTrue(state.getSellLines().isEmpty());
            assertNotNull(state.getBuyLines());
            assertTrue(state.getBuyLines().isEmpty());
        }

        @Test
        void constructor_shouldDefensivelyCopyLines() {
            List<SellStoreLine> source = new ArrayList<SellStoreLine>();
            source.add(sellLine(1L, 1L));

            StoreState state = StoreState.builder()
                    .type(CharacterPrivateStore.SELL)
                    .sellLines(source)
                    .build();
            source.add(sellLine(2L, 1L));

            assertEquals(1, state.getSellLines().size());
        }
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        PrivateStoreStateEvent original = PrivateStoreStateEvent.builder()
                .eventId(ID)
                .characterId(7L)
                .store(store())
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        PrivateStoreStateEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
        assertEquals(ID, copy.getEventId());
        assertEquals(7L, copy.getCharacterId());
        assertEquals(store(), copy.getStore());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
        assertEquals(CharacterPrivateStore.SELL, store().getType());
        assertEquals("selling", store().getTitle());
        assertTrue(store().isOffline());
        assertEquals(Arrays.asList(sellLine(10L, 2L), sellLine(11L, 1L)), store().getSellLines());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(PrivateStoreStateEvent.class).verify();
    }

    @Test
    void gson_shouldRoundtripFullStateAndTombstone() {
        Gson gson = new Gson();
        PrivateStoreStateEvent full = PrivateStoreStateEvent.builder()
                .eventId(ID)
                .characterId(7L)
                .store(store())
                .build();
        PrivateStoreStateEvent tombstone =
                PrivateStoreStateEvent.builder().eventId(ID).characterId(7L).build();

        assertEquals(full, gson.fromJson(gson.toJson(full), PrivateStoreStateEvent.class));
        PrivateStoreStateEvent tombstoneCopy = gson.fromJson(gson.toJson(tombstone), PrivateStoreStateEvent.class);
        assertEquals(tombstone, tombstoneCopy);
        assertNull(tombstoneCopy.getStore());
    }
}
