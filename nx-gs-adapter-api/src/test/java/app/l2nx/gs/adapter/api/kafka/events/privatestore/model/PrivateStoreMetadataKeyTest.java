package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrivateStoreMetadataKeyTest {

    @Nested
    class FromToken {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "store_owner_adena,STORE_OWNER_ADENA",
            "source,SOURCE",
            "buyer_fee_adena,BUYER_FEE_ADENA",
            "buyer_fee_percent,BUYER_FEE_PERCENT",
            "seller_fee_adena,SELLER_FEE_ADENA",
            "seller_fee_percent,SELLER_FEE_PERCENT"
        })
        void fromToken_shouldResolveWireKey(String token, PrivateStoreMetadataKey expected) {
            assertEquals(expected, PrivateStoreMetadataKey.fromToken(token));
            assertEquals(token, expected.key());
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", " ", "custom_key", "SOURCE", "STORE_OWNER_ADENA"})
        void fromToken_shouldReturnNull_whenTokenUnknownOrBlank(String token) {
            assertNull(PrivateStoreMetadataKey.fromToken(token));
        }
    }
}
