package app.l2nx.gs.adapter.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SchemaMigrationTest {

    private static final JdbcConnectionSource SOURCE = new UnusableJdbcConnectionSource();

    private static SchemaMigration migrationOf(SchemaMigrator migrator) {
        return new SchemaMigration(() -> Collections.singletonList(migrator), () -> Collections.singletonList(SOURCE));
    }

    @Nested
    class Discovery {

        @Test
        void run_shouldNameWrapperArtifact_whenNoMigratorOnClasspath() {
            SchemaMigration migration = new SchemaMigration(
                    Collections::<SchemaMigrator>emptyList, () -> Collections.singletonList(SOURCE));

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(actual.getMessage().contains("nx-gs-db-migrator-liquibase5"), actual.getMessage());
        }

        @Test
        void run_shouldNameBothMigrators_whenTwoOnClasspath() {
            SchemaMigration migration = new SchemaMigration(
                    () -> Arrays.<SchemaMigrator>asList(new CountingMigrator(), new NoOpSchemaMigrator()),
                    () -> Collections.singletonList(SOURCE));

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(actual.getMessage().contains(CountingMigrator.class.getName()), actual.getMessage());
            assertTrue(actual.getMessage().contains(NoOpSchemaMigrator.class.getName()), actual.getMessage());
        }

        @Test
        void run_shouldNameServiceDescriptor_whenNoConnectionSourceOnClasspath() {
            SchemaMigration migration = new SchemaMigration(
                    () -> Collections.<SchemaMigrator>singletonList(new CountingMigrator()),
                    Collections::<JdbcConnectionSource>emptyList);

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(
                    actual.getMessage().contains("META-INF/services/" + JdbcConnectionSource.class.getName()),
                    actual.getMessage());
        }

        @Test
        void run_shouldFail_whenTwoConnectionSourcesOnClasspath() {
            SchemaMigration migration = new SchemaMigration(
                    () -> Collections.<SchemaMigrator>singletonList(new CountingMigrator()),
                    () -> Arrays.<JdbcConnectionSource>asList(SOURCE, new UnusableJdbcConnectionSource()));

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(actual.getMessage().contains("Several JdbcConnectionSource"), actual.getMessage());
        }

        @Test
        void run_shouldMigrateWithDiscoveredSource_whenExactlyOneOfEach() throws Exception {
            CountingMigrator migrator = new CountingMigrator();

            migrationOf(migrator).run();

            assertEquals(1, migrator.calls.get());
            assertSame(SOURCE, migrator.lastSource);
        }

        @Test
        void run_shouldNameJava17_whenMigratorClassIsTooNewForTheJvm() {
            SchemaMigration migration = new SchemaMigration(
                    () -> {
                        throw new ServiceConfigurationError(
                                "Provider could not be instantiated",
                                new UnsupportedClassVersionError("class file version 61.0"));
                    },
                    () -> Collections.singletonList(SOURCE));

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(actual.getMessage().contains("Java 17"), actual.getMessage());
        }

        @Test
        void run_shouldNameLiquibase_whenMigratorClassCannotLinkLiquibase() {
            SchemaMigration migration = new SchemaMigration(
                    () -> {
                        throw new ServiceConfigurationError(
                                "Provider could not be instantiated",
                                new NoClassDefFoundError("liquibase/changelog/visitor/ChangeExecListener"));
                    },
                    () -> Collections.singletonList(SOURCE));

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(actual.getMessage().contains("liquibase-core 5.x"), actual.getMessage());
        }

        @Test
        void run_shouldSucceed_whenServiceLoaderFindsOneRegistrationOfEach() throws Exception {
            new SchemaMigration().run();
        }
    }

    @Nested
    class Idempotency {

        @Test
        void run_shouldMigrateOnce_whenCalledRepeatedly() throws Exception {
            CountingMigrator migrator = new CountingMigrator();
            SchemaMigration migration = migrationOf(migrator);

            migration.run();
            migration.run();
            migration.run();

            assertEquals(1, migrator.calls.get());
        }

        @Test
        void run_shouldRethrowFirstFailureWithoutRetrying_whenFirstRunFailed() {
            CountingMigrator migrator = new CountingMigrator();
            migrator.failure = new SchemaMigrationException("lock held by dead-host");
            SchemaMigration migration = migrationOf(migrator);

            assertThrows(SchemaMigrationException.class, migration::run);
            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migration::run);

            assertEquals("lock held by dead-host", actual.getMessage());
            assertEquals(1, migrator.calls.get());
        }

        @Test
        void run_shouldWrapRuntimeException_whenMigratorThrowsUnchecked() {
            CountingMigrator migrator = new CountingMigrator();
            migrator.unchecked = new IllegalStateException("boom");

            SchemaMigrationException actual = assertThrows(SchemaMigrationException.class, migrationOf(migrator)::run);

            assertTrue(actual.getMessage().contains("boom"), actual.getMessage());
        }

        @Test
        void run_shouldRecordAnErrorAsTheFailure_whenMigratorThrowsAnError() {
            CountingMigrator migrator = new CountingMigrator();
            migrator.error = new NoClassDefFoundError("liquibase/Scope");
            SchemaMigration migration = migrationOf(migrator);

            SchemaMigrationException first = assertThrows(SchemaMigrationException.class, migration::run);
            SchemaMigrationException again = assertThrows(SchemaMigrationException.class, migration::run);

            assertTrue(first.getMessage().contains("NoClassDefFoundError"), first.getMessage());
            assertEquals(first.getMessage(), again.getMessage());
            assertEquals(1, migrator.calls.get());
        }

        @Test
        void run_shouldRethrowVirtualMachineError_whenMigratorRunsOutOfMemory() {
            CountingMigrator migrator = new CountingMigrator();
            migrator.error = new OutOfMemoryError("simulated");

            assertThrows(OutOfMemoryError.class, migrationOf(migrator)::run);
        }

        @Test
        void run_shouldMigrateOnce_whenCalledConcurrently() throws Exception {
            CountDownLatch release = new CountDownLatch(1);
            CountingMigrator migrator = new CountingMigrator();
            migrator.gate = release;
            SchemaMigration migration = migrationOf(migrator);
            int callers = 8;
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            try {
                List<Future<?>> futures = new ArrayList<Future<?>>();
                for (int i = 0; i < callers; i++) {
                    futures.add(pool.submit(() -> {
                        migration.run();
                        return null;
                    }));
                }
                release.countDown();
                for (Future<?> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertEquals(1, migrator.calls.get());
        }
    }

    @Nested
    class MigratedSource {

        @Test
        void migratedSource_shouldTellToMigrateFirst_whenNotRunYet() {
            SchemaMigration migration = migrationOf(new CountingMigrator());

            IllegalStateException actual = assertThrows(IllegalStateException.class, migration::migratedSource);

            assertTrue(actual.getMessage().contains("NxAdapter.migrateSchema()"), actual.getMessage());
        }

        @Test
        void migratedSource_shouldReturnTheMigratedSource_whenRunSucceeded() throws SchemaMigrationException {
            SchemaMigration migration = migrationOf(new CountingMigrator());
            migration.run();

            assertSame(SOURCE, migration.migratedSource());
        }

        @Test
        void migratedSource_shouldCarryTheFailure_whenRunFailed() {
            CountingMigrator migrator = new CountingMigrator();
            migrator.failure = new SchemaMigrationException("no DDL rights");
            SchemaMigration migration = migrationOf(migrator);
            assertThrows(SchemaMigrationException.class, migration::run);

            IllegalStateException actual = assertThrows(IllegalStateException.class, migration::migratedSource);

            assertTrue(actual.getMessage().contains("no DDL rights"), actual.getMessage());
            assertSame(migrator.failure, actual.getCause());
        }
    }

    private static final class CountingMigrator implements SchemaMigrator {
        final AtomicInteger calls = new AtomicInteger();
        volatile JdbcConnectionSource lastSource;
        volatile SchemaMigrationException failure;
        volatile RuntimeException unchecked;
        volatile Error error;
        volatile CountDownLatch gate;

        @Override
        public void migrate(JdbcConnectionSource source) throws SchemaMigrationException {
            calls.incrementAndGet();
            lastSource = source;
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            if (unchecked != null) {
                throw unchecked;
            }
            if (error != null) {
                throw error;
            }
        }
    }
}
