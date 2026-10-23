package app.l2nx.gs.adapter.api.spi;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.capability.NxAppSessions;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

final class NoOpAppSessions implements NxAppSessions {

    static final NoOpAppSessions INSTANCE = new NoOpAppSessions();

    private NoOpAppSessions() {}

    @Override
    public CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request) {
        return CompletableFuture.completedFuture(AppSessionStartOutcome.unavailable("app sessions are not wired"));
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity) {
        return CompletableFuture.completedFuture(Optional.<OfflineSession>empty());
    }

    @Override
    public void onActionEnded(UUID sessionId, LogoutReason reason) {}

    @Override
    public void hostReady() {}

    @Override
    public ClientLoginOutcome onClientLogin(int characterId) {
        return ClientLoginOutcome.none();
    }

    @Override
    public Optional<OfflineSession> findActive(int characterId) {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson) {
        return CompletableFuture.completedFuture(Boolean.FALSE);
    }

    @Override
    public List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity) {
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> findLastStarted(UUID masterAccountId, ActivityType activity) {
        return CompletableFuture.completedFuture(Optional.<OfflineSession>empty());
    }
}
