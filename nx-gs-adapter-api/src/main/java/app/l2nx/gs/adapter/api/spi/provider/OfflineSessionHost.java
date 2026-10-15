package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.spi.capability.NxAppSessions;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.OfflineSessionPlacement;
import app.l2nx.gs.adapter.api.spi.model.OfflineSessionSettings;
import java.util.ServiceLoader;

/**
 * The game side of app sessions, implemented by the host and discovered via {@link ServiceLoader}. The engine owns the
 * session table, the clock, the restore after a restart and the presence events; the host owns the world. Exactly one
 * implementation may be registered: none leaves the engine disabled, two put it into {@code FAILED}.
 *
 * <p><b>Threads.</b> The engine calls every method except {@link #settings()} from an adapter IO thread, never from
 * the game thread or the Kafka consumer thread, and waits for the result. An implementation hops to its game thread
 * itself and blocks until the work is done, or runs inline when already on it. Calls for one session never overlap;
 * calls for different sessions may.</p>
 *
 * <p>The host checks its rules before {@link NxAppSessions#start} and again in {@link #placeInWorld} (a spot may be
 * taken after a restart). A method that throws is logged; what the engine does then is stated per method.</p>
 */
public interface OfflineSessionHost {

    /**
     * Loads the character without a client and puts it on the session's spot, marked as app-loaded. On a restore
     * after a restart the spot may be taken, and the host may then use another free spot of the SAME pool. The
     * character must not get the client-session presence event of the normal enter-world path; the engine publishes
     * the session's own.
     *
     * <p>Refused or thrown: the character is not in the world. A start is rolled back (no row, no event); a restore
     * closes the session with {@code APP_RESTORE_FAILED}.</p>
     */
    OfflineSessionPlacement placeInWorld(OfflineSession session);

    /**
     * Starts the session's action for a character {@link #placeInWorld} placed, reading what it needs from
     * {@link OfflineSession#getTypeState()}. When the action ends on its own the host reports it through
     * {@link NxAppSessions#onActionEnded}.
     *
     * <p>Thrown: a start removes the character again ({@link #removeFromWorld} with return) and is rolled back; a restore
     * closes the session with {@code APP_RESTORE_FAILED}.</p>
     */
    void startAction(OfflineSession session);

    /**
     * Stops the session's action. Also called after the host reported {@link NxAppSessions#onActionEnded}, so it must
     * tolerate an action that already stopped. A throw is logged and the end continues.
     */
    void stopAction(OfflineSession session);

    /**
     * Ends the character's presence for a closing session, after {@link #stopAction}. {@code returnToOrigin} is true
     * when the session's {@code activeMs} is below {@link OfflineSessionSettings#getReturnThresholdMs()}; the character
     * then goes back to the session's origin, otherwise it stays where it is.
     *
     * <ul>
     *   <li>App-loaded character in the world: move it to the origin when asked, store it and take it out of the
     *   world.</li>
     *   <li>Character not in the world (restore never reached it, placement failed, or a login came first): only the
     *   stored location changes, to the origin when asked.</li>
     *   <li>Character played with a client is never touched. On an {@code APP_LOGIN} end this runs before the
     *   host loads the live character, so the stored location is already right when the client enters.</li>
     * </ul>
     *
     * <p>A throw is logged and the end continues: the row closes and the end event is published.</p>
     */
    void removeFromWorld(OfflineSession session, boolean returnToOrigin);

    /**
     * Current settings, read on every use. Unlike the other methods it may be called from the engine's scheduler thread
     * and the thread that starts the adapter's modules, so it must return at once and must not hop to the game thread.
     * A throw or {@code null} keeps the last good value; at engine boot, before there is one, it fails the boot.
     */
    OfflineSessionSettings settings();
}
