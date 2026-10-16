package app.l2nx.gs.adapter.core.receipts;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySqlJdbcCommandReceiptsIT extends AbstractJdbcCommandReceiptsIT {

    @Container
    private static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.4");

    @Override
    protected boolean mysqlFamily() {
        return true;
    }

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
