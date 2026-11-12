package app.l2nx.gs.adapter.core.appsessions;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
class MariaDbJdbcOfflineSessionStoreIT extends AbstractJdbcOfflineSessionStoreIT {

    @Container
    private static final MariaDBContainer DB = new MariaDBContainer("mariadb:11.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
