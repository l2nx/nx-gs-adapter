package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionCheckOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.session.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The engine enforces only the table's invariants; every business rule is the host's, checked before {@link #start}.
 * Where the engine does not run, starts answer {@code UNAVAILABLE}, lookups find nothing and the rest does nothing.
 * No method throws; never wait for a returned future on the adapter IO pool ({@code ctx.io()}).
 */
public interface NxAppSessions {

    /** Idempotent by {@link AppSessionStartRequest#getIdempotencyKey()}. */
    CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request);

    /**
     * Check mode of {@link #start}: runs the engine's start checks in the start's order and writes nothing, takes no
     * spot, places no character and calls no {@link OfflineSessionHost} method. Refusals match the start's, same data;
     * skipping {@code placeInWorld} misses none, since it repeats rules the host's steps already checked.
     *
     * @param spotPool validated and trimmed by {@link AppSessionStartRequest#normalizeSpotPool}, as a start's
     * @param spotId validated by {@link AppSessionStartRequest#requireValidSpotId}, as a start's
     */
    CompletableFuture<AppSessionCheckOutcome> check(int characterId, String spotPool, int spotId);

    /**
     * Stops the character's open session of {@code activity} with {@code APP_STOPPED}, or with
     * {@code APP_STOPPED_BEFORE_RESTORE} when the restore after a restart has not reached it yet.
     *
     * @return the closed session, or empty when none is open (an idempotent success)
     */
    CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity);

    /** The host's action ended on its own; closes asynchronously, so the game thread may call it. */
    void onActionEnded(UUID sessionId, LogoutReason reason);

    /**
     * Restore of sessions open before a restart waits for this call; without it they are never restored. Call it after
     * the host's own offline characters are back. Any thread; early and repeated calls are safe.
     */
    void hostReady();

    /**
     * Closes the character's open session with {@code APP_LOGIN} and returns only when it is closed. On timeout (15 s,
     * plus up to 30 s while booting) the result is {@link ClientLoginOutcome.Status#PENDING}: refuse the login.
     * Must not run on the thread {@link OfflineSessionHost} methods hop to, unless those hops run inline.
     */
    ClientLoginOutcome onClientLogin(int characterId);

    /**
     * In-memory, so the game thread may call it. An ended or rolled-back session whose table write failed is still
     * returned until a retry lands, because the table keeps the character busy until then.
     */
    Optional<OfflineSession> findActive(int characterId);

    /**
     * Inside an {@code OfflineSessionHost} callback of the same session it runs inline; from a thread that callback
     * hopped to it runs only after the callback returns, so waiting there deadlocks.
     *
     * @return {@code false} when the session is not open
     */
    CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson);

    /**
     * Sessions that still hold their character, ordered by {@link OfflineSession#getStartedAt()}, then id; in-memory.
     * The check and a later {@link #start} are two steps: the host must serialize them per master account.
     */
    List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity);

    /**
     * Like {@link #findOpenByMasterAccount} for every account. Before {@link #hostReady()} it already lists sessions
     * awaiting the restore, so the host can keep their spots out of a free-spot pick.
     */
    List<OfflineSession> findOpen(ActivityType activity);

    /**
     * Latest start among the rows the table keeps; ties go to the greater id. A failed read, or one while the engine
     * does not run, completes exceptionally: refuse the start as unavailable, never as "no previous session".
     */
    CompletableFuture<Optional<OfflineSession>> findLastStartedByMasterAccount(
            UUID masterAccountId, ActivityType activity);

    /** Per character; same rules as {@link #findLastStartedByMasterAccount(UUID, ActivityType)}. */
    CompletableFuture<Optional<OfflineSession>> findLastStartedByCharacter(int characterId, ActivityType activity);
}
