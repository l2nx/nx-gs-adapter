package app.l2nx.gs.adapter.core.events;

import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class EventsBootstrap {

    private static final NxLog log = NxLogFactory.getLogger(EventsBootstrap.class);

    private EventsBootstrap() {}

    /**
     * {@code serverId} keys the per-server events; without one they have no binding and are dropped. Null or empty
     * {@code familyTopics} disables every publish call (no-op + DEBUG log).
     */
    public static Started start(
            @Nullable UUID serverId,
            @Nullable Map<String, String> familyTopics,
            EventsPublisher.Sender sender,
            EventsPublisher.ProducerFlusher producerFlusher,
            EventsConfig config) {
        EventTypeRegistry registry = registry(serverId);
        EventsPublisher publisher = new EventsPublisher(familyTopics, sender, producerFlusher, config, registry);
        publisher.start();
        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        return new Started(publisher, events);
    }

    /** Rebuilds the publisher behind a stable façade so modules holding {@code ctx.events()} survive reconnect. */
    public static EventsPublisher swap(
            NxEvents facade,
            @Nullable UUID serverId,
            @Nullable Map<String, String> familyTopics,
            EventsPublisher.Sender sender,
            EventsPublisher.ProducerFlusher producerFlusher,
            EventsConfig config) {
        if (!(facade instanceof NxEventsImpl)) {
            throw new IllegalArgumentException("swap() requires a facade produced by EventsBootstrap.start(); got "
                    + (facade == null ? "null" : facade.getClass().getName()));
        }
        EventTypeRegistry registry = registry(serverId);
        EventsPublisher publisher = new EventsPublisher(familyTopics, sender, producerFlusher, config, registry);
        publisher.start();
        ((NxEventsImpl) facade).swap(publisher, registry);
        return publisher;
    }

    private static EventTypeRegistry registry(@Nullable UUID serverId) {
        if (serverId != null) {
            return new EventTypeRegistry(serverId);
        }
        log.warn("Events: the platform sent no server id, per-server events (app-sessions snapshot) are not published");
        return new EventTypeRegistry();
    }

    public static final class Started {

        private final EventsPublisher publisher;
        private final NxEvents events;

        Started(EventsPublisher publisher, NxEvents events) {
            this.publisher = publisher;
            this.events = events;
        }

        public EventsPublisher publisher() {
            return publisher;
        }

        public NxEvents events() {
            return events;
        }
    }
}
