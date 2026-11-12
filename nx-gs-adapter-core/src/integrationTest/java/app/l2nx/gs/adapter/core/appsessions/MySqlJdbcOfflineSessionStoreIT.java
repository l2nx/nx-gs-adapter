package app.l2nx.gs.adapter.core.appsessions;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class MySqlJdbcOfflineSessionStoreIT extends AbstractJdbcOfflineSessionStoreIT {

    @Container
    private static final MySQLContainer DB = new MySQLContainer("mysql:8.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
