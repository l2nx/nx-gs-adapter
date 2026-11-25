package app.l2nx.gs.adapter.api.domain.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CharacterPrivateStoreTest {

    @Test
    void byId_shouldReturnNull_forNonSurfacedIds() {
        // 0 = no store, 2 = sell pending (menu), 4 = buy pending (menu), 6/7 = manage modes.
        assertNull(CharacterPrivateStore.byId(0));
        assertNull(CharacterPrivateStore.byId(2));
        assertNull(CharacterPrivateStore.byId(4));
        assertNull(CharacterPrivateStore.byId(6));
        assertNull(CharacterPrivateStore.byId(7));
        assertNull(CharacterPrivateStore.byId(-1));
        assertNull(CharacterPrivateStore.byId(99));
    }

    @Test
    void canonicalSamples_shouldResolveToExpectedConstant() {
        assertSame(CharacterPrivateStore.SELL, CharacterPrivateStore.byId(1));
        assertSame(CharacterPrivateStore.BUY, CharacterPrivateStore.byId(3));
        assertSame(CharacterPrivateStore.CRAFT, CharacterPrivateStore.byId(5));
        assertSame(CharacterPrivateStore.PACKAGE_SELL, CharacterPrivateStore.byId(8));

        Map<String, Integer> expected = new LinkedHashMap<String, Integer>();
        expected.put("SELL", 1);
        expected.put("BUY", 3);
        expected.put("CRAFT", 5);
        expected.put("PACKAGE_SELL", 8);
        Map<String, Integer> actual = new LinkedHashMap<String, Integer>();
        for (CharacterPrivateStore m : CharacterPrivateStore.values()) {
            actual.put(m.name(), m.getId());
            assertSame(m, CharacterPrivateStore.byId(m.getId()));
        }
        assertEquals(expected, actual);
    }
}
