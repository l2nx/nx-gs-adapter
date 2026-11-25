package app.l2nx.gs.adapter.api.kafka.ops.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChangesSummaryTest {

    @ParameterizedTest
    @CsvSource({"3, 7, 1", "2, 5, 0"})
    void builder_shouldMapEachFieldToConstructorPosition(long created, long updated, long deleted) {
        ChangesSummary summary = ChangesSummary.builder()
                .created(created)
                .updated(updated)
                .deleted(deleted)
                .build();

        assertEquals(created, summary.getCreated());
        assertEquals(updated, summary.getUpdated());
        assertEquals(deleted, summary.getDeleted());
        assertEquals(summary, summary.toBuilder().build());
    }
}
