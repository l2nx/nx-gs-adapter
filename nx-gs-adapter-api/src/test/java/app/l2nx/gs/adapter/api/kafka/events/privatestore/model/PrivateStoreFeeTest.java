package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.PrivateStoreFees;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SellerFeeTest {

    private static Map<String, String> metadata(String adena, String percent) {
        Map<String, String> map = new HashMap<String, String>();
        if (adena != null) {
            map.put(WellKnownPrivateStoreMetadata.SELLER_FEE_ADENA, adena);
        }
        if (percent != null) {
            map.put(WellKnownPrivateStoreMetadata.SELLER_FEE_PERCENT, percent);
        }
        return map;
    }

    @Nested
    class SellerFeeAccessor {

        @Test
        void sellerFee_shouldParseBothEntries() {
            SellerFee fee = WellKnownPrivateStoreMetadata.sellerFee(metadata("1500", "5"));

            assertEquals(SellerFee.of(1500L, 5), fee);
            assertEquals(1500L, fee.getAdena());
            assertEquals(
                    0L,
                    WellKnownPrivateStoreMetadata.sellerFee(metadata("0", "5")).getAdena());
            assertEquals(5, fee.getPercent());
        }

        @Test
        void sellerFee_shouldAcceptPercentBounds() {
            assertEquals(
                    0,
                    WellKnownPrivateStoreMetadata.sellerFee(metadata("1", "0")).getPercent());
            assertEquals(
                    PrivateStoreFees.MAX_FEE_PERCENT,
                    WellKnownPrivateStoreMetadata.sellerFee(metadata("1", "50")).getPercent());
        }

        @Test
        void sellerFee_shouldReturnNull_whenMetadataNullOrEmpty() {
            assertNull(WellKnownPrivateStoreMetadata.sellerFee(null));
            assertNull(WellKnownPrivateStoreMetadata.sellerFee(new HashMap<String, String>()));
        }

        @ParameterizedTest(name = "adena={0} percent={1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "null,5",
                    "100,null",
                    "-3,5",
                    "100,-1",
                    "100,51",
                    "abc,5",
                    "100,x",
                    "1.5,5",
                    "100,5.0",
                    "99999999999999999999,5"
                })
        void sellerFee_shouldReturnNull_whenAnyEntryMissingOrInvalid(String adena, String percent) {
            assertNull(WellKnownPrivateStoreMetadata.sellerFee(metadata(adena, percent)));
        }
    }

    @Test
    void of_shouldRejectNegativeAdenaAndOutOfRangePercent() {
        assertThrows(IllegalArgumentException.class, () -> SellerFee.of(-1L, 5));
        assertThrows(IllegalArgumentException.class, () -> SellerFee.of(1L, -1));
        assertThrows(IllegalArgumentException.class, () -> SellerFee.of(1L, PrivateStoreFees.MAX_FEE_PERCENT + 1));
    }
}
