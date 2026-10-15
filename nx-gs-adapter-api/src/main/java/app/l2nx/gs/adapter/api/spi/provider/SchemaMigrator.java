package app.l2nx.gs.adapter.api.spi.provider;

/**
 * Brings the adapter-owned {@code l2nx_*} tables in the host database to the current version; discovered by
 * {@code nx-gs-adapter-core} via {@link java.util.ServiceLoader}. The adapter ships the implementation
 * ({@code nx-gs-db-migrator-liquibase5}); a host only puts it on the classpath.
 *
 * <p>Exactly one impl is supported: none or several fail the migration and with it the adapter start.</p>
 */
public interface SchemaMigrator {

    /**
     * Applies every pending change; a no-op on an up-to-date database. Borrows a writable connection from
     * {@code source} and returns it; never closes the host pool.
     *
     * @throws SchemaMigrationException with a message naming the cause an operator acts on: the failing change and
     *     the missing privilege, the holder of a stale migration lock, or the underlying database error
     */
    void migrate(JdbcConnectionSource source) throws SchemaMigrationException;
}
