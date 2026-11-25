package app.l2nx.gs.adapter.api.kafka.commands.character;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class KickCharacterResultTest {

    @Test
    void builder_shouldRoundtripFields() {
        KickCharacterResult result = KickCharacterResult.builder()
                .characterId(42L)
                .offlineTrader(true)
                .build();

        assertEquals(42L, result.getCharacterId().longValue());
        assertTrue(result.isOfflineTrader());
        assertEquals(result, result.toBuilder().build());
    }

    @Test
    void constructor_shouldRejectNullCharacterId() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> new KickCharacterResult(null, false));
        assertTrue(ex.getMessage().contains("characterId"));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(KickCharacterResult.class).verify();
    }
}
