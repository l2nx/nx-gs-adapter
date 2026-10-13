package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.BoughtLine;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BuyFromPrivateStoreResultTest {

    private static BoughtLine boughtLine() {
        return BoughtLine.builder()
                .itemId(1)
                .itemTemplateId(57L)
                .count(1L)
                .unitPriceAdena(100L)
                .build();
    }

    private static BuyFromPrivateStoreResult.Builder valid() {
        return BuyFromPrivateStoreResult.builder()
                .itemsTotalAdena(100L)
                .buyerFeeAdena(5L)
                .sellerFeeAdena(10L)
                .paidTotalAdena(105L)
                .bought(Collections.singletonList(boughtLine()))
                .storeClosed(false)
                .mailId(42L);
    }

    @ParameterizedTest(name = "items={0}, buyerFee={1}, paid={2}")
    @CsvSource({"100, 5, 104", "100, 5, 106", "0, 0, 1"})
    void constructor_shouldReject_whenPaidTotalDoesNotEqualItemsPlusBuyerFee(long items, long buyerFee, long paid) {
        BuyFromPrivateStoreResult.Builder builder =
                valid().itemsTotalAdena(items).buyerFeeAdena(buyerFee).paidTotalAdena(paid);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "items={0}, buyerFee={1}")
    @CsvSource({"100, 5", "0, 0", "1000000, 0"})
    void constructor_shouldAccept_whenPaidTotalEqualsItemsPlusBuyerFee(long items, long buyerFee) {
        BuyFromPrivateStoreResult result = valid().sellerFeeAdena(0L)
                .itemsTotalAdena(items)
                .buyerFeeAdena(buyerFee)
                .paidTotalAdena(items + buyerFee)
                .build();
        assertEquals(items + buyerFee, result.getPaidTotalAdena());
    }

    @Test
    void constructor_shouldNotCountSellerFeeIntoPaidTotal() {
        BuyFromPrivateStoreResult result = valid().sellerFeeAdena(40L).build();

        assertEquals(40L, result.getSellerFeeAdena());
        assertEquals(105L, result.getPaidTotalAdena());
    }

    @ParameterizedTest(name = "sellerFee={0}")
    @CsvSource({"-1", "101"})
    void constructor_shouldReject_whenSellerFeeOutsideZeroToItemsTotal(long sellerFee) {
        BuyFromPrivateStoreResult.Builder builder = valid().sellerFeeAdena(sellerFee);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "sellerFee={0}")
    @CsvSource({"0", "100"})
    void constructor_shouldAccept_whenSellerFeeAtBounds(long sellerFee) {
        assertEquals(sellerFee, valid().sellerFeeAdena(sellerFee).build().getSellerFeeAdena());
    }

    @Test
    void getBought_shouldReturnEmptyList_whenBuilderOmits() {
        BuyFromPrivateStoreResult result = BuyFromPrivateStoreResult.builder()
                .itemsTotalAdena(0L)
                .buyerFeeAdena(0L)
                .sellerFeeAdena(0L)
                .paidTotalAdena(0L)
                .storeClosed(false)
                .mailId(0L)
                .build();

        assertTrue(result.getBought().isEmpty());
    }

    @Test
    void getBought_shouldBeUnmodifiable() {
        BuyFromPrivateStoreResult result = valid().build();
        List<BoughtLine> bought = result.getBought();
        assertThrows(UnsupportedOperationException.class, () -> bought.add(boughtLine()));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BuyFromPrivateStoreResult original = valid().build();

        BuyFromPrivateStoreResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
    }

    @Test
    void isStoreClosed_shouldReflectBuilderValue() {
        BuyFromPrivateStoreResult result = valid().storeClosed(true).build();
        assertTrue(result.isStoreClosed());
    }
}
