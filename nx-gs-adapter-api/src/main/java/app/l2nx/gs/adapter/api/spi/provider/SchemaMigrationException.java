package app.l2nx.gs.adapter.api.spi.provider;

/**
 * The adapter-owned schema could not be brought to the current version. Checked on purpose: the tables are a hard
 * dependency, so a host calling the migration must decide whether its boot stops. The message is for an operator.
 */
public class SchemaMigrationException extends Exception {

    private static final long serialVersionUID = 1L;

    public SchemaMigrationException(String message) {
        super(message);
    }

    public SchemaMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
