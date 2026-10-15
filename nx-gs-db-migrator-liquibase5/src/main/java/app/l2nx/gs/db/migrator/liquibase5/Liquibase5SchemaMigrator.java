package app.l2nx.gs.db.migrator.liquibase5;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrator;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import liquibase.GlobalConfiguration;
import liquibase.Scope;
import liquibase.UpdateSummaryEnum;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.analytics.configuration.AnalyticsArgs;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.visitor.AbstractChangeExecListener;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.ChangeExecListenerCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.ShowSummaryArgument;
import liquibase.configuration.ConfigurationDefinition;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.license.LicenseTrackingArgs;
import liquibase.parser.ChangeLogParserConfiguration;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.sql.SqlConfiguration;
import liquibase.ui.LoggerUIService;
import liquibase.ui.UIServiceEnum;
import org.jspecify.annotations.Nullable;

/**
 * Isolated from any Liquibase use of the host: own changelog tables ({@value #CHANGELOG_TABLE},
 * {@value #CHANGELOG_LOCK_TABLE}) and own {@link Scope} with every setting pinned, so system properties, environment
 * variables and the host's scope cannot change the run.
 */
public final class Liquibase5SchemaMigrator implements SchemaMigrator {

    static final String CHANGELOG_FILE = "app/l2nx/gs/db/migrations/changelog.xml";
    static final String CHANGELOG_TABLE = "l2nx_databasechangelog";
    static final String CHANGELOG_LOCK_TABLE = "l2nx_databasechangeloglock";

    // a concurrent run finishes in seconds, so a lock held for a minute is stale
    private static final long LOCK_WAIT_MINUTES = 1L;
    private static final long LOCK_POLL_SECONDS = 5L;

    private final long lockWaitMinutes;
    private final long lockPollSeconds;

    public Liquibase5SchemaMigrator() {
        this(LOCK_WAIT_MINUTES, LOCK_POLL_SECONDS);
    }

    Liquibase5SchemaMigrator(long lockWaitMinutes, long lockPollSeconds) {
        this.lockWaitMinutes = lockWaitMinutes;
        this.lockPollSeconds = lockPollSeconds;
    }

    @Override
    public void migrate(JdbcConnectionSource source) throws SchemaMigrationException {
        Connection connection;
        try {
            connection = source.getConnection();
        } catch (SQLException e) {
            throw new SchemaMigrationException(
                    "Adapter schema migration could not borrow a connection from JdbcConnectionSource '" + source.name()
                            + "': " + e.getMessage(),
                    e);
        }
        try (Connection c = connection) {
            boolean lentReadOnly = c.isReadOnly();
            boolean lentAutoCommit = c.getAutoCommit();
            Throwable failure = null;
            try {
                c.setReadOnly(false);
                update(c);
            } catch (Throwable t) {
                failure = t;
                throw t;
            } finally {
                restore(c, lentReadOnly, lentAutoCommit, failure);
            }
        } catch (SQLException e) {
            throw new SchemaMigrationException(
                    "Adapter schema migration failed on the connection from JdbcConnectionSource '" + source.name()
                            + "': " + e.getMessage(),
                    e);
        }
    }

    /** A failure here is suppressed into {@code failure} so it never masks why the run failed. */
    private static void restore(Connection c, boolean readOnly, boolean autoCommit, @Nullable Throwable failure)
            throws SQLException {
        try {
            if (!c.getAutoCommit()) {
                c.rollback();
            }
            c.setAutoCommit(autoCommit);
            c.setReadOnly(readOnly);
        } catch (SQLException | RuntimeException e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e);
        }
    }

    private void update(Connection connection) throws SchemaMigrationException {
        FailedChangeSetListener listener = null;
        try {
            listener = new FailedChangeSetListener();
            FailedChangeSetListener changeExecListener = listener;
            Scope.child(isolatedScope(), () -> {
                Database database =
                        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDatabaseChangeLogTableName(CHANGELOG_TABLE);
                database.setDatabaseChangeLogLockTableName(CHANGELOG_LOCK_TABLE);
                // null would fall back to configuration, which a host may set for its own run
                database.setLiquibaseCatalogName(database.getDefaultCatalogName());
                database.setLiquibaseSchemaName(database.getDefaultSchemaName());
                new CommandScope(UpdateCommandStep.COMMAND_NAME)
                        .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_FILE)
                        .addArgumentValue(ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_ARG, changeExecListener)
                        .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY, UpdateSummaryEnum.OFF)
                        .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
                        .setOutput(OutputStream.nullOutputStream())
                        .execute();
            });
        } catch (NoClassDefFoundError e) {
            throw new SchemaMigrationException(
                    "Adapter schema migration needs org.liquibase:liquibase-core 5.x on the host classpath, which the"
                            + " adapter does not bundle; missing class: " + e.getMessage(),
                    e);
        } catch (Exception e) {
            throw MigrationFailures.describe(e, listener == null ? null : listener.failed, lockWaitMinutes);
        }
    }

    private Map<String, Object> isolatedScope() {
        ClassLoader classLoader = Liquibase5SchemaMigrator.class.getClassLoader();
        Map<String, Object> values = new HashMap<>();
        values.put(Scope.Attr.classLoader.name(), classLoader);
        values.put(Scope.Attr.resourceAccessor.name(), new ClassLoaderResourceAccessor(classLoader));
        values.put(Scope.Attr.ui.name(), new LoggerUIService());
        pin(values, GlobalConfiguration.DATABASECHANGELOG_TABLE_NAME, CHANGELOG_TABLE);
        pin(values, GlobalConfiguration.DATABASECHANGELOGLOCK_TABLE_NAME, CHANGELOG_LOCK_TABLE);
        pin(values, GlobalConfiguration.CHANGELOGLOCK_WAIT_TIME, lockWaitMinutes);
        pin(values, GlobalConfiguration.CHANGELOGLOCK_POLL_RATE, lockPollSeconds);
        pin(values, GlobalConfiguration.FILE_ENCODING, StandardCharsets.UTF_8);
        pin(values, GlobalConfiguration.CONVERT_DATA_TYPES, Boolean.TRUE);
        pin(values, GlobalConfiguration.PRESERVE_SCHEMA_CASE, Boolean.FALSE);
        pin(values, GlobalConfiguration.INCLUDE_CATALOG_IN_SPECIFICATION, Boolean.FALSE);
        pin(values, GlobalConfiguration.SECURE_PARSING, Boolean.TRUE);
        pin(values, GlobalConfiguration.VALIDATE_XML_CHANGELOG_FILES, Boolean.TRUE);
        pin(values, GlobalConfiguration.DUPLICATE_FILE_MODE, GlobalConfiguration.DuplicateFileMode.ERROR);
        pin(values, GlobalConfiguration.ALLOW_DUPLICATED_CHANGESETS_IDENTIFIERS, Boolean.FALSE);
        pin(values, GlobalConfiguration.ALLOW_CUSTOM_CHANGE, Boolean.FALSE);
        pin(values, GlobalConfiguration.ALLOW_EXECUTE_COMMAND, Boolean.FALSE);
        pin(values, GlobalConfiguration.ALLOW_SQL_PRECONDITION, Boolean.FALSE);
        pin(values, GlobalConfiguration.ALLOW_EXTERNAL_CHANGELOG_PATHS, Boolean.FALSE);
        pin(values, GlobalConfiguration.ALLOW_PARENT_DIRECTORY_REFERENCES, Boolean.FALSE);
        pin(values, GlobalConfiguration.ALLOW_INCLUDE_ALL_CLASSES, Boolean.FALSE);
        pin(values, GlobalConfiguration.STRICT, Boolean.FALSE);
        pin(values, GlobalConfiguration.HEADLESS, Boolean.TRUE);
        pin(values, GlobalConfiguration.SHOW_BANNER, Boolean.FALSE);
        pin(values, GlobalConfiguration.UI_SERVICE, UIServiceEnum.LOGGER);
        pin(values, ChangeLogParserConfiguration.SUPPORT_PROPERTY_ESCAPING, Boolean.FALSE);
        pin(
                values,
                ChangeLogParserConfiguration.MISSING_PROPERTY_MODE,
                ChangeLogParserConfiguration.MissingPropertyMode.ERROR);
        pin(
                values,
                ChangeLogParserConfiguration.CHANGELOG_PARSE_MODE,
                ChangeLogParserConfiguration.ChangelogParseMode.STRICT);
        pin(values, SqlConfiguration.SHOW_AT_LOG_LEVEL, Level.FINE);
        pin(values, AnalyticsArgs.ENABLED, Boolean.FALSE);
        pin(values, LicenseTrackingArgs.ENABLED, Boolean.FALSE);
        return values;
    }

    private static <T> void pin(Map<String, Object> values, ConfigurationDefinition<T> setting, T value) {
        values.put(setting.getKey(), value);
    }

    private static final class FailedChangeSetListener extends AbstractChangeExecListener {

        volatile @Nullable ChangeSet failed;

        @Override
        public void runFailed(
                ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database, Exception exception) {
            failed = changeSet;
        }
    }
}
