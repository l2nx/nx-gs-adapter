package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PrivateStoreFeeTest {

    @Test
    void of_shouldRejectNegativeAdenaAndNegativePercent() {
        assertThrows(IllegalArgumentException.class, () -> PrivateStoreFee.of(-1L, 5));
        assertThrows(IllegalArgumentException.class, () -> PrivateStoreFee.of(1L, -1));
    }
}
