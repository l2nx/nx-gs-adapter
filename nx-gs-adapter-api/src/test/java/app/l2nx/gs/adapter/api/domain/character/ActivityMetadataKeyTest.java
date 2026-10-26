package app.l2nx.gs.adapter.api.domain.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActivityMetadataKeyTest {

    @Nested
    class FromToken {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "elapsed,ELAPSED",
            "remaining,REMAINING",
            "rodItemId,ROD_ITEM_ID",
            "baitItemId,BAIT_ITEM_ID",
            "fishingShotItemId,FISHING_SHOT_ITEM_ID",
            "spotPool,SPOT_POOL",
            "spotId,SPOT_ID"
        })
        void fromToken_shouldResolveWireKey(String token, ActivityMetadataKey expected) {
            assertEquals(expected, ActivityMetadataKey.fromToken(token));
            assertEquals(token, expected.key());
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", " ", "custom_key", "ELAPSED", "ROD_ITEM_ID", "rod_item_id"})
        void fromToken_shouldReturnNull_whenTokenUnknownOrBlank(String token) {
            assertNull(ActivityMetadataKey.fromToken(token));
        }
    }
}
