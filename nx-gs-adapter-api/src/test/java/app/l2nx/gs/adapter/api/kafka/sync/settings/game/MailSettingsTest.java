package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MailSettingsTest {

    @ParameterizedTest(name = "{0}/{1}")
    @CsvSource({"-1,0", "0,-1"})
    void build_shouldRejectNegativeFee(long feeAdena, long feeAdenaPerAttachment) {
        assertThrows(IllegalArgumentException.class, () -> new MailSettings(feeAdena, feeAdenaPerAttachment));
    }

    @Test
    void build_shouldAcceptZeroFees() {
        assertEquals(new MailSettings(0, 0), MailSettings.builder().build());
    }
}
