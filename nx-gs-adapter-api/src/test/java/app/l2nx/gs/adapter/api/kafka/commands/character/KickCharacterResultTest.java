package app.l2nx.gs.adapter.api.kafka.commands.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KickCharacterResultTest {

    @Test
    void builder_shouldRoundtripFields() {
        KickCharacterResult result =
                KickCharacterResult.builder().characterId(42L).offlineTrader(true).build();

        assertEquals(42L, result.getCharacterId().longValue());
        assertTrue(result.isOfflineTrader());
    }

    @Test
    void constructor_shouldRejectNullCharacterId() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> new KickCharacterResult(null, false));
        assertTrue(ex.getMessage().contains("characterId"));
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        KickCharacterResult original =
                KickCharacterResult.builder().characterId(42L).offlineTrader(true).build();

        KickCharacterResult copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void equals_shouldDistinguishOnCharacterId() {
        KickCharacterResult a = KickCharacterResult.builder().characterId(1L).build();
        KickCharacterResult b = KickCharacterResult.builder().characterId(2L).build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnOfflineTrader() {
        KickCharacterResult a =
                KickCharacterResult.builder().characterId(1L).offlineTrader(true).build();
        KickCharacterResult b =
                KickCharacterResult.builder().characterId(1L).offlineTrader(false).build();

        assertNotEquals(a, b);
    }

    @Test
    void hashCode_shouldMatchEquals() {
        KickCharacterResult a =
                KickCharacterResult.builder().characterId(1L).offlineTrader(true).build();
        KickCharacterResult b =
                KickCharacterResult.builder().characterId(1L).offlineTrader(true).build();

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_shouldExposeFields() {
        KickCharacterResult result =
                KickCharacterResult.builder().characterId(42L).offlineTrader(true).build();

        String s = result.toString();

        assertTrue(s.contains("42"));
        assertTrue(s.contains("true"));
    }
}
