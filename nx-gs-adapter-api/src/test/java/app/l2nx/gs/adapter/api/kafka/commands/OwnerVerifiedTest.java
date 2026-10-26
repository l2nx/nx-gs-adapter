package app.l2nx.gs.adapter.api.kafka.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.chat.ChatAudiences;
import app.l2nx.gs.adapter.api.kafka.commands.chat.SendChatMessageCommand;
import app.l2nx.gs.adapter.api.kafka.commands.mail.AcceptMailCommand;
import app.l2nx.gs.adapter.api.kafka.commands.mail.SendPlayerMailCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.BuyFromPrivateStoreCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.SellToPrivateStoreCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreBuyCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStorePackageSellCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.StartPrivateStoreSellCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.StopPrivateStoreCommand;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.BuyLine;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.WantedLine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OwnerVerifiedTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static Stream<Arguments> verifiedCommands() {
        SellLine sell = new SellLine(1, 1L, 10L);
        BuyLine buy = BuyLine.builder()
                .itemId(1)
                .itemTemplateId(57L)
                .count(1L)
                .unitPriceAdena(100L)
                .build();
        return Stream.of(
                Arguments.of(new StopPrivateStoreCommand(1, true, null, TestInitiators.USER)),
                Arguments.of(new StartPrivateStoreSellCommand(
                        1,
                        "t",
                        Collections.singletonList(sell),
                        null,
                        null,
                        true,
                        null,
                        UUID.randomUUID(),
                        TestInitiators.USER)),
                Arguments.of(new StartPrivateStorePackageSellCommand(
                        1,
                        "t",
                        Collections.singletonList(sell),
                        null,
                        null,
                        true,
                        null,
                        UUID.randomUUID(),
                        TestInitiators.USER)),
                Arguments.of(new StartPrivateStoreBuyCommand(
                        1,
                        "t",
                        Collections.singletonList(new WantedLine(57L, 1L, 10L)),
                        5,
                        null,
                        true,
                        null,
                        UUID.randomUUID(),
                        TestInitiators.USER)),
                Arguments.of(new BuyFromPrivateStoreCommand(
                        1,
                        2,
                        Collections.singletonList(buy),
                        5,
                        Instant.parse("2026-08-11T12:00:00Z"),
                        "Courier",
                        "Subject",
                        "Body",
                        true,
                        TestInitiators.USER)),
                Arguments.of(new SellToPrivateStoreCommand(
                        1,
                        2,
                        Collections.singletonList(sell),
                        5,
                        Instant.parse("2026-08-11T12:00:00Z"),
                        "Courier",
                        "Subject",
                        "Body",
                        true,
                        TestInitiators.USER)),
                Arguments.of(new SendChatMessageCommand(
                        UUID.randomUUID(),
                        "CLAN",
                        ChatAudiences.CLAN,
                        7L,
                        100L,
                        null,
                        "APP",
                        "hello",
                        null,
                        true,
                        TestInitiators.USER)),
                Arguments.of(new SendPlayerMailCommand(
                        1L,
                        2L,
                        "title",
                        null,
                        null,
                        null,
                        null,
                        true,
                        java.util.UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001"),
                        Instant.parse("2026-08-11T12:00:00Z"),
                        TestInitiators.USER)),
                Arguments.of(new AcceptMailCommand(
                        1L,
                        10L,
                        2L,
                        0L,
                        null,
                        true,
                        java.util.UUID.fromString("5b1f3c2e-0000-4000-8000-000000000002"),
                        Instant.parse("2026-08-11T12:00:00Z"),
                        TestInitiators.USER)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verifiedCommands")
    void command_shouldImplementOwnerVerified(OwnerVerified command) {
        assertTrue(command.isOwnerVerified());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verifiedCommands")
    void gsonRoundTrip_shouldKeepOwnerVerified_whenTrue(Object command) {
        Object copy = GSON.fromJson(GSON.toJson(command), command.getClass());

        assertEquals(command, copy);
        assertTrue(assertInstanceOf(OwnerVerified.class, copy).isOwnerVerified());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verifiedCommands")
    void gsonDeserialize_shouldYieldNotVerified_whenFieldAbsent(Object command) {
        JsonObject json = GSON.toJsonTree(command).getAsJsonObject();
        json.remove("ownerVerified");

        Object copy = GSON.fromJson(json, command.getClass());

        assertFalse(assertInstanceOf(OwnerVerified.class, copy).isOwnerVerified());
    }

    @Test
    void builder_shouldDefaultToNotVerified() {
        assertFalse(StopPrivateStoreCommand.builder()
                .charId(1)
                .initiator(TestInitiators.USER)
                .build()
                .isOwnerVerified());
    }
}
