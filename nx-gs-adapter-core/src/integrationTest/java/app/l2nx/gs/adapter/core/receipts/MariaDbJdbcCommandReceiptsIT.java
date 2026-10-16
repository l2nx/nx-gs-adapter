package app.l2nx.gs.adapter.core.receipts;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MariaDbJdbcCommandReceiptsIT extends AbstractJdbcCommandReceiptsIT {

    @Container
    private static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4");

    @Override
    protected boolean mysqlFamily() {
        return true;
    }

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
