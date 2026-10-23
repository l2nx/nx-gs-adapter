package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SoldLine;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SellToPrivateStoreResultTest {

    private static SoldLine soldLine() {
        return SoldLine.builder()
                .itemId(1)
                .itemTemplateId(57L)
                .count(1L)
                .unitPriceAdena(100L)
                .build();
    }

    private static SellToPrivateStoreResult.Builder valid() {
        return SellToPrivateStoreResult.builder()
                .itemsTotalAdena(100L)
                .sellerFeeAdena(5L)
                .receivedTotalAdena(95L)
                .buyerFeeAdena(10L)
                .sold(Collections.singletonList(soldLine()))
                .storeClosed(false)
                .mailId(42L);
    }

    @ParameterizedTest(name = "items={0}, sellerFee={1}, received={2}")
    @CsvSource({"100, 5, 94", "100, 5, 96", "0, 0, 1"})
    void constructor_shouldReject_whenReceivedTotalDoesNotEqualItemsMinusSellerFee(
            long items, long sellerFee, long received) {
        SellToPrivateStoreResult.Builder builder =
                valid().itemsTotalAdena(items).sellerFeeAdena(sellerFee).receivedTotalAdena(received);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "items={0}, sellerFee={1}")
    @CsvSource({"100, 5", "0, 0", "1000000, 0", "100, 100"})
    void constructor_shouldAccept_whenReceivedTotalEqualsItemsMinusSellerFee(long items, long sellerFee) {
        SellToPrivateStoreResult result = valid().itemsTotalAdena(items)
                .sellerFeeAdena(sellerFee)
                .receivedTotalAdena(items - sellerFee)
                .build();
        assertEquals(items - sellerFee, result.getReceivedTotalAdena());
    }

    @ParameterizedTest(name = "sellerFee={0}")
    @CsvSource({"-1", "101"})
    void constructor_shouldReject_whenSellerFeeOutsideZeroToItemsTotal(long sellerFee) {
        SellToPrivateStoreResult.Builder builder =
                valid().sellerFeeAdena(sellerFee).receivedTotalAdena(100L - sellerFee);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenBuyerFeeNegative() {
        SellToPrivateStoreResult.Builder builder = valid().buyerFeeAdena(-1L);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldNotCountBuyerFeeIntoReceivedTotal() {
        SellToPrivateStoreResult result = valid().buyerFeeAdena(40L).build();

        assertEquals(40L, result.getBuyerFeeAdena());
        assertEquals(95L, result.getReceivedTotalAdena());
    }

    @Test
    void getSold_shouldReturnEmptyList_whenBuilderOmits() {
        SellToPrivateStoreResult result = SellToPrivateStoreResult.builder()
                .itemsTotalAdena(0L)
                .sellerFeeAdena(0L)
                .receivedTotalAdena(0L)
                .buyerFeeAdena(0L)
                .storeClosed(false)
                .mailId(0L)
                .build();

        assertTrue(result.getSold().isEmpty());
    }

    @Test
    void getSold_shouldBeUnmodifiable() {
        SellToPrivateStoreResult result = valid().build();
        List<SoldLine> sold = result.getSold();
        assertThrows(UnsupportedOperationException.class, () -> sold.add(soldLine()));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        SellToPrivateStoreResult original = valid().build();

        SellToPrivateStoreResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
    }

    @Test
    void isStoreClosed_shouldReflectBuilderValue() {
        SellToPrivateStoreResult result = valid().storeClosed(true).build();
        assertTrue(result.isStoreClosed());
    }
}
