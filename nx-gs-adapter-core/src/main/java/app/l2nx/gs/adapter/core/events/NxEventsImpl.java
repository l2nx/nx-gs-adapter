package app.l2nx.gs.adapter.core.events;

import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/** Façade retargeted via {@link #swap} on reconnect; bad input is logged and dropped, never thrown into the game loop. */
final class NxEventsImpl implements EventsPublisher.AcknowledgedEvents {

    private static final NxLog log = NxLogFactory.getLogger(NxEventsImpl.class);

    private final AtomicReference<EventsPublisher> publisherRef;
    private final AtomicReference<EventTypeRegistry> registryRef;

    NxEventsImpl(EventsPublisher publisher, EventTypeRegistry registry) {
        this.publisherRef = new AtomicReference<EventsPublisher>(publisher);
        this.registryRef = new AtomicReference<EventTypeRegistry>(registry);
    }

    void swap(EventsPublisher next, EventTypeRegistry nextRegistry) {
        publisherRef.set(next);
        registryRef.set(nextRegistry);
    }

    @Override
    public void publish(@Nullable Object event) {
        dispatch(event, null);
    }

    @Override
    public void publish(Object event, EventsPublisher.SendCallback callback) {
        dispatch(event, callback);
    }

    /**
     * A callback is completed with a failure on every path that never reaches the queue; an
     * {@link UndeliverableEventException} where sending again cannot help.
     */
    private void dispatch(@Nullable Object event, EventsPublisher.@Nullable SendCallback callback) {
        if (event == null) {
            log.warn("publish called with null event — dropping");
            fail(callback, new UndeliverableEventException("null event"));
            return;
        }
        EventsPublisher publisher = publisherRef.get();
        EventTypeRegistry registry = registryRef.get();
        if (publisher == null || registry == null) {
            log.debug("publish called before publisher wired — dropping");
            fail(callback, new IllegalStateException("events publisher not wired"));
            return;
        }
        EventTypeBinding binding = registry.lookup(event.getClass());
        if (binding == null) {
            log.warn(
                    "No registered binding for event type {} — dropping",
                    event.getClass().getName());
            fail(
                    callback,
                    new UndeliverableEventException(
                            "no binding for " + event.getClass().getName()));
            return;
        }
        // disabled families must not burn queue capacity or inflate droppedTotal
        if (!publisher.isFamilyEnabled(binding.familyKey())) {
            log.debug("events.{} disabled — no topic configured; skipping publish", binding.familyKey());
            fail(callback, new UndeliverableEventException("events." + binding.familyKey() + " disabled"));
            return;
        }
        publisher.enqueue(new EventEnvelope(binding.wireNormalizer().apply(event), binding), callback);
    }

    private static void fail(EventsPublisher.@Nullable SendCallback callback, IllegalStateException failure) {
        if (callback != null) {
            callback.onCompletion(failure);
        }
    }

    @Override
    public boolean flush(long timeoutMs) {
        EventsPublisher publisher = publisherRef.get();
        if (publisher == null) {
            log.debug("flush called before publisher wired — nothing to flush");
            return true;
        }
        try {
            return publisher.flush(timeoutMs);
        } catch (Throwable t) {
            log.warn("flush threw {} — reporting incomplete", t.getClass().getName());
            return false;
        }
    }
}
