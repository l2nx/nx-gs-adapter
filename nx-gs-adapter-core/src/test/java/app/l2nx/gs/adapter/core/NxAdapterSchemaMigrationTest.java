package app.l2nx.gs.adapter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.spi.model.ClientLoginOutcome;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrator;
import app.l2nx.gs.adapter.core.schema.SchemaMigration;
import app.l2nx.gs.adapter.core.schema.UnusableJdbcConnectionSource;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NxAdapterSchemaMigrationTest {

    private static final String[] L2NX_PROPS = {
        "l2nx.gs-key", "l2nx.platform-url", "l2nx.enabled", "l2nx.config-file", "l2nx.host-type"
    };

    private final Map<String, String> savedProps = new HashMap<>();

    @BeforeEach
    void clearSysprops() {
        for (String key : L2NX_PROPS) {
            String val = System.getProperty(key);
            if (val != null) {
                savedProps.put(key, val);
                System.clearProperty(key);
            }
        }
        NxAdapter.resetForTesting();
    }

    @AfterEach
    void restoreSysprops() {
        NxAdapter.resetForTesting();
        for (String key : L2NX_PROPS) {
            System.clearProperty(key);
        }
        for (Map.Entry<String, String> e : savedProps.entrySet()) {
            System.setProperty(e.getKey(), e.getValue());
        }
        savedProps.clear();
    }

    @Nested
    class Start {

        @Test
        void start_shouldFailWithoutConnecting_whenMigrationFails() throws Exception {
            configure("gs", "true");
            NxAdapter.setSchemaMigrationForTesting(failingMigration());
            List<AdapterState> captured = new CopyOnWriteArrayList<>();
            CountDownLatch failed = new CountDownLatch(1);
            NxAdapter.onStateChange(state -> {
                captured.add(state);
                if (state == AdapterState.FAILED) {
                    failed.countDown();
                }
            });

            NxAdapter.start();

            assertTrue(failed.await(10, TimeUnit.SECONDS), "adapter must reach FAILED");
            assertEquals(Collections.singletonList(AdapterState.FAILED), captured);
        }

        @Test
        void start_shouldFailAndStopLoginsWaiting_whenMigratorThrowsAnError() throws Exception {
            configure("gs", "true");
            CountingMigrator migrator = new CountingMigrator();
            migrator.error = new NoClassDefFoundError("liquibase/Scope");
            NxAdapter.setSchemaMigrationForTesting(migrationOf(migrator));
            CountDownLatch failed = new CountDownLatch(1);
            NxAdapter.onStateChange(state -> {
                if (state == AdapterState.FAILED) {
                    failed.countDown();
                }
            });

            NxAdapter.start();

            assertTrue(failed.await(10, TimeUnit.SECONDS), "adapter must reach FAILED");
            ClientLoginOutcome actual = assertTimeoutPreemptively(
                    Duration.ofSeconds(2), () -> NxAdapter.appSessions().onClientLogin(1));
            assertEquals(ClientLoginOutcome.Status.NONE, actual.getStatus());
        }

        @Test
        void start_shouldNotMigrate_whenAdapterDisabled() {
            configure("gs", "false");
            CountingMigrator migrator = new CountingMigrator();
            NxAdapter.setSchemaMigrationForTesting(migrationOf(migrator));

            NxAdapter.start();

            assertEquals(AdapterState.DISABLED, NxAdapter.state());
            assertEquals(0, migrator.calls.get());
        }
    }

    @Nested
    class MigrateSchema {

        @Test
        void migrateSchema_shouldMigrate_whenAdapterDisabled() throws Exception {
            configure("gs", "false");
            CountingMigrator migrator = new CountingMigrator();
            NxAdapter.setSchemaMigrationForTesting(migrationOf(migrator));

            NxAdapter.migrateSchema();

            assertEquals(1, migrator.calls.get());
        }

        @Test
        void migrateSchema_shouldMigrateOnce_whenHostCalledItBeforeStart() throws Exception {
            CountingMigrator migrator = new CountingMigrator();
            NxAdapter.setSchemaMigrationForTesting(migrationOf(migrator));

            NxAdapter.migrateSchema();
            NxAdapter.migrateSchema();

            assertEquals(1, migrator.calls.get());
        }
    }

    private static void configure(String hostType, String enabled) {
        System.setProperty("l2nx.gs-key", "nx_sk_" + repeat('c', 32));
        System.setProperty("l2nx.platform-url", "https://127.0.0.1:1");
        System.setProperty("l2nx.enabled", enabled);
        System.setProperty("l2nx.host-type", hostType);
    }

    private static SchemaMigration failingMigration() {
        return new SchemaMigration(
                Collections::<SchemaMigrator>emptyList, Collections::<JdbcConnectionSource>emptyList);
    }

    private static SchemaMigration migrationOf(SchemaMigrator migrator) {
        return new SchemaMigration(
                () -> Collections.singletonList(migrator),
                () -> Collections.<JdbcConnectionSource>singletonList(new UnusableJdbcConnectionSource()));
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static final class CountingMigrator implements SchemaMigrator {
        final AtomicInteger calls = new AtomicInteger();
        volatile Error error;

        @Override
        public void migrate(JdbcConnectionSource source) throws SchemaMigrationException {
            calls.incrementAndGet();
            if (error != null) {
                throw error;
            }
        }
    }
}
