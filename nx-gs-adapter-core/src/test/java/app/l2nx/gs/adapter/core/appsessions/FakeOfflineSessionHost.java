package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSessionPlacement;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** Records every call as {@code "<method>:<sessionId>[:<flag>]"}; placement and failures are scriptable. */
final class FakeOfflineSessionHost implements OfflineSessionHost {

    final List<String> calls = new ArrayList<String>();
    final List<OfflineSession> removed = new ArrayList<OfflineSession>();
    final List<Boolean> returnFlags = new ArrayList<Boolean>();
    Function<OfflineSession, OfflineSessionPlacement> placement = s -> OfflineSessionPlacement.placed(s.getSpotId());
    boolean failStartAction;
    final List<OfflineSession> startedSessions = new ArrayList<OfflineSession>();
    Consumer<OfflineSession> onStartAction = s -> {};
    /** When set, {@code removeFromWorld} blocks until it opens (at most 10 s), like a busy game thread. */
    volatile CountDownLatch removeGate;

    @Override
    public synchronized OfflineSessionPlacement placeInWorld(OfflineSession session) {
        calls.add("place:" + session.getId());
        return placement.apply(session);
    }

    @Override
    public synchronized void startAction(OfflineSession session) {
        calls.add("startAction:" + session.getId());
        startedSessions.add(session);
        if (failStartAction) {
            throw new IllegalStateException("simulated action failure");
        }
        onStartAction.accept(session);
    }

    @Override
    public synchronized void stopAction(OfflineSession session) {
        calls.add("stopAction:" + session.getId());
    }

    @Override
    public void removeFromWorld(OfflineSession session, boolean returnToOrigin) {
        CountDownLatch gate = removeGate;
        if (gate != null) {
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        record(session, returnToOrigin);
    }

    private synchronized void record(OfflineSession session, boolean returnToOrigin) {
        calls.add("remove:" + session.getId() + ":" + returnToOrigin);
        removed.add(session);
        returnFlags.add(returnToOrigin);
    }

    synchronized boolean called(String prefix) {
        for (String call : calls) {
            if (call.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
