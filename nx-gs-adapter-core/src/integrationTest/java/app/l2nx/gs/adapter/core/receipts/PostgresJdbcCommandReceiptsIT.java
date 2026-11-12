package app.l2nx.gs.adapter.core.receipts;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresJdbcCommandReceiptsIT extends AbstractJdbcCommandReceiptsIT {

    @Container
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
