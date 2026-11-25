package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.mail.model.MailItem;
import com.google.gson.Gson;
import java.util.ArrayList;
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

class SendMailCommandTest {

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID key = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
        List<MailItem> items = Arrays.asList(
                MailItem.builder().itemTemplateId(57L).count(1_000L).build(),
                MailItem.builder().itemTemplateId(58L).count(2L).build());

        SendMailCommand original = SendMailCommand.builder()
                .characterId(42L)
                .author("admin")
                .title("Reward")
                .body("here is your loot")
                .items(items)
                .adena(250L)
                .idempotencyKey(key)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, original.getCharacterId().longValue());
        assertEquals("admin", original.getAuthor());
        assertEquals("Reward", original.getTitle());
        assertEquals("here is your loot", original.getBody());
        assertEquals(items, original.getItems());
        assertEquals(250L, original.getAdena().longValue());
        assertEquals(key, original.getIdempotencyKey());
        assertEquals(original, original.toBuilder().build());
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("characterId", (Executable)
                        () -> new SendMailCommand(null, "a", "t", "b", null, TestInitiators.USER)),
                Arguments.of(
                        "title", (Executable) () -> new SendMailCommand(1L, "a", null, "b", null, TestInitiators.USER)),
                Arguments.of("adena", (Executable) () -> SendMailCommand.builder()
                        .characterId(1L)
                        .author("a")
                        .title("t")
                        .body("b")
                        .adena(0L)
                        .initiator(TestInitiators.USER)
                        .build()),
                Arguments.of("adena", (Executable) () -> SendMailCommand.builder()
                        .characterId(1L)
                        .author("a")
                        .title("t")
                        .body("b")
                        .adena(-1L)
                        .initiator(TestInitiators.USER)
                        .build()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidFields")
    void constructor_shouldReject_whenFieldInvalid(String field, Executable construction) {
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, construction);
        assertTrue(actual.getMessage().contains(field));
    }

    @Test
    void constructor_shouldLeaveOptionalFieldsNull_whenOmitted() {
        SendMailCommand sixArgs = new SendMailCommand(1L, "a", "t", "b", null, TestInitiators.USER);
        SendMailCommand noAuthor = new SendMailCommand(1L, null, "t", "b", null, TestInitiators.USER);
        SendMailCommand noBody = new SendMailCommand(1L, "a", "t", null, null, TestInitiators.USER);
        SendMailCommand noIdempotencyKey = SendMailCommand.builder()
                .characterId(1L)
                .author("a")
                .title("t")
                .body("b")
                .adena(5L)
                .initiator(TestInitiators.USER)
                .build();

        assertNull(sixArgs.getAdena());
        assertNull(sixArgs.getIdempotencyKey());
        assertNull(noAuthor.getAuthor());
        assertNull(noBody.getBody());
        assertNull(noIdempotencyKey.getIdempotencyKey());
    }

    @Test
    void gsonRoundTrip_shouldKeepIdempotencyKey() {
        Gson gson = new Gson();
        SendMailCommand cmd = SendMailCommand.builder()
                .characterId(1L)
                .title("t")
                .adena(5L)
                .idempotencyKey(UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001"))
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(cmd, gson.fromJson(gson.toJson(cmd), SendMailCommand.class));
        assertEquals(TestInitiators.USER, cmd.getInitiator());
    }

    @Test
    void gson_shouldReadIdempotencyKeyAsNull_whenOldPayload() {
        SendMailCommand cmd = new Gson()
                .fromJson("{\"characterId\":1,\"title\":\"t\",\"items\":[],\"adena\":5}", SendMailCommand.class);

        assertNull(cmd.getIdempotencyKey());
        assertEquals(5L, cmd.getAdena().longValue());
    }

    @Test
    void getItems_shouldReturnEmpty_whenNull() {
        SendMailCommand cmd = new SendMailCommand(1L, "a", "t", "b", null, TestInitiators.USER);

        assertTrue(cmd.getItems().isEmpty());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeItems() {
        MailItem item = MailItem.builder().itemTemplateId(57L).build();
        List<MailItem> mutable = new ArrayList<>(Collections.singletonList(item));

        SendMailCommand cmd = new SendMailCommand(1L, "a", "t", "b", mutable, TestInitiators.USER);
        mutable.add(MailItem.builder().itemTemplateId(58L).build());

        assertEquals(1, cmd.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> cmd.getItems().add(item));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SendMailCommand.class).verify();
    }
}
