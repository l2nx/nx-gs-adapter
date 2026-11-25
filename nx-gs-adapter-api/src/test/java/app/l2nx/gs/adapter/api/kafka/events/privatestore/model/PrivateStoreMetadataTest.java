package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrivateStoreMetadataTest {

    private static Map<String, String> metadata(
            PrivateStoreMetadataKey adenaKey, String adena, PrivateStoreMetadataKey percentKey, String percent) {
        Map<String, String> map = new HashMap<String, String>();
        if (adena != null) {
            map.put(adenaKey.key(), adena);
        }
        if (percent != null) {
            map.put(percentKey.key(), percent);
        }
        return map;
    }

    private static Map<String, String> sellerMetadata(String adena, String percent) {
        return metadata(
                PrivateStoreMetadataKey.SELLER_FEE_ADENA, adena, PrivateStoreMetadataKey.SELLER_FEE_PERCENT, percent);
    }

    private static Map<String, String> buyerMetadata(String adena, String percent) {
        return metadata(
                PrivateStoreMetadataKey.BUYER_FEE_ADENA, adena, PrivateStoreMetadataKey.BUYER_FEE_PERCENT, percent);
    }

    @Test
    void fees_shouldReturnNull_whenMetadataNullOrEmpty() {
        assertNull(PrivateStoreMetadata.sellerFee(null));
        assertNull(PrivateStoreMetadata.sellerFee(new HashMap<String, String>()));
        assertNull(PrivateStoreMetadata.buyerFee(null));
        assertNull(PrivateStoreMetadata.buyerFee(new HashMap<String, String>()));
    }

    @ParameterizedTest(name = "adena={0} percent={1}")
    @CsvSource(
            nullValues = "null",
            value = {
                "null,5",
                "100,null",
                "-3,5",
                "100,-1",
                "abc,5",
                "100,x",
                "1.5,5",
                "100,5.0",
                "99999999999999999999,5"
            })
    void fees_shouldReturnNull_whenAnyEntryMissingOrInvalid(String adena, String percent) {
        assertNull(PrivateStoreMetadata.sellerFee(sellerMetadata(adena, percent)));
        assertNull(PrivateStoreMetadata.buyerFee(buyerMetadata(adena, percent)));
    }

    @Nested
    class SellerFeeAccessor {

        @Test
        void sellerFee_shouldParseBothEntries() {
            PrivateStoreFee actual = PrivateStoreMetadata.sellerFee(sellerMetadata("1500", "5"));

            assertEquals(PrivateStoreFee.of(1500L, 5), actual);
            assertEquals(
                    0L, PrivateStoreMetadata.sellerFee(sellerMetadata("0", "5")).getAdena());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 75, 100})
        void sellerFee_shouldAcceptPercentUpToHundred(int percent) {
            assertEquals(
                    percent,
                    PrivateStoreMetadata.sellerFee(sellerMetadata("1", String.valueOf(percent)))
                            .getPercent());
        }

        @Test
        void sellerFee_shouldReturnNull_whenPercentAboveHundred() {
            assertNull(PrivateStoreMetadata.sellerFee(sellerMetadata("1", "101")));
        }

        @Test
        void sellerFee_shouldIgnoreBuyerFeeEntries() {
            Map<String, String> buyerOnly = metadata(
                    PrivateStoreMetadataKey.BUYER_FEE_ADENA, "100", PrivateStoreMetadataKey.BUYER_FEE_PERCENT, "5");

            assertNull(PrivateStoreMetadata.sellerFee(buyerOnly));
        }
    }

    @Nested
    class BuyerFeeAccessor {

        @Test
        void buyerFee_shouldParseBothEntries() {
            PrivateStoreFee actual = PrivateStoreMetadata.buyerFee(buyerMetadata("1500", "5"));

            assertEquals(PrivateStoreFee.of(1500L, 5), actual);
            assertEquals(
                    0L, PrivateStoreMetadata.buyerFee(buyerMetadata("0", "5")).getAdena());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 75, 150})
        void buyerFee_shouldAcceptPercentAboveHundred(int percent) {
            assertEquals(
                    percent,
                    PrivateStoreMetadata.buyerFee(buyerMetadata("1", String.valueOf(percent)))
                            .getPercent());
        }

        @Test
        void buyerFee_shouldIgnoreSellerFeeEntries() {
            Map<String, String> sellerOnly = metadata(
                    PrivateStoreMetadataKey.SELLER_FEE_ADENA, "100", PrivateStoreMetadataKey.SELLER_FEE_PERCENT, "5");

            assertNull(PrivateStoreMetadata.buyerFee(sellerOnly));
        }
    }
}
