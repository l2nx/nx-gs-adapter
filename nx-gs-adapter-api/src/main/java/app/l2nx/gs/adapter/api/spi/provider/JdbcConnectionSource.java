package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.ops.model.PoolStats;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Source of JDBC {@link Connection}s to the host database, discovered via {@link java.util.ServiceLoader}. Intended to
 * wrap the host's existing pool, not open a second one.
 *
 * <p>Read-only is the consumer's choice, set after every borrow because the pool is host-owned: db-sync calls
 * {@link Connection#setReadOnly(boolean) setReadOnly(true)}, while the {@link SchemaMigrator} and the adapter's
 * {@code l2nx_*} stores call {@code setReadOnly(false)}. A provider must not force read-only.</p>
 */
public interface JdbcConnectionSource {

    /**
     * Informational name for logging (e.g. {@code "my-hikari"}).
     */
    String name();

    /**
     * Borrows a pooled connection; the caller closes it.
     */
    Connection getConnection() throws SQLException;

    /**
     * Pool stats for heartbeats; empty when the pool exposes none.
     */
    default Optional<PoolStats> stats() {
        return Optional.empty();
    }

    /**
     * Probe to skip a tick while the pool is known-down; default {@code true} leaves
     * {@link #getConnection()} outcome as the health signal.
     */
    default boolean isHealthy() {
        return true;
    }
}
