package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PrivateStoreFeeTest {

    @ParameterizedTest(name = "adena={0} percent={1}")
    @CsvSource({"-1, 5", "1, -1"})
    void of_shouldRejectNegativeAdenaAndNegativePercent(long adena, int percent) {
        assertThrows(IllegalArgumentException.class, () -> PrivateStoreFee.of(adena, percent));
    }
}
