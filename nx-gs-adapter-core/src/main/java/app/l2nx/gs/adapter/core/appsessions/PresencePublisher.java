package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.core.events.EventsPublisher.AcknowledgedEvents;
import app.l2nx.gs.adapter.core.events.EventsPublisher.SendCallback;
import app.l2nx.gs.adapter.core.events.UndeliverableEventException;
import app.l2nx.gs.commons.UUIDv7;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.ArrayDeque;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A login can close a session before the platform connection exists; its end event waits until {@link #bind}. */
final class PresencePublisher {

    private static final NxLog log = NxLogFactory.getLogger(PresencePublisher.class);

    static final int MAX_PENDING = 10_000;

    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private @Nullable NxEvents events;

    synchronized void bind(NxEvents events) {
        this.events = events;
        Pending next;
        while ((next = pending.poll()) != null) {
            send(events, next.event, next.acked);
        }
    }

    synchronized void unbind() {
        this.events = null;
    }

    void online(OfflineSession session, boolean resumed) {
        publish(event(session, true, resumed, null, UUIDv7.generate()), null);
    }

    /**
     * The end event of a closed row, identical on its first send and on every republish, {@code eventId} included.
     *
     * @throws RuntimeException when the row cannot make a valid event
     */
    static CharacterPresenceEvent endEvent(OfflineSession closed) {
        return event(closed, false, false, closed.getEndReason(), endEventId(closed));
    }

    /**
     * {@code acked} learns whether Kafka acknowledged the end, a drop before the platform connects included; an
     * {@link UndeliverableEventException} when no later send can be acknowledged either.
     */
    void sendEnd(CharacterPresenceEvent end, SendCallback acked) {
        publish(end, acked);
    }

    /**
     * A UUIDv7 dated by the end, whose other 74 bits are the session id's own (its counter and random bits): one id per
     * end, so the platform deduplicates a republish by {@code eventId} as well as by {@code sessionId}.
     */
    static UUID endEventId(OfflineSession closed) {
        return UUIDv7.withTimestamp(closed.getEndedAt(), closed.getId());
    }

    private synchronized void publish(CharacterPresenceEvent event, @Nullable SendCallback acked) {
        NxEvents bound = events;
        if (bound != null) {
            send(bound, event, acked);
            return;
        }
        if (pending.size() >= MAX_PENDING) {
            Pending dropped = pending.poll();
            log.warn("App-session presence buffer full before connect, dropped the oldest event {}", dropped.event);
            if (dropped.acked != null) {
                dropped.acked.onCompletion(new IllegalStateException("presence buffer full before connect"));
            }
        }
        pending.add(new Pending(event, acked));
    }

    private static void send(NxEvents events, CharacterPresenceEvent event, @Nullable SendCallback acked) {
        if (acked == null) {
            events.publish(event);
        } else if (events instanceof AcknowledgedEvents) {
            ((AcknowledgedEvents) events).publish(event, acked);
        } else {
            events.publish(event);
            acked.onCompletion(new UndeliverableEventException("the events facade reports no acknowledgement"));
        }
    }

    private static CharacterPresenceEvent event(
            OfflineSession session, boolean online, boolean resumed, @Nullable LogoutReason reason, UUID eventId) {
        return CharacterPresenceEvent.builder()
                .eventId(eventId)
                .charId(session.getCharacterId())
                .online(online)
                .source(SessionSource.APP)
                .activity(session.getActivity())
                .resumed(resumed)
                .logoutReason(reason)
                .sessionId(session.getId())
                .accountName(session.getAccountName())
                .ip(session.getInitiatorIp())
                .hwid(session.getHwid())
                .build();
    }

    private static final class Pending {
        final CharacterPresenceEvent event;
        final @Nullable SendCallback acked;

        Pending(CharacterPresenceEvent event, @Nullable SendCallback acked) {
            this.event = event;
            this.acked = acked;
        }
    }
}
