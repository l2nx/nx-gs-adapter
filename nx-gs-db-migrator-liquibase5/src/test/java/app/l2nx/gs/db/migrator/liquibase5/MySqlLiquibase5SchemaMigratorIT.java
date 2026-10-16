package app.l2nx.gs.db.migrator.liquibase5;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySqlLiquibase5SchemaMigratorIT extends AbstractLiquibase5SchemaMigratorIT {

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
