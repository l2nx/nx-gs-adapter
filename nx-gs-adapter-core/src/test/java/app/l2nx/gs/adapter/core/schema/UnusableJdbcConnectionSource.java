package app.l2nx.gs.adapter.core.schema;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import java.sql.Connection;
import java.sql.SQLException;

/** Pairs with {@link NoOpSchemaMigrator} on the core test classpath; no core test opens a connection. */
public final class UnusableJdbcConnectionSource implements JdbcConnectionSource {

    @Override
    public String name() {
        return "unusable";
    }

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLException("core tests have no database");
    }
}
