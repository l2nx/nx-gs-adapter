package app.l2nx.gs.adapter.api.spi.provider;

/**
 * Optional SPI telling {@code gd-sync} whether the host's game-data catalogs are loaded. At most one
 * implementation; none registered means always ready.
 *
 * <p>The adapter connects before the datapack is parsed, and an empty (non-{@code null}) snapshot would publish a
 * {@code SNAPSHOT_COMPLETE count=0}, on which the platform deletes every row of that entity for the server. So the
 * module skips the pass while {@link #ready()} is {@code false}.</p>
 *
 * <p>Polled from the module's scheduler thread: must be cheap, non-blocking and thread-safe,
 * typically a volatile flag.</p>
 */
public interface GameDataReadinessProvider {

    boolean ready();
}
