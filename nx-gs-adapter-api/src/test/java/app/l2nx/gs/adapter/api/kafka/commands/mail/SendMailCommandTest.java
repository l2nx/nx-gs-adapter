package app.l2nx.gs.adapter.api.kafka.commands.mail;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.mail.model.MailItem;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SendMailCommandTest {

    @Test
    void builder_shouldRoundtripFields() {
        MailItem item = MailItem.builder().itemTemplateId(57L).count(1_000L).build();

        SendMailCommand cmd = SendMailCommand.builder()
                .charId(42L)
                .author("admin")
                .title("Reward")
                .body("here is your loot")
                .items(Collections.singletonList(item))
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, cmd.getCharId().longValue());
        assertEquals("admin", cmd.getAuthor());
        assertEquals("Reward", cmd.getTitle());
        assertEquals("here is your loot", cmd.getBody());
        assertEquals(Collections.singletonList(item), cmd.getItems());
    }

    @Test
    void constructor_shouldRejectNullCharId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new SendMailCommand(null, "a", "t", "b", null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("charId"));
    }

    @Test
    void builder_shouldCarryAdena() {
        SendMailCommand cmd = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .adena(250L)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(250L, cmd.getAdena().longValue());
        assertEquals(cmd, cmd.toBuilder().build());
    }

    @Test
    void builder_shouldCarryIdempotencyKey() {
        UUID key = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");

        SendMailCommand cmd = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .idempotencyKey(key)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(key, cmd.getIdempotencyKey());
        assertEquals(cmd, cmd.toBuilder().build());
        assertNotEquals(cmd, cmd.toBuilder().idempotencyKey(UUID.randomUUID()).build());
    }

    @Test
    void constructor_shouldDefaultIdempotencyKeyToNull_whenOlderConstructors() {
        assertNull(new SendMailCommand(1L, "a", "t", "b", null, TestInitiators.USER).getIdempotencyKey());
        assertNull(SendMailCommand.builder()
                .charId(1L)
                .author("a")
                .title("t")
                .body("b")
                .adena(5L)
                .initiator(TestInitiators.USER)
                .build()
                .getIdempotencyKey());
    }

    @Test
    void gsonRoundTrip_shouldKeepIdempotencyKey() {
        Gson gson = new Gson();
        SendMailCommand cmd = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .adena(5L)
                .idempotencyKey(UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001"))
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(cmd, gson.fromJson(gson.toJson(cmd), SendMailCommand.class));
    }

    @Test
    void gson_shouldReadIdempotencyKeyAsNull_whenOldPayload() {
        SendMailCommand cmd =
                new Gson().fromJson("{\"charId\":1,\"title\":\"t\",\"items\":[],\"adena\":5}", SendMailCommand.class);

        assertNull(cmd.getIdempotencyKey());
        assertEquals(5L, cmd.getAdena().longValue());
    }

    @Test
    void constructor_shouldRejectNonPositiveAdena() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SendMailCommand.builder()
                        .charId(1L)
                        .author("a")
                        .title("t")
                        .body("b")
                        .adena(0L)
                        .initiator(TestInitiators.USER)
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SendMailCommand.builder()
                        .charId(1L)
                        .author("a")
                        .title("t")
                        .body("b")
                        .adena(-1L)
                        .initiator(TestInitiators.USER)
                        .build());
    }

    @Test
    void constructor_shouldDefaultAdenaToNull_whenFiveArgs() {
        assertNull(new SendMailCommand(1L, "a", "t", "b", null, TestInitiators.USER).getAdena());
    }

    @Test
    void constructor_shouldRejectNullTitle() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new SendMailCommand(1L, "a", null, "b", null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("title"));
    }

    @Test
    void constructor_shouldAllowNullAuthor() {
        SendMailCommand cmd = new SendMailCommand(1L, null, "t", "b", null, TestInitiators.USER);

        assertNull(cmd.getAuthor());
    }

    @Test
    void constructor_shouldAllowNullBody() {
        SendMailCommand cmd = new SendMailCommand(1L, "a", "t", null, null, TestInitiators.USER);

        assertNull(cmd.getBody());
    }

    @Test
    void getItems_shouldReturnEmpty_whenNull() {
        SendMailCommand cmd = new SendMailCommand(1L, "a", "t", "b", null, TestInitiators.USER);

        assertTrue(cmd.getItems().isEmpty());
    }

    @Test
    void getItems_shouldBeUnmodifiable() {
        MailItem item = MailItem.builder().itemTemplateId(57L).build();
        SendMailCommand cmd = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .items(Collections.singletonList(item))
                .initiator(TestInitiators.USER)
                .build();

        assertThrows(UnsupportedOperationException.class, () -> cmd.getItems().add(item));
    }

    @Test
    void constructor_shouldDefensivelyCopyItems() {
        MailItem item = MailItem.builder().itemTemplateId(57L).build();
        java.util.List<MailItem> mutable = new java.util.ArrayList<MailItem>(Collections.singletonList(item));

        SendMailCommand cmd = new SendMailCommand(1L, "a", "t", "b", mutable, TestInitiators.USER);
        mutable.add(MailItem.builder().itemTemplateId(58L).build());

        assertEquals(1, cmd.getItems().size());
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        SendMailCommand original = SendMailCommand.builder()
                .charId(42L)
                .author("admin")
                .title("Reward")
                .body("body")
                .items(Arrays.asList(
                        MailItem.builder().itemTemplateId(57L).count(1L).build(),
                        MailItem.builder().itemTemplateId(58L).count(2L).build()))
                .initiator(TestInitiators.USER)
                .build();

        SendMailCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void equals_shouldDistinguishOnCharId() {
        SendMailCommand a = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .initiator(TestInitiators.USER)
                .build();
        SendMailCommand b = SendMailCommand.builder()
                .charId(2L)
                .title("t")
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnTitle() {
        SendMailCommand a = SendMailCommand.builder()
                .charId(1L)
                .title("a")
                .initiator(TestInitiators.USER)
                .build();
        SendMailCommand b = SendMailCommand.builder()
                .charId(1L)
                .title("b")
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnAuthor() {
        SendMailCommand a = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .author("x")
                .initiator(TestInitiators.USER)
                .build();
        SendMailCommand b = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .author("y")
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnItems() {
        SendMailCommand a = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .items(Collections.singletonList(
                        MailItem.builder().itemTemplateId(57L).build()))
                .initiator(TestInitiators.USER)
                .build();
        SendMailCommand b = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .items(Collections.singletonList(
                        MailItem.builder().itemTemplateId(58L).build()))
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void hashCode_shouldMatchEquals() {
        SendMailCommand a = SendMailCommand.builder()
                .charId(1L)
                .author("x")
                .title("t")
                .body("b")
                .initiator(TestInitiators.USER)
                .build();
        SendMailCommand b = SendMailCommand.builder()
                .charId(1L)
                .author("x")
                .title("t")
                .body("b")
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_shouldExposeAllFields() {
        SendMailCommand cmd = SendMailCommand.builder()
                .charId(42L)
                .author("admin")
                .title("Reward")
                .body("body")
                .initiator(TestInitiators.USER)
                .build();

        String s = cmd.toString();

        assertTrue(s.contains("42"));
        assertTrue(s.contains("admin"));
        assertTrue(s.contains("Reward"));
        assertTrue(s.contains("body"));
    }

    @Test
    void implementsNxCommandMarker() {
        SendMailCommand cmd = SendMailCommand.builder()
                .charId(1L)
                .title("t")
                .initiator(TestInitiators.USER)
                .build();

        assertInstanceOf(NxCommand.class, cmd);
    }
}
