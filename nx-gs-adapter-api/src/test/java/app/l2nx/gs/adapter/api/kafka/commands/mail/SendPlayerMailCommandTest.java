package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
                .senderCharacterId(1L)
                .recipientCharacterId(2L)
                .title("Hello")
                .idempotencyKey(KEY)
                .deadline(DEADLINE)
                .initiator(TestInitiators.USER);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SendPlayerMailCommand.class).verify();
    }

    @Test
    void gsonRoundTrip_shouldWriteIdempotencyKeyOnWire() {
        assertTrue(GSON.toJson(valid().build()).contains("\"idempotencyKey\":\"" + KEY + "\""));
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                invalid(
                        "deadline",
                        NullPointerException.class,
                        () -> valid().deadline(null).build()),
                invalid(
                        "idempotencyKey",
                        NullPointerException.class,
                        () -> valid().idempotencyKey(null).build()),
                invalid(
                        "senderCharacterId",
                        IllegalArgumentException.class,
                        () -> valid().senderCharacterId(null).build()),
                invalid(
                        "recipientCharacterId",
                        IllegalArgumentException.class,
                        () -> valid().recipientCharacterId(null).build()),
                invalid(
                        "title",
                        IllegalArgumentException.class,
                        () -> valid().title(null).build()),
                invalid(
                        "adena",
                        IllegalArgumentException.class,
                        () -> valid().adena(0L).build()),
                invalid(
                        "adena",
                        IllegalArgumentException.class,
                        () -> valid().adena(-1L).build()),
                invalid(
                        "codPrice",
                        IllegalArgumentException.class,
                        () -> valid().codPrice(0L).build()),
                invalid(
                        "codPrice",
                        IllegalArgumentException.class,
                        () -> valid().codPrice(-5L).build()),
                invalid("count", IllegalArgumentException.class, () -> new PlayerMailItem(1L, 0L)),
                invalid("itemId", IllegalArgumentException.class, () -> new PlayerMailItem(null, 1L)),
                invalid("count", IllegalArgumentException.class, () -> new PlayerMailItem(1L, null)));
    }

    private static Arguments invalid(String field, Class<? extends Exception> expected, Executable build) {
        return Arguments.of(field, expected, build);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidValues")
    void constructor_shouldReject_whenValueIsMissingOrNotPositive(
            String field, Class<? extends Exception> expected, Executable build) {
        Exception ex = assertThrows(expected, build);
        assertTrue(ex.getMessage().contains(field), ex.getMessage());
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
    void gsonRoundTrip_shouldKeepAllFields() {
        List<PlayerMailItem> items = Arrays.asList(new PlayerMailItem(10L, 3L), new PlayerMailItem(11L, 1L));

        SendPlayerMailCommand cmd = valid().body("text")
                .items(items)
                .adena(500L)
                .codPrice(900L)
                .ownerVerified(true)
                .build();

        assertEquals(1L, cmd.getSenderCharacterId().longValue());
        assertEquals(2L, cmd.getRecipientCharacterId().longValue());
        assertEquals("Hello", cmd.getTitle());
        assertEquals("text", cmd.getBody());
        assertEquals(items, cmd.getItems());
        assertEquals(500L, cmd.getAdena().longValue());
        assertEquals(900L, cmd.getCodPrice().longValue());
        assertTrue(cmd.isOwnerVerified());
        assertEquals(KEY, cmd.getIdempotencyKey());
        assertEquals(DEADLINE, cmd.getDeadline());
        assertEquals(cmd, cmd.toBuilder().build());
        assertEquals(cmd, GSON.fromJson(GSON.toJson(cmd), SendPlayerMailCommand.class));
        assertEquals(TestInitiators.USER, cmd.getInitiator());
        assertEquals(10L, cmd.getItems().get(0).getItemId());
        assertEquals(3L, cmd.getItems().get(0).getCount());
    }

    @Test
    void gsonRoundTrip_shouldKeepResult() {
        SendPlayerMailResult result = new SendPlayerMailResult(10243L, 1100L);

        assertEquals(result, GSON.fromJson(GSON.toJson(result), SendPlayerMailResult.class));
        assertEquals("{\"mailId\":10243,\"feeAdena\":1100}", GSON.toJson(result));
        assertEquals(10243L, result.getMailId());
        assertEquals(1100L, result.getFeeAdena());
    }
}
