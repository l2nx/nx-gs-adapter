package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class StartPrivateStoreSessionFieldsTest {

    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final Instant DEADLINE = Instant.parse("2026-08-11T12:00:00Z");
    private static final SellLine LINE = new SellLine(1, 1L, 100L);

    private static StartPrivateStoreSellCommand.Builder sell() {
        return StartPrivateStoreSellCommand.builder()
                .characterId(1)
                .lines(Collections.singletonList(LINE))
                .idempotencyKey(KEY)
                .initiator(TestInitiators.USER);
    }

    static Stream<Arguments> buildersWithoutIdempotencyKey() {
        Executable sell = sell().idempotencyKey(null)::build;
        Executable packageSell = StartPrivateStorePackageSellCommand.builder()
                .characterId(1)
                .lines(Collections.singletonList(LINE))
                .initiator(TestInitiators.USER)::build;
        return Stream.of(Arguments.of("sell", sell), Arguments.of("package-sell", packageSell));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buildersWithoutIdempotencyKey")
    void constructor_shouldReject_whenIdempotencyKeyNull(String name, Executable build) {
        assertThrows(NullPointerException.class, build);
    }

    @Test
    void toBuilder_shouldRoundtripAllFields_forBothSubclasses() {
        StartPrivateStoreSellCommand minimal = sell().build();
        StartPrivateStoreSellCommand sell = sell().title("WTS")
                .sellerFeePercent(5)
                .offlineAllowed(true)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .build();
        StartPrivateStorePackageSellCommand packageSell = StartPrivateStorePackageSellCommand.builder()
                .characterId(1)
                .title("WTS")
                .lines(Collections.singletonList(LINE))
                .sellerFeePercent(5)
                .offlineAllowed(true)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(minimal, minimal.toBuilder().build());
        assertEquals(sell, sell.toBuilder().build());
        assertEquals(KEY, sell.getIdempotencyKey());
        assertEquals(packageSell, packageSell.toBuilder().build());
        assertEquals(TestInitiators.USER, packageSell.getInitiator());
        assertEquals(1, sell.getCharacterId());
        assertEquals(TestInitiators.USER, sell.getInitiator());
        assertTrue(sell.getOfflineAllowed());
        assertTrue(sell.isOwnerVerified());
        assertEquals(DEADLINE, sell.getDeadline());
        assertEquals(1, packageSell.getCharacterId());
        assertEquals("WTS", packageSell.getTitle());
        assertEquals(Collections.singletonList(LINE), packageSell.getLines());
        assertEquals(5, packageSell.getSellerFeePercent());
        assertTrue(packageSell.getOfflineAllowed());
        assertTrue(packageSell.isOwnerVerified());
        assertEquals(DEADLINE, packageSell.getDeadline());
        assertEquals(KEY, packageSell.getIdempotencyKey());
        assertEquals("WTS", sell.getTitle());
        assertEquals(Collections.singletonList(LINE), sell.getLines());
        assertEquals(5, sell.getSellerFeePercent());
    }

    @ParameterizedTest(name = "[{0}] -> {1}")
    @CsvSource({"'  ',", "' WTS ', WTS"})
    void title_shouldBeTrimmedOrNull_whenBlank(String title, String expected) {
        assertEquals(expected, sell().title(title).build().getTitle());
    }

    @Test
    void sellLine_shouldRoundtripAllFields() {
        SellLine original =
                SellLine.builder().itemId(7).count(2L).priceAdena(100L).build();

        assertEquals(7, original.getItemId());
        assertEquals(2L, original.getCount());
        assertEquals(100L, original.getPriceAdena());
        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(StartPrivateStoreSellCommand.class)
                .usingGetClass()
                .verify();
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
        assertEquals("SELL", app.getStoreType());
        assertEquals(1, app.getAcceptedCount());
    }
}
