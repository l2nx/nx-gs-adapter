package app.l2nx.gs.adapter.api.kafka.sync.db;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class SyncEventTest {

    private static final long TS = 1_761_661_381_123L;

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        Payload payload = new Payload("Hellbound", 80);

        SyncEvent<Payload> event = SyncEvent.<Payload>builder()
                .entityName("clan")
                .pk(12345L)
                .op("UPDATED")
                .payload(payload)
                .timestampEpochMs(TS)
                .build();

        assertEquals("clan", event.getEntityName());
        assertEquals(12345L, event.getPk());
        assertEquals("UPDATED", event.getOp());
        assertEquals(payload, event.getPayload());
        assertEquals(TS, event.getTimestampEpochMs());
        assertEquals(event, event.toBuilder().build());
    }

    @Test
    void payload_shouldBeNullable_forDeleteTombstone() {
        SyncEvent<Payload> tombstone = SyncEvent.<Payload>builder()
                .entityName("clan")
                .pk(99L)
                .op("DELETED")
                .timestampEpochMs(TS)
                .build();

        assertNull(tombstone.getPayload());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SyncEvent.class).verify();
    }

    private static final class Payload {
        private final String name;
        private final int level;

        Payload(String name, int level) {
            this.name = name;
            this.level = level;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Payload)) return false;
            Payload p = (Payload) o;
            return level == p.level && java.util.Objects.equals(name, p.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, level);
        }
    }
}
