package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MailSettingsTest {

    @Test
    void build_shouldRejectNegativeFee() {
        assertThrows(IllegalArgumentException.class, () -> new MailSettings(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MailSettings(0, -1));
    }

    @Test
    void build_shouldAcceptZeroFees() {
        assertEquals(new MailSettings(0, 0), MailSettings.builder().build());
    }
}
