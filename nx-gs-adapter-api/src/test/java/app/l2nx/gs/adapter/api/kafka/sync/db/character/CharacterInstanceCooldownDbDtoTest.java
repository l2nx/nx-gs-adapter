package app.l2nx.gs.adapter.api.kafka.sync.db.character;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class CharacterInstanceCooldownDbDtoTest {

    private static final Instant REENTRY = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterInstanceCooldownDbDto.class).verify();
    }

    @Test
    void constructor_shouldRejectNullReentryAt() {
        assertThrows(NullPointerException.class, () -> new CharacterInstanceCooldownDbDto(1, null));
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        CharacterInstanceCooldownDbDto original = CharacterInstanceCooldownDbDto.builder()
                .instanceId(42)
                .reentryAt(REENTRY)
                .build();

        assertEquals(42, original.getInstanceId());
        assertEquals(REENTRY, original.getReentryAt());
        assertEquals(new CharacterInstanceCooldownDbDto(42, REENTRY), original);
        assertEquals(original, original.toBuilder().build());
    }
}
