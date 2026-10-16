package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartOutcome;
import app.l2nx.gs.adapter.api.spi.model.AppSessionStartRequest;
import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.OfflineSessionHost;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The adapter's app-session engine as the host sees it. Reached through {@code CommandContext.appSessions()},
 * {@code ConnectContext.appSessions()} or {@code NxAdapter.appSessions()}: the same instance, usable from JVM start.
 *
 * <p>The engine enforces only the session table's invariants (one open session per character, one character per spot,
 * one session per idempotency key); every business rule is the host's, checked before {@link #start}. Where the engine
 * does not run (no {@link OfflineSessionHost}, adapter disabled or failed) starts answer {@code UNAVAILABLE}, lookups
 * find nothing and the rest does nothing.</p>
 *
 * <p>No method throws into the caller. Futures complete on adapter threads; never wait for one on the adapter IO pool
 * ({@code ctx.io()}), which runs the work.</p>
 */
public interface NxAppSessions {

    /**
     * Stores the session, has the host place the character ({@link OfflineSessionHost#placeInWorld}) and start its
     * action, then publishes the {@code online=true, source=APP} presence event. Idempotent by
     * {@link AppSessionStartRequest#getIdempotencyKey()}.
     */
    CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request);

    /**
     * Stops the character's open session of {@code activity} with {@code APP_STOPPED}, or with
     * {@code APP_STOPPED_BEFORE_RESTORE} when the restore after a restart has not reached it yet. The activity keeps a
     * stop command of one session type from closing a session of another.
     *
     * @return the closed session, or empty when the character has no open session of that activity (an idempotent
     *     success for the caller)
     */
    CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity);

    /**
     * The host's action ended on its own (out of bait, daily limit, inventory full, sold out, ban, kick). Closes the
     * session with {@code reason} asynchronously and returns at once, so the game thread may call it. Any other
     * reason, or an unknown session, is logged and ignored.
     */
    void onActionEnded(UUID sessionId, LogoutReason reason);

    /**
     * A client is logging in to {@code characterId}. Closes the character's open session (also one still waiting for
     * its restore) with {@code APP_LOGIN} and returns only when it is closed, so the host calls it under the
     * character's mutation lock before loading the live character.
     *
     * <p>The wait is bounded (15 s for the close; up to 30 s more while the engine boots after a restart). When it runs
     * out the result is {@link ClientLoginOutcome.Status#PENDING}: refuse this login with "try again"; the close goes
     * on in the background.</p>
     *
     * <p>Must not be called on the thread {@link OfflineSessionHost} methods hop to, unless those hops run inline when
     * already on it: the close calls them and waits.</p>
     */
    ClientLoginOutcome onClientLogin(int characterId);

    /**
     * The character's open session, including one waiting for its restore after a restart; in-memory, so the game
     * thread may call it.
     *
     * <p>A session the engine ended, or a start it rolled back, whose table write failed is still returned until a
     * retry reaches the table, because the table keeps the character busy until then: an ended one carries
     * {@code endedAt} and {@code endReason}.</p>
     */
    Optional<OfflineSession> findActive(int characterId);

    /**
     * Stores the host's per-type JSON of an open session.
     *
     * @return {@code false} when the session is not open
     */
    CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson);

    /**
     * Publishes the server's app settings; call at start and after every applied change. A snapshot equal to the last
     * published one (ignoring {@code updatedAt}) is not sent again; the last one is resent after a reconnect and, before
     * the platform connection exists, kept and sent on connect.
     */
    void publishSettings(AppSettings settings);
}
