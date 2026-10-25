package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PrivateStoreSettingsTest {

    @Test
    void build_shouldAcceptAllNull_asFreeOfflineMode() {
        PrivateStoreSettings actual = PrivateStoreSettings.builder().build();

        assertNull(actual.getOfflineFeeItemTemplateId());
        assertNull(actual.getOfflineFeeItemTemplateCount());
        assertNull(actual.getOfflineFeePeriodHours());
    }

    @Test
    void build_shouldAcceptAllSet() {
        PrivateStoreSettings actual = new PrivateStoreSettings(57, 1000L, 24);

        assertEquals(1000L, actual.getOfflineFeeItemTemplateCount());
    }

    @ParameterizedTest(name = "{0}/{1}/{2}")
    @CsvSource({"57,,", ",1000,", ",,24", "57,1000,", "57,,24", ",1000,24"})
    void build_shouldRejectPartiallySetFee(Integer itemTemplateId, Long count, Integer periodHours) {
        assertThrows(
                IllegalArgumentException.class, () -> new PrivateStoreSettings(itemTemplateId, count, periodHours));
    }

    @ParameterizedTest(name = "{0}/{1}/{2}")
    @CsvSource({"0,1000,24", "-1,1000,24", "57,0,24", "57,-5,24", "57,1000,0", "57,1000,-1"})
    void build_shouldRejectNonPositiveValues(Integer itemTemplateId, Long count, Integer periodHours) {
        assertThrows(
                IllegalArgumentException.class, () -> new PrivateStoreSettings(itemTemplateId, count, periodHours));
    }
}
