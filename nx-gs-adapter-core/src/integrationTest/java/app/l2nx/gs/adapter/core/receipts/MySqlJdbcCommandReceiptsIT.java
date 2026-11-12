package app.l2nx.gs.adapter.core.receipts;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class MySqlJdbcCommandReceiptsIT extends AbstractJdbcCommandReceiptsIT {

    @Container
    private static final MySQLContainer DB = new MySQLContainer("mysql:8.4");

    @Override
    protected boolean mysqlFamily() {
        return true;
    }

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
