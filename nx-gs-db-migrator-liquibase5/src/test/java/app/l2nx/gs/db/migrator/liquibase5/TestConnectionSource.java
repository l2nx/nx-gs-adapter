package app.l2nx.gs.db.migrator.liquibase5;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Stands in for a host pool: hands out driver connections and records the state each one is returned in. */
final class TestConnectionSource implements JdbcConnectionSource {

    private final String url;
    private final String user;
    private final String password;

    boolean lendReadOnly;
    volatile Connection lastConnection;
    volatile boolean lastAutoCommitOnClose;

    TestConnectionSource(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public String name() {
        return "test";
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection raw = DriverManager.getConnection(url, user, password);
        if (lendReadOnly) {
            raw.setReadOnly(true);
        }
        Connection tracked = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && !raw.isClosed()) {
                        lastAutoCommitOnClose = raw.getAutoCommit();
                    }
                    try {
                        return method.invoke(raw, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        lastConnection = tracked;
        return tracked;
    }
}
