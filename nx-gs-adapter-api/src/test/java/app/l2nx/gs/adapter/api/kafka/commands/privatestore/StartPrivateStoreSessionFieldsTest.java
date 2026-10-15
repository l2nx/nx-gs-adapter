package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StartPrivateStoreSessionFieldsTest {

    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final SellLine LINE = new SellLine(1, 1L, 100L);

    private static StartPrivateStoreSellCommand.Builder sell() {
        return StartPrivateStoreSellCommand.builder()
                .charId(1)
                .lines(Collections.singletonList(LINE))
                .idempotencyKey(KEY)
                .initiatorIp("1.2.3.4");
    }

    @Test
    void constructor_shouldRejectNullIdempotencyKey() {
        assertThrows(
                NullPointerException.class, () -> sell().idempotencyKey(null).build());
        assertThrows(
                NullPointerException.class,
                () -> StartPrivateStorePackageSellCommand.builder()
                        .charId(1)
                        .lines(Collections.singletonList(LINE))
                        .build());
    }

    @Test
    void toBuilder_shouldRoundtripSessionFields_forBothSubclasses() {
        StartPrivateStoreSellCommand sell = sell().build();
        StartPrivateStorePackageSellCommand packageSell = StartPrivateStorePackageSellCommand.builder()
                .charId(1)
                .lines(Collections.singletonList(LINE))
                .idempotencyKey(KEY)
                .initiatorIp("1.2.3.4")
                .build();

        assertEquals(sell, sell.toBuilder().build());
        assertEquals(KEY, sell.toBuilder().build().getIdempotencyKey());
        assertEquals("1.2.3.4", packageSell.toBuilder().build().getInitiatorIp());
    }

    @Test
    void initiatorIp_shouldBeNull_whenBlank() {
        assertNull(sell().initiatorIp("  ").build().getInitiatorIp());
    }

    @Test
    void equals_shouldDistinguishIdempotencyKeyAndInitiatorIp() {
        StartPrivateStoreSellCommand base = sell().build();

        assertNotEquals(base, sell().idempotencyKey(UUID.randomUUID()).build());
        assertNotEquals(base, sell().initiatorIp("5.6.7.8").build());
    }

    @Test
    void result_shouldCarrySessionId_andNullByDefault() {
        StartPrivateStoreResult plain = StartPrivateStoreResult.builder()
                .storeType("SELL")
                .acceptedCount(1)
                .build();
        StartPrivateStoreResult app = plain.toBuilder().sessionId(KEY).build();

        assertNull(plain.getSessionId());
        assertEquals(KEY, app.getSessionId());
        assertNotEquals(plain, app);
        assertEquals(app, app.toBuilder().build());
    }
}
