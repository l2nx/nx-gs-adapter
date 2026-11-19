package app.l2nx.gs.adapter.core.commands;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.CommandResult;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.spi.capability.CommandHandler;
import org.junit.jupiter.api.Test;

class NxCommandsImplTest {

    static final class FakeCommand implements NxCommand<Void> {
        @Override
        public CommandInitiator getInitiator() {
            return null;
        }
    }

    static final class Other {
        static final class FakeCommand implements NxCommand<Void> {
            @Override
            public CommandInitiator getInitiator() {
                return null;
            }
        }
    }

    private static final CommandHandler<FakeCommand, Void> FIRST = (cmd, ctx) -> CommandResult.<Void>ok();
    private static final CommandHandler<Other.FakeCommand, Void> CLASHING = (cmd, ctx) -> CommandResult.<Void>ok();

    @Test
    void on_shouldThrowNamingTheMessageType_whenAnotherClassTakesIt() {
        CommandTypeRegistry registry = new CommandTypeRegistry();
        NxCommandsImpl commands = new NxCommandsImpl(registry);
        commands.on(FakeCommand.class, FIRST);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> commands.on(Other.FakeCommand.class, CLASHING));

        assertTrue(thrown.getMessage().contains("'FakeCommand'"), thrown.getMessage());
        assertSame(FIRST, registry.lookup("FakeCommand").handler());
    }
}
