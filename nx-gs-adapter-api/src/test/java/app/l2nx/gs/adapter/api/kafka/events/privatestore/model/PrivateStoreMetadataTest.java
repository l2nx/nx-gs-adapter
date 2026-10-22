package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Nested
    class SellerFeeAccessor {

        private Map<String, String> sellerMetadata(String adena, String percent) {
            return metadata(
                    PrivateStoreMetadataKey.SELLER_FEE_ADENA,
                    adena,
                    PrivateStoreMetadataKey.SELLER_FEE_PERCENT,
                    percent);
        }

        @Test
        void sellerFee_shouldParseBothEntries() {
            PrivateStoreFee actual = PrivateStoreMetadata.sellerFee(sellerMetadata("1500", "5"));

            assertEquals(PrivateStoreFee.of(1500L, 5), actual);
            assertEquals(
                    0L, PrivateStoreMetadata.sellerFee(sellerMetadata("0", "5")).getAdena());
        }

        @Test
        void sellerFee_shouldAcceptPercentBounds() {
            assertEquals(
                    0, PrivateStoreMetadata.sellerFee(sellerMetadata("1", "0")).getPercent());
            assertEquals(
                    75,
                    PrivateStoreMetadata.sellerFee(sellerMetadata("1", "75")).getPercent());
        }

        @Test
        void sellerFee_shouldAcceptHundredAndReturnNullAbove() {
            assertEquals(
                    100,
                    PrivateStoreMetadata.sellerFee(sellerMetadata("1", "100")).getPercent());
            assertNull(PrivateStoreMetadata.sellerFee(sellerMetadata("1", "101")));
        }

        @Test
        void sellerFee_shouldReturnNull_whenMetadataNullOrEmpty() {
            assertNull(PrivateStoreMetadata.sellerFee(null));
            assertNull(PrivateStoreMetadata.sellerFee(new HashMap<String, String>()));
        }

        @Test
        void sellerFee_shouldIgnoreBuyerFeeEntries() {
            Map<String, String> buyerOnly = metadata(
                    PrivateStoreMetadataKey.BUYER_FEE_ADENA, "100", PrivateStoreMetadataKey.BUYER_FEE_PERCENT, "5");

            assertNull(PrivateStoreMetadata.sellerFee(buyerOnly));
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
        void sellerFee_shouldReturnNull_whenAnyEntryMissingOrInvalid(String adena, String percent) {
            assertNull(PrivateStoreMetadata.sellerFee(sellerMetadata(adena, percent)));
        }
    }

    @Nested
    class BuyerFeeAccessor {

        private Map<String, String> buyerMetadata(String adena, String percent) {
            return metadata(
                    PrivateStoreMetadataKey.BUYER_FEE_ADENA, adena, PrivateStoreMetadataKey.BUYER_FEE_PERCENT, percent);
        }

        @Test
        void buyerFee_shouldParseBothEntries() {
            PrivateStoreFee actual = PrivateStoreMetadata.buyerFee(buyerMetadata("1500", "5"));

            assertEquals(PrivateStoreFee.of(1500L, 5), actual);
            assertEquals(
                    0L, PrivateStoreMetadata.buyerFee(buyerMetadata("0", "5")).getAdena());
        }

        @Test
        void buyerFee_shouldAcceptPercentBounds() {
            assertEquals(
                    0, PrivateStoreMetadata.buyerFee(buyerMetadata("1", "0")).getPercent());
            assertEquals(
                    75, PrivateStoreMetadata.buyerFee(buyerMetadata("1", "75")).getPercent());
        }

        @Test
        void buyerFee_shouldAcceptPercentAboveHundred() {
            assertEquals(
                    150,
                    PrivateStoreMetadata.buyerFee(buyerMetadata("1", "150")).getPercent());
        }

        @Test
        void buyerFee_shouldReturnNull_whenMetadataNullOrEmpty() {
            assertNull(PrivateStoreMetadata.buyerFee(null));
            assertNull(PrivateStoreMetadata.buyerFee(new HashMap<String, String>()));
        }

        @Test
        void buyerFee_shouldIgnoreSellerFeeEntries() {
            Map<String, String> sellerOnly = metadata(
                    PrivateStoreMetadataKey.SELLER_FEE_ADENA, "100", PrivateStoreMetadataKey.SELLER_FEE_PERCENT, "5");

            assertNull(PrivateStoreMetadata.buyerFee(sellerOnly));
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
        void buyerFee_shouldReturnNull_whenAnyEntryMissingOrInvalid(String adena, String percent) {
            assertNull(PrivateStoreMetadata.buyerFee(buyerMetadata(adena, percent)));
        }
    }
}
