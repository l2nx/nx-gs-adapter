package app.l2nx.gs.adapter.core.commands;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.CommandResult;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.offline.fishing.CheckOfflineFishingCommand;
import app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionCheckResult;
import app.l2nx.gs.adapter.api.spi.capability.CommandHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandTypeRegistryTest {

    static final class FakeCommandA implements NxCommand<Void> {
        @Override
        public CommandInitiator getInitiator() {
            return null;
        }
    }

    static final class FakeCommandB implements NxCommand<Void> {
        @Override
        public CommandInitiator getInitiator() {
            return null;
        }
    }

    private static final CommandHandler<FakeCommandA, Void> NOOP_HANDLER_A = (cmd, ctx) -> CommandResult.<Void>ok();
    private static final CommandHandler<FakeCommandB, Void> NOOP_HANDLER_B = (cmd, ctx) -> CommandResult.<Void>ok();

    @Test
    void register_shouldReplaceTheHandlerAndReportIt_whenTheSameClassRegistersAgain() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        CommandHandler<FakeCommandA, Void> next = (cmd, ctx) -> CommandResult.<Void>ok();

        assertFalse(registry.register(FakeCommandA.class, NOOP_HANDLER_A));
        assertTrue(registry.register(FakeCommandA.class, NOOP_HANDLER_A));
        assertTrue(registry.register(FakeCommandA.class, next));

        assertSame(next, registry.lookup("FakeCommandA").handler());
    }

    static final class Other {
        static final class FakeCommandA implements NxCommand<Void> {
            @Override
            public CommandInitiator getInitiator() {
                return null;
            }
        }
    }

    @Test
    void register_shouldThrow_whenAnotherClassTakesTheSameMessageType() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        registry.register(FakeCommandA.class, NOOP_HANDLER_A);
        CommandHandler<Other.FakeCommandA, Void> clashing = (cmd, ctx) -> CommandResult.<Void>ok();

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> registry.register(Other.FakeCommandA.class, clashing));

        assertTrue(thrown.getMessage().contains("'FakeCommandA'"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(FakeCommandA.class.getName()), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(Other.FakeCommandA.class.getName()), thrown.getMessage());
        assertEquals(FakeCommandA.class, registry.lookup("FakeCommandA").commandClass());
    }

    @Test
    void lookup_unregisteredType_shouldReturnNull() {
        CommandTypeRegistry registry = new CommandTypeRegistry();

        assertNull(registry.lookup("Whatever"));
    }

    @Test
    void lookup_nullMessageType_shouldReturnNull() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        registry.register(FakeCommandA.class, NOOP_HANDLER_A);

        assertNull(registry.lookup(null));
    }

    @Test
    void snapshotRegisteredTypes_shouldBeSorted() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        registry.register(FakeCommandB.class, NOOP_HANDLER_B);
        registry.register(FakeCommandA.class, NOOP_HANDLER_A);

        List<String> snapshot = registry.snapshotRegisteredTypes();

        assertEquals("FakeCommandA", snapshot.get(0));
        assertEquals("FakeCommandB", snapshot.get(1));
    }

    @Test
    void snapshotRegisteredTypes_shouldBeUnmodifiable() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        registry.register(FakeCommandA.class, NOOP_HANDLER_A);

        List<String> snapshot = registry.snapshotRegisteredTypes();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("X"));
    }

    @Test
    void snapshotRegisteredTypes_emptyRegistry_shouldReturnEmptyList() {
        CommandTypeRegistry registry = new CommandTypeRegistry();

        assertTrue(registry.snapshotRegisteredTypes().isEmpty());
    }

    @Test
    void binding_shouldExposePreCachedReplyMessageTypeBytes() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        registry.register(FakeCommandA.class, NOOP_HANDLER_A);

        CommandTypeBinding binding = registry.lookup("FakeCommandA");

        assertNotNull(binding);
        assertArrayEquals(
                "FakeCommandAResult".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                binding.replyMessageTypeBytes());
    }

    @Test
    void register_shouldRouteACheckCommand_underItsSimpleNameWithItsResultReplyType() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        CommandHandler<CheckOfflineFishingCommand, AppSessionCheckResult> handler =
                (cmd, ctx) -> CommandResult.ok(AppSessionCheckResult.allowed(null));

        registry.register(CheckOfflineFishingCommand.class, handler);

        CommandTypeBinding binding = registry.lookup("CheckOfflineFishingCommand");
        assertNotNull(binding);
        assertEquals(CheckOfflineFishingCommand.class, binding.commandClass());
        assertSame(handler, binding.handler());
        assertArrayEquals(
                "CheckOfflineFishingResult".getBytes(StandardCharsets.UTF_8), binding.replyMessageTypeBytes());
    }
}
