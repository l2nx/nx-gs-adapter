package app.l2nx.gs.adapter.api.domain.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocationTypeTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LocationType.class)
    void fromToken_shouldReadEveryConstantName(LocationType type) {
        assertEquals(type, LocationType.fromToken(type.name()));
    }

    @ParameterizedTest(name = "token={0}")
    @NullSource
    @ValueSource(strings = {"", "TERRITORY", "town"})
    void fromToken_shouldBeNull_whenTokenIsUnknown(String token) {
        assertNull(LocationType.fromToken(token));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LocationType.class)
    void displayName_shouldCoverEveryPlatformLocale(LocationType type) {
        for (String locale : Arrays.asList("ru", "en", "uk", "es")) {
            assertNotNull(type.displayName().get(locale), type + " " + locale);
        }
    }

    @Test
    void sortOrder_shouldBeUniqueAndFollowDeclarationOrder() {
        int previous = Integer.MIN_VALUE;
        Set<Integer> seen = new HashSet<Integer>();
        for (LocationType type : LocationType.values()) {
            assertEquals(true, type.sortOrder() > previous, type.name());
            assertEquals(true, seen.add(type.sortOrder()), type.name());
            previous = type.sortOrder();
        }
    }

    @Test
    void values_shouldBeTheWireVocabulary() {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "LAND",
                "TOWN",
                "HUNTING_GROUND",
                "DUNGEON",
                "CASTLE",
                "FORTRESS",
                "CLAN_HALL",
                "REFLECTION",
                "LANDMARK",
                "SPECIAL_ZONE"));
        Set<String> actual = new HashSet<String>();
        for (LocationType type : LocationType.values()) {
            actual.add(type.name());
        }

        assertEquals(expected, actual);
    }
}
