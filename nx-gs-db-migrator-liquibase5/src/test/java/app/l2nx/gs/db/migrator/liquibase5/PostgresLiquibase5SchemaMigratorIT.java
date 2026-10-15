package app.l2nx.gs.db.migrator.liquibase5;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresLiquibase5SchemaMigratorIT extends AbstractLiquibase5SchemaMigratorIT {

    @Container
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }

    @Nested
    class Privileges {

        @Test
        void migrate_shouldNameChangesetAndPrivilege_whenUserMayNotCreateTables() throws Exception {
            new Liquibase5SchemaMigrator().migrate(source());
            execute("DROP TABLE l2nx_offline_sessions");
            execute("DELETE FROM l2nx_databasechangelog WHERE id = '0004-offline-sessions-table'");
            execute("DROP ROLE IF EXISTS migrator_without_ddl");
            execute("CREATE ROLE migrator_without_ddl LOGIN PASSWORD 'secret'");
            execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO migrator_without_ddl");
            TestConnectionSource withoutDdl =
                    new TestConnectionSource(DB.getJdbcUrl(), "migrator_without_ddl", "secret");

            SchemaMigrationException actual = assertThrows(
                    SchemaMigrationException.class, () -> new Liquibase5SchemaMigrator().migrate(withoutDdl));

            assertTrue(actual.getMessage().contains("'0004-offline-sessions-table'"), actual.getMessage());
            assertTrue(actual.getMessage().contains("grant it CREATE"), actual.getMessage());
        }
    }
}
