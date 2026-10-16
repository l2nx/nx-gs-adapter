package app.l2nx.gs.adapter.core.schema;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrator;
import app.l2nx.gs.commons.spi.ServiceProviders;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Runs at most once; later calls rethrow the first failure so a host's early call and the adapter's start agree. */
public final class SchemaMigration {

    static final String MIGRATOR_ARTIFACT = "app.l2nx:nx-gs-db-migrator-liquibase5";

    private static final NxLog log = NxLogFactory.getLogger(SchemaMigration.class);

    private final Supplier<List<SchemaMigrator>> migrators;
    private final Supplier<List<JdbcConnectionSource>> sources;
    private final Object lock = new Object();
    private boolean done;
    private @Nullable SchemaMigrationException failure;
    private volatile @Nullable JdbcConnectionSource migratedSource;

    public SchemaMigration() {
        this(
                () -> ServiceProviders.loadAll(SchemaMigrator.class, SchemaMigration.class),
                () -> ServiceProviders.loadAll(JdbcConnectionSource.class, SchemaMigration.class));
    }

    public SchemaMigration(Supplier<List<SchemaMigrator>> migrators, Supplier<List<JdbcConnectionSource>> sources) {
        this.migrators = migrators;
        this.sources = sources;
    }

    public void run() throws SchemaMigrationException {
        synchronized (lock) {
            if (failure != null) {
                throw new SchemaMigrationException(failure.getMessage(), failure);
            }
            if (done) {
                return;
            }
            try {
                migrate();
                done = true;
            } catch (SchemaMigrationException e) {
                failure = e;
                throw e;
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable e) {
                // an Error (missing class, JVM too old) must be the run-once outcome too, or a later call would retry
                failure = new SchemaMigrationException(
                        "Adapter schema migration failed unexpectedly: "
                                + e.getClass().getName() + ": " + e.getMessage(),
                        e);
                throw failure;
            }
        }
    }

    private void migrate() throws SchemaMigrationException {
        SchemaMigrator migrator = single(
                discoverMigrators(),
                SchemaMigrator.class,
                "add " + MIGRATOR_ARTIFACT + " (and the org.liquibase:liquibase-core 5.x it needs, which the adapter"
                        + " does not bundle) to the host classpath");
        JdbcConnectionSource source = single(
                sources.get(),
                JdbcConnectionSource.class,
                "register the host pool via META-INF/services/" + JdbcConnectionSource.class.getName());
        long startedAt = System.nanoTime();
        log.info(
                "Migrating the adapter schema with {} over JdbcConnectionSource '{}'",
                migrator.getClass().getName(),
                source.name());
        migrator.migrate(source);
        migratedSource = source;
        log.info("Adapter schema is current ({} ms)", (System.nanoTime() - startedAt) / 1_000_000L);
    }

    /**
     * The source the schema was migrated over; the adapter's own stores work only on a current schema.
     *
     * @throws IllegalStateException the migration has not run yet in this JVM, or it failed
     */
    public JdbcConnectionSource migratedSource() {
        JdbcConnectionSource source = migratedSource;
        if (source != null) {
            return source;
        }
        synchronized (lock) {
            if (failure != null) {
                throw new IllegalStateException(
                        "The adapter-owned l2nx_* tables are unusable, their schema migration failed: "
                                + failure.getMessage(),
                        failure);
            }
            source = migratedSource;
            if (source == null) {
                throw new IllegalStateException("The adapter-owned l2nx_* tables are not migrated yet: call"
                        + " NxAdapter.migrateSchema() before using them");
            }
            return source;
        }
    }

    private List<SchemaMigrator> discoverMigrators() throws SchemaMigrationException {
        try {
            return migrators.get();
        } catch (ServiceConfigurationError | LinkageError e) {
            throw explainDiscoveryFailure(e);
        }
    }

    static SchemaMigrationException explainDiscoveryFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof UnsupportedClassVersionError) {
                return new SchemaMigrationException(
                        MIGRATOR_ARTIFACT + " needs Java 17 or newer (Liquibase 5 does), but this JVM is Java "
                                + System.getProperty("java.version") + ": run the host on Java 17+",
                        failure);
            }
            if (cause instanceof NoClassDefFoundError || cause instanceof ClassNotFoundException) {
                return new SchemaMigrationException(
                        MIGRATOR_ARTIFACT + " could not be loaded because a class is missing (" + cause.getMessage()
                                + "): add org.liquibase:liquibase-core 5.x, which the adapter does not bundle, to the"
                                + " host classpath",
                        failure);
            }
            cause = cause.getCause();
        }
        return new SchemaMigrationException(
                "Loading the SchemaMigrator failed: " + failure.getClass().getName() + ": " + failure.getMessage(),
                failure);
    }

    private static <T> T single(List<T> found, Class<T> spi, String whenMissing) throws SchemaMigrationException {
        if (found.isEmpty()) {
            throw new SchemaMigrationException("No " + spi.getSimpleName()
                    + " on the classpath, so the adapter-owned l2nx_* tables cannot be created or updated: "
                    + whenMissing);
        }
        if (found.size() > 1) {
            throw new SchemaMigrationException("Several " + spi.getSimpleName() + " implementations on the classpath: ["
                    + ServiceProviders.classNames(found) + "]; keep exactly one");
        }
        return found.get(0);
    }
}
