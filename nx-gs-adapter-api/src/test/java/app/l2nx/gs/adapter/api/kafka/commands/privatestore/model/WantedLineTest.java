package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WantedLineTest {

    private static WantedLine.Builder valid() {
        return WantedLine.builder().itemTemplateId(57L).count(1L).priceAdena(100L);
    }

    @ParameterizedTest(name = "itemTemplateId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenItemTemplateIdNotPositive(long itemTemplateId) {
        WantedLine.Builder builder = valid().itemTemplateId(itemTemplateId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "count={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenCountNotPositive(long count) {
        WantedLine.Builder builder = valid().count(count);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenPriceAdenaNegative() {
        WantedLine.Builder builder = valid().priceAdena(-1L);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenCountTimesPriceOverflows() {
        WantedLine.Builder builder = valid().count(Long.MAX_VALUE).priceAdena(2L);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldKeepZeroPrice() {
        WantedLine actual = valid().priceAdena(0L).build();

        assertEquals(0L, actual.getPriceAdena());
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        WantedLine original = valid().count(3L).build();

        assertEquals(original, original.toBuilder().build());
        assertEquals(original.hashCode(), original.toBuilder().build().hashCode());
    }

    @Test
    void firstDuplicateItemTemplateId_shouldReturnNull_whenTemplatesDistinct() {
        WantedLine first = valid().build();
        WantedLine second = valid().itemTemplateId(58L).build();

        assertNull(WantedLine.firstDuplicateItemTemplateId(Arrays.asList(first, second)));
        assertNull(WantedLine.firstDuplicateItemTemplateId(Collections.<WantedLine>emptyList()));
    }

    @Test
    void firstDuplicateItemTemplateId_shouldReturnTheRepeatedTemplate_whenTemplateRepeats() {
        WantedLine first = valid().build();
        WantedLine second = valid().count(9L).priceAdena(1L).build();

        assertEquals(
                Long.valueOf(second.getItemTemplateId()),
                WantedLine.firstDuplicateItemTemplateId(Arrays.asList(first, second)));
    }
}
