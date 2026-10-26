package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.spi.capability.NxAppSessions;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSessionPlacement;
import java.util.ServiceLoader;

/**
 * The game side of app sessions, discovered via {@link ServiceLoader}. Exactly one implementation may be registered:
 * none leaves the engine disabled, two put it into {@code FAILED}.
 *
 * <p>The engine calls every method from an adapter IO thread and waits for the result. An implementation hops to its
 * game thread itself and blocks until the work is done, or runs inline when already on it. Calls for one session never
 * overlap; calls for different sessions may. See {@link NxAppSessions#updateTypeState} for calling back in.</p>
 *
 * <p>The host checks its rules before {@link NxAppSessions#start} and again in {@link #placeInWorld} (a spot may be
 * taken after a restart). {@link NxAppSessions#check} never calls this interface, so its {@code ALLOWED} does not
 * rule out a {@link #placeInWorld} refusal of the start that follows. A method that throws is logged; what the engine does then is stated per method.</p>
 */
public interface OfflineSessionHost {

    /**
     * Loads the character without a client and puts it on the session's spot, marked as app-loaded. On a restore the
     * spot may be taken; the host may then use another free spot of the same pool. The character must not get the
     * client-session presence event of the normal enter-world path; the engine publishes the session's own.
     *
     * <p>Refused or thrown: the character is not in the world. A start is rolled back (no row, no event); a restore
     * closes the session with {@code APP_RESTORE_FAILED}, or tries again later on
     * {@link OfflineSessionPlacement#refusedForNow}. A type state the placement changed goes back in the answer, not
     * through {@link NxAppSessions#updateTypeState}.</p>
     */
    OfflineSessionPlacement placeInWorld(OfflineSession session);

    /**
     * Starts the session's action for a character {@link #placeInWorld} placed, reading what it needs from
     * {@link OfflineSession#getTypeState()}, already including what the placement returned. When the action ends on its
     * own the host reports it through {@link NxAppSessions#onActionEnded}.
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
     * when the session's {@code activeMs} is below its {@link OfflineSession#getReturnThresholdMs()}; the character then
     * goes back to the session's origin, otherwise it stays where it is.
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
}
