package app.l2nx.gs.adapter.api.spi;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.capability.NxAppSessions;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionCheckOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.session.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
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
    public CompletableFuture<AppSessionCheckOutcome> check(int characterId, String spotPool, int spotId) {
        return CompletableFuture.completedFuture(AppSessionCheckOutcome.unavailable("app sessions are not wired"));
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
    public List<OfflineSession> findOpen(ActivityType activity) {
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> findLastStartedByMasterAccount(
            UUID masterAccountId, ActivityType activity) {
        return notWired();
    }

    @Override
    public CompletableFuture<Optional<OfflineSession>> findLastStartedByCharacter(
            int characterId, ActivityType activity) {
        return notWired();
    }

    private static CompletableFuture<Optional<OfflineSession>> notWired() {
        CompletableFuture<Optional<OfflineSession>> failed = new CompletableFuture<Optional<OfflineSession>>();
        failed.completeExceptionally(new IllegalStateException("app sessions are not wired"));
        return failed;
    }
}
