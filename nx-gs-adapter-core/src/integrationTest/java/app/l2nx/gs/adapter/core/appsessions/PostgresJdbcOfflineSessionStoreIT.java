package app.l2nx.gs.adapter.core.appsessions;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresJdbcOfflineSessionStoreIT extends AbstractJdbcOfflineSessionStoreIT {

    @Container
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
