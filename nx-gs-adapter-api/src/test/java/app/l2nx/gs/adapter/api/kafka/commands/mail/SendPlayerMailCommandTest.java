package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

class SendPlayerMailCommandTest {

    private static final Instant DEADLINE = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static SendPlayerMailCommand.Builder valid() {
        return SendPlayerMailCommand.builder()
                .senderCharId(1L)
                .recipientCharId(2L)
                .title("Hello")
                .idempotencyKey(KEY)
                .deadline(DEADLINE)
                .initiator(TestInitiators.USER);
    }

    @Test
    void builder_shouldRoundtripFields() {
        List<PlayerMailItem> items = Arrays.asList(new PlayerMailItem(10L, 3L), new PlayerMailItem(11L, 1L));

        SendPlayerMailCommand cmd = valid().body("text")
                .items(items)
                .adena(500L)
                .codPrice(900L)
                .ownerVerified(true)
                .build();

        assertEquals(1L, cmd.getSenderCharId().longValue());
        assertEquals(2L, cmd.getRecipientCharId().longValue());
        assertEquals("Hello", cmd.getTitle());
        assertEquals("text", cmd.getBody());
        assertEquals(items, cmd.getItems());
        assertEquals(500L, cmd.getAdena().longValue());
        assertEquals(900L, cmd.getCodPrice().longValue());
        assertTrue(cmd.isOwnerVerified());
        assertEquals(KEY, cmd.getIdempotencyKey());
        assertEquals(DEADLINE, cmd.getDeadline());
        assertEquals(cmd, cmd.toBuilder().build());
    }

    @Test
    void command_shouldBeNxCommandOfResult() {
        NxCommand<SendPlayerMailResult> cmd = valid().build();

        assertFalse(((SendPlayerMailCommand) cmd).isOwnerVerified());
    }

    @Test
    void constructor_shouldRejectNullDeadline() {
        SendPlayerMailCommand.Builder builder = valid().deadline(null);

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void constructor_shouldRejectNullIdempotencyKey() {
        SendPlayerMailCommand.Builder builder = valid().idempotencyKey(null);

        NullPointerException ex = assertThrows(NullPointerException.class, builder::build);
        assertTrue(ex.getMessage().contains("idempotencyKey"));
    }

    @Test
    void equals_shouldDifferByIdempotencyKey() {
        assertNotEquals(
                valid().build(), valid().idempotencyKey(UUID.randomUUID()).build());
    }

    @Test
    void constructor_shouldRejectNonPositiveAdenaAndCodPrice() {
        assertThrows(IllegalArgumentException.class, () -> valid().adena(0L).build());
        assertThrows(IllegalArgumentException.class, () -> valid().adena(-1L).build());
        assertThrows(IllegalArgumentException.class, () -> valid().codPrice(0L).build());
        assertThrows(IllegalArgumentException.class, () -> valid().codPrice(-5L).build());
    }

    @Test
    void gsonRoundTrip_shouldWriteIdempotencyKeyOnWire() {
        assertTrue(GSON.toJson(valid().build()).contains("\"idempotencyKey\":\"" + KEY + "\""));
    }

    @ParameterizedTest
    @NullSource
    void constructor_shouldRejectNullRequiredFields(Long nullId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().senderCharId(nullId).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().recipientCharId(nullId).build());
        assertThrows(IllegalArgumentException.class, () -> valid().title(null).build());
    }

    @Test
    void constructor_shouldNormalizeNullItemsToEmpty() {
        SendPlayerMailCommand cmd = valid().items(null).build();

        assertTrue(cmd.getItems().isEmpty());
        assertNull(cmd.getAdena());
        assertNull(cmd.getCodPrice());
    }

    @Test
    void getItems_shouldBeUnmodifiable() {
        SendPlayerMailCommand cmd = valid().items(Collections.singletonList(new PlayerMailItem(1L, 1L)))
                .build();

        assertThrows(UnsupportedOperationException.class, () -> cmd.getItems().clear());
    }

    @Test
    void equals_shouldDifferByDeadline() {
        assertNotEquals(
                valid().build(), valid().deadline(DEADLINE.plusSeconds(1)).build());
    }

    @Test
    void gsonRoundTrip_shouldKeepAllFields() {
        SendPlayerMailCommand cmd = valid().body("text")
                .items(Collections.singletonList(new PlayerMailItem(10L, 3L)))
                .adena(500L)
                .codPrice(900L)
                .ownerVerified(true)
                .build();

        SendPlayerMailCommand copy = GSON.fromJson(GSON.toJson(cmd), SendPlayerMailCommand.class);

        assertEquals(cmd, copy);
    }

    @Test
    void gsonRoundTrip_shouldKeepResult() {
        SendPlayerMailResult result = new SendPlayerMailResult(10243L, 1100L);

        assertEquals(result, GSON.fromJson(GSON.toJson(result), SendPlayerMailResult.class));
        assertEquals("{\"mailId\":10243,\"feeAdena\":1100}", GSON.toJson(result));
    }

    @Test
    void playerMailItem_shouldRejectNonPositiveCount() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerMailItem(1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PlayerMailItem(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new PlayerMailItem(1L, null));
    }
}
