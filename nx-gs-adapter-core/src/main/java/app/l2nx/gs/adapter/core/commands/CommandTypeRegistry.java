package app.l2nx.gs.adapter.core.commands;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.spi.capability.CommandHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

/**
 * Routing matches on {@code Class.getSimpleName()}, the {@code Message-Type}. Registration may race with lookup on
 * the consumer thread.
 */
final class CommandTypeRegistry {

    private final ConcurrentMap<String, CommandTypeBinding> bindingsByMessageType =
            new ConcurrentHashMap<String, CommandTypeBinding>();

    /**
     * @return {@code true} if the same class was bound before and its handler is replaced
     * @throws IllegalStateException when another class holds the {@code Message-Type}; its binding stays
     */
    <R, C extends NxCommand<R>> boolean register(Class<C> type, CommandHandler<C, R> handler) {
        String messageType = type.getSimpleName();
        boolean[] replaced = new boolean[1];
        bindingsByMessageType.compute(messageType, (key, previous) -> {
            if (previous != null && previous.commandClass() != type) {
                throw new IllegalStateException("Message-Type '" + messageType + "' is already registered by "
                        + previous.commandClass().getName() + "; refused registration of " + type.getName());
            }
            replaced[0] = previous != null;
            return new CommandTypeBinding(type, handler);
        });
        return replaced[0];
    }

    @Nullable
    CommandTypeBinding lookup(String messageType) {
        if (messageType == null) {
            return null;
        }
        return bindingsByMessageType.get(messageType);
    }

    /** Sorted for stable heartbeat output. */
    List<String> snapshotRegisteredTypes() {
        if (bindingsByMessageType.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>(bindingsByMessageType.keySet());
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }
}
