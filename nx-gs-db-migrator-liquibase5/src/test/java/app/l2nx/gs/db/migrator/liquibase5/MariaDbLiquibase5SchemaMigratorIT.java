package app.l2nx.gs.db.migrator.liquibase5;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MariaDbLiquibase5SchemaMigratorIT extends AbstractLiquibase5SchemaMigratorIT {

    @Container
    private static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DB;
    }
}
