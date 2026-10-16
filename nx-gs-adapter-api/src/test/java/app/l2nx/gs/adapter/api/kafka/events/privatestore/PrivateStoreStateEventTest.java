package app.l2nx.gs.adapter.api.kafka.events.privatestore;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.character.CharacterPrivateStore;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.StoreLine;
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

    private static StoreLine line(long itemTemplateId, long count) {
        return StoreLine.builder()
                .itemId(555L)
                .itemTemplateId(itemTemplateId)
                .enchantLevel(3)
                .attributes(Collections.singletonMap(Attribute.FIRE, 20))
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
                .lines(Arrays.asList(line(10L, 2L), line(11L, 1L)))
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

            assertTrue(state.getLines().isEmpty());
            assertThrows(
                    UnsupportedOperationException.class, () -> state.getLines().add(line(1L, 1L)));
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

            assertNotNull(state.getLines());
            assertTrue(state.getLines().isEmpty());
        }

        @Test
        void constructor_shouldDefensivelyCopyLines() {
            List<StoreLine> source = new ArrayList<StoreLine>();
            source.add(line(1L, 1L));

            StoreState state = StoreState.builder()
                    .type(CharacterPrivateStore.SELL)
                    .lines(source)
                    .build();
            source.add(line(2L, 1L));

            assertEquals(1, state.getLines().size());
        }
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        PrivateStoreStateEvent original = PrivateStoreStateEvent.builder()
                .eventId(ID)
                .charId(7L)
                .store(store())
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        PrivateStoreStateEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishCharId() {
        PrivateStoreStateEvent a =
                PrivateStoreStateEvent.builder().eventId(ID).charId(1L).build();
        PrivateStoreStateEvent b =
                PrivateStoreStateEvent.builder().eventId(ID).charId(2L).build();

        assertNotEquals(a, b);
    }

    @Test
    void gson_shouldRoundtripFullStateAndTombstone() {
        Gson gson = new Gson();
        PrivateStoreStateEvent full = PrivateStoreStateEvent.builder()
                .eventId(ID)
                .charId(7L)
                .store(store())
                .build();
        PrivateStoreStateEvent tombstone =
                PrivateStoreStateEvent.builder().eventId(ID).charId(7L).build();

        assertEquals(full, gson.fromJson(gson.toJson(full), PrivateStoreStateEvent.class));
        PrivateStoreStateEvent tombstoneCopy = gson.fromJson(gson.toJson(tombstone), PrivateStoreStateEvent.class);
        assertEquals(tombstone, tombstoneCopy);
        assertNull(tombstoneCopy.getStore());
    }
}
