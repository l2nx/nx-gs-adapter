package app.l2nx.gs.adapter.api.kafka.commands;

/**
 * Marker for inbound command DTOs sent on {@code <tenant>.gs.commands}; routed by the {@code Nx-Message-Type}
 * header (simple class name). The package split under {@code commands} is for discovery only; the topic is single.
 *
 * @param <R> success-payload type; {@link Void} when there is none
 * @see CommandResult
 * @see app.l2nx.gs.adapter.api.spi.capability.CommandHandler
 * @see app.l2nx.gs.adapter.api.spi.capability.NxCommands
 */
public interface NxCommand<R> {

    /** Required; the consumer refuses a missing or invalid one with {@code VALIDATION_FAILED}, and requires {@code source = user} on an {@link OwnerVerified} command. */
    CommandInitiator getInitiator();
}
