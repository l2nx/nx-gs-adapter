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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @Nested
    class StoreStateRules {

        @ParameterizedTest
        @EnumSource(
                value = CharacterPrivateStore.class,
                names = {"SELL", "PACKAGE_SELL", "BUY"},
                mode = EnumSource.Mode.EXCLUDE)
        void constructor_shouldReject_whenNotMarketType(CharacterPrivateStore type) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StoreState.builder().type(type).build());
        }

        @Test
        void constructor_shouldReject_whenTypeNull() {
            assertThrows(NullPointerException.class, () -> StoreState.builder().build());
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
        @EnumSource(
                value = CharacterPrivateStore.class,
                names = {"SELL", "PACKAGE_SELL"})
        void constructor_shouldReject_whenBuyLinesInASellStore(CharacterPrivateStore type) {
            StoreState.Builder builder =
                    StoreState.builder().type(type).buyLines(Collections.singletonList(buyLine(1L, 1L)));

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldReject_whenSellLinesInABuyStore() {
            StoreState.Builder builder = StoreState.builder()
                    .type(CharacterPrivateStore.BUY)
                    .sellLines(Collections.singletonList(sellLine(1L, 1L)));

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
    }

    @Test
    void equals_shouldDistinguishCharacterId() {
        PrivateStoreStateEvent a =
                PrivateStoreStateEvent.builder().eventId(ID).characterId(1L).build();
        PrivateStoreStateEvent b =
                PrivateStoreStateEvent.builder().eventId(ID).characterId(2L).build();

        assertNotEquals(a, b);
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
