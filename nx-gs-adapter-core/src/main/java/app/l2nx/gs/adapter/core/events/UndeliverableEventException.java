package app.l2nx.gs.adapter.core.events;

import org.jspecify.annotations.Nullable;

/**
 * A {@link EventsPublisher.SendCallback} failure that sending the same event again cannot fix while this publisher is
 * bound: no binding for its type, its family has no topic, or the facade reports no acknowledgement. Every other
 * failure (a full queue, a broker error, a stop) may pass on a later send.
 */
public final class UndeliverableEventException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public UndeliverableEventException(String message) {
        super(message);
    }

    public UndeliverableEventException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
