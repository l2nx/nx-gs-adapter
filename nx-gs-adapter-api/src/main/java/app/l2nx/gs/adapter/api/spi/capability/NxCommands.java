package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.spi.CommandContext;
import app.l2nx.gs.adapter.api.spi.ConnectContext;

/**
 * Registration of inbound command handlers, obtained via {@link ConnectContext#commands()}; implemented by
 * adapter-core. Registration is thread-safe and allowed after the consumer started. Routing matches
 * {@code Message-Type} against {@code Class.getSimpleName()}; registering the same class again replaces its handler.
 * With no commands topic configured, registrations are accepted but never invoked.
 *
 * @see CommandHandler
 * @see CommandContext
 */
public interface NxCommands {

    /**
     * Never blocks.
     *
     * @throws IllegalStateException when another class already holds the {@code Message-Type} of {@code type}; that
     *     class stays bound
     */
    <R, C extends NxCommand<R>> void on(Class<C> type, CommandHandler<C, R> handler);
}
