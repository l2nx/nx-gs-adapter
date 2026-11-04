package app.l2nx.gs.adapter.api.domain.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemSourceTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ItemSource.class)
    void fromToken_shouldReadEveryConstantName(ItemSource source) {
        assertEquals(source, ItemSource.fromToken(source.name()));
    }

    @ParameterizedTest(name = "token={0}")
    @NullSource
    @ValueSource(strings = {"", "SOME_FUTURE_SOURCE", "fishing"})
    void fromToken_shouldBeNull_whenTokenIsUnknown(String token) {
        assertNull(ItemSource.fromToken(token));
    }

    @Test
    void values_shouldBeTheWireVocabulary() {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "DROP",
                "SWEEP",
                "FISHING",
                "QUEST",
                "REWARD",
                "CRAFT",
                "CONSUME",
                "ENCHANT",
                "CRYSTALLIZE",
                "DEATH_DROP",
                "FEE",
                "NPC_BUY",
                "NPC_SELL",
                "MULTISELL",
                "TRADE",
                "PRIVATE_STORE",
                "MAIL",
                "PARTY_DISTRIBUTE",
                "WAREHOUSE",
                "PET",
                "FREIGHT",
                "ADMIN",
                "DONATE",
                "PLATFORM"));
        Set<String> actual = new HashSet<String>();
        for (ItemSource source : ItemSource.values()) {
            actual.add(source.name());
        }

        assertEquals(expected, actual);
    }
}
