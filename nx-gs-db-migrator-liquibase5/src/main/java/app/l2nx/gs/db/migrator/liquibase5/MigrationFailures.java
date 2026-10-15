package app.l2nx.gs.db.migrator.liquibase5;

import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrationException;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import liquibase.change.Change;
import liquibase.changelog.ChangeSet;
import liquibase.exception.LockException;
import org.jspecify.annotations.Nullable;

final class MigrationFailures {

    private static final String POSTGRES_INSUFFICIENT_PRIVILEGE = "42501";
    private static final Set<Integer> MYSQL_ACCESS_DENIED = Set.of(1044, 1142, 1227);

    private MigrationFailures() {}

    static SchemaMigrationException describe(
            Throwable failure, @Nullable ChangeSet failedChangeSet, long lockWaitMinutes) {
        LockException lock = find(failure, LockException.class);
        if (lock != null) {
            return new SchemaMigrationException(
                    """
                    Adapter schema migration lock %s was not released within %d minute(s): %s. \
                    If no other process is migrating this database, a previous run died holding it; release it with \
                    UPDATE %s SET locked = FALSE, lockgranted = NULL, lockedby = NULL WHERE id = 1. \
                    The adapter never releases a lock it did not take.\
                    """.formatted(
                                    Liquibase5SchemaMigrator.CHANGELOG_LOCK_TABLE,
                                    lockWaitMinutes,
                                    lock.getMessage(),
                                    Liquibase5SchemaMigrator.CHANGELOG_LOCK_TABLE),
                    failure);
        }
        SQLException denied = accessDenied(failure);
        if (denied != null) {
            if (failedChangeSet == null) {
                return new SchemaMigrationException(
                        """
                        Database user may not create the adapter changelog tables %s and %s: grant it CREATE on \
                        the schema of the host database. Database said: %s\
                        """.formatted(
                                        Liquibase5SchemaMigrator.CHANGELOG_TABLE,
                                        Liquibase5SchemaMigrator.CHANGELOG_LOCK_TABLE,
                                        denied.getMessage()),
                        failure);
            }
            return new SchemaMigrationException(
                    """
                    Database user lacks a privilege for adapter changeset '%s' (%s): grant it %s on the schema \
                    of the host database. Database said: %s\
                    """.formatted(
                                    failedChangeSet.getId(),
                                    changeNames(failedChangeSet),
                                    requiredPrivileges(failedChangeSet),
                                    denied.getMessage()),
                    failure);
        }
        String cause = rootMessage(failure);
        if (failedChangeSet != null) {
            return new SchemaMigrationException(
                    "Adapter changeset '" + failedChangeSet.getId() + "' failed: " + cause, failure);
        }
        return new SchemaMigrationException("Adapter schema migration failed: " + cause, failure);
    }

    private static String changeNames(ChangeSet changeSet) {
        return changeSet.getChanges().stream()
                .map(Change::getSerializedObjectName)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static String requiredPrivileges(ChangeSet changeSet) {
        Set<String> privileges = new LinkedHashSet<>();
        for (Change change : changeSet.getChanges()) {
            privileges.add(
                    switch (change.getSerializedObjectName()) {
                        case "createTable" -> "CREATE";
                        case "createIndex" -> "INDEX (MySQL / MariaDB) or table ownership (PostgreSQL)";
                        default -> "ALTER";
                    });
        }
        return String.join(" and ", privileges);
    }

    private static @Nullable SQLException accessDenied(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                for (SQLException e = sql; e != null; e = e.getNextException()) {
                    if (POSTGRES_INSUFFICIENT_PRIVILEGE.equals(e.getSQLState())
                            || MYSQL_ACCESS_DENIED.contains(e.getErrorCode())) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    private static <T extends Throwable> @Nullable T find(Throwable failure, Class<T> type) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getName();
    }
}
