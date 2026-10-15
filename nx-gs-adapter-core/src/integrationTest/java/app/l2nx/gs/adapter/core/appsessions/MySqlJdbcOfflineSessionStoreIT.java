package app.l2nx.gs.adapter.core.appsessions;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySqlJdbcOfflineSessionStoreIT extends AbstractJdbcOfflineSessionStoreIT {

    @Container
    private static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
