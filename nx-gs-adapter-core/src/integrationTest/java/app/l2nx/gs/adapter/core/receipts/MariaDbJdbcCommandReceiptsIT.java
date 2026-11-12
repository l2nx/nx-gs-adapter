package app.l2nx.gs.adapter.core.receipts;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
class MariaDbJdbcCommandReceiptsIT extends AbstractJdbcCommandReceiptsIT {

    @Container
    private static final MariaDBContainer DB = new MariaDBContainer("mariadb:11.4");

    @Override
    protected boolean mysqlFamily() {
        return true;
    }

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
