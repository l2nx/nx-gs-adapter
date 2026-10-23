package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The adapter's app-session engine as the host sees it; the same instance comes from the contexts and
 * {@code NxAdapter.appSessions()}, usable from JVM start.
 *
 * <p>The engine enforces only the table's invariants (one open session per character, one character per spot, one
 * session per idempotency key); every business rule is the host's, checked before {@link #start}. Where the engine
 * does not run, starts answer {@code UNAVAILABLE}, lookups find nothing and the rest does nothing.</p>
 *
 * <p>No method throws. Never wait for a returned future on the adapter IO pool ({@code ctx.io()}), which runs the
 * work.</p>
 */
public interface NxAppSessions {

    /**
     * Idempotent by {@link AppSessionStartRequest#getIdempotencyKey()}. Has the host place the character
     * ({@link OfflineSessionHost#placeInWorld}), then publishes the {@code online=true, source=APP} presence event.
     */
    CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request);

    /**
     * Stops the character's open session of {@code activity} with {@code APP_STOPPED}, or with
     * {@code APP_STOPPED_BEFORE_RESTORE} when the restore after a restart has not reached it yet.
     *
     * @return the closed session, or empty when none is open (an idempotent success)
     */
    CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity);

    /**
     * The host's action ended on its own (out of bait, daily limit, inventory full, sold out, ban, kick). Closes the
     * session with {@code reason} asynchronously, so the game thread may call it. Any other reason, or an unknown
     * session, is logged and ignored.
     */
    void onActionEnded(UUID sessionId, LogoutReason reason);

    /**
     * The host's world is loaded and can take characters back. Sessions open before a restart are restored only after
     * this call and once the {@code app-sessions} module has started, whichever comes last; without the call they are
     * never restored.
     *
     * <p>Call it after the host's own offline characters are back. Any thread; a call before the engine has booted is
     * remembered, a second call does nothing.</p>
     */
    void hostReady();

    /**
     * A client is logging in to {@code characterId}. Closes the character's open session (also one waiting for its
     * restore) with {@code APP_LOGIN} and returns only when it is closed, so the host calls it under the character's
     * mutation lock before loading the live character.
     *
     * <p>The wait is bounded (15 s, plus up to 30 s while the engine boots after a restart). On timeout the result is
     * {@link ClientLoginOutcome.Status#PENDING}: refuse the login with "try again"; the close goes on in the
     * background.</p>
     *
     * <p>Must not run on the thread {@link OfflineSessionHost} methods hop to, unless those hops run inline: the close
     * calls them and waits.</p>
     */
    ClientLoginOutcome onClientLogin(int characterId);

    /**
     * The character's open session, including one waiting for its restore; in-memory, so the game thread may call it.
     *
     * <p>An ended session (carrying {@code endedAt} and {@code endReason}) or rolled-back start whose table write
     * failed is still returned until a retry lands, because the table keeps the character busy until then.</p>
     */
    Optional<OfflineSession> findActive(int characterId);

    /**
     * Stores the host's per-type JSON of an open session. Inside an {@code OfflineSessionHost} callback of the same
     * session it runs inline; from a thread that callback hopped to it runs only after the callback returns, so
     * waiting there deadlocks.
     *
     * @return {@code false} when the session is not open
     */
    CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson);

    /**
     * Every session of {@code activity} of the master account that still holds its character (running, awaiting
     * restore, start in flight, or end not yet taken by the table), ordered by {@link OfflineSession#getStartedAt()},
     * then id. In-memory, so the game thread may call it.
     *
     * <p>Never matches a session without a master account. The check and a later {@link #start} are two steps: starts
     * for different characters of one master account both pass unless the host serializes them per account.</p>
     */
    List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity);

    /**
     * The session of {@code activity} of the master account with the latest real start
     * ({@link OfflineSession#getStartedAt()}), open or closed, among those the table still keeps; ties go to the greater
     * id. Starts in flight or rolled back and sessions without a master account never count.
     *
     * <p>Reads the table on the adapter IO pool. A database failure completes the future exceptionally: the host refuses
     * the start as temporarily unavailable, never as "no previous session".</p>
     */
    CompletableFuture<Optional<OfflineSession>> findLastStarted(UUID masterAccountId, ActivityType activity);
}
