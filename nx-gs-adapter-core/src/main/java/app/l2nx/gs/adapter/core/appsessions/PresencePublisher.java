package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.commons.UUIDv7;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.ArrayDeque;
import org.jspecify.annotations.Nullable;

/** A login can close a session before the platform connection exists; its end event waits until {@link #bind}. */
final class PresencePublisher {

    private static final NxLog log = NxLogFactory.getLogger(PresencePublisher.class);

    static final int MAX_PENDING = 10_000;

    private final ArrayDeque<CharacterPresenceEvent> pending = new ArrayDeque<CharacterPresenceEvent>();
    private @Nullable NxEvents events;

    synchronized void bind(NxEvents events) {
        this.events = events;
        CharacterPresenceEvent next;
        while ((next = pending.poll()) != null) {
            events.publish(next);
        }
    }

    synchronized void unbind() {
        this.events = null;
    }

    void online(OfflineSession session, boolean resumed) {
        publish(event(session, true, resumed, null));
    }

    void offline(OfflineSession session, LogoutReason reason) {
        publish(event(session, false, false, reason));
    }

    private synchronized void publish(CharacterPresenceEvent event) {
        NxEvents bound = events;
        if (bound != null) {
            bound.publish(event);
            return;
        }
        if (pending.size() >= MAX_PENDING) {
            CharacterPresenceEvent dropped = pending.poll();
            log.warn("App-session presence buffer full before connect, dropped the oldest event {}", dropped);
        }
        pending.add(event);
    }

    private static CharacterPresenceEvent event(
            OfflineSession session, boolean online, boolean resumed, @Nullable LogoutReason reason) {
        return CharacterPresenceEvent.builder()
                .eventId(UUIDv7.generate())
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
}
