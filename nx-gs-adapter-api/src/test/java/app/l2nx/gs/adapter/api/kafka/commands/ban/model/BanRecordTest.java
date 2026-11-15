package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BanRecordTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z");

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BanRecord original = BanRecord.builder().id(11L).expiresAt(EXPIRES_AT).build();

        BanRecord copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
    }

    @Test
    void equals_shouldDistinguishPermanentFromTimed() {
        assertNotEquals(new BanRecord(11L, EXPIRES_AT), new BanRecord(11L, null));
    }
}
