package app.l2nx.gs.adapter.core.appsessions;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MariaDbJdbcOfflineSessionStoreIT extends AbstractJdbcOfflineSessionStoreIT {

    @Container
    private static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
