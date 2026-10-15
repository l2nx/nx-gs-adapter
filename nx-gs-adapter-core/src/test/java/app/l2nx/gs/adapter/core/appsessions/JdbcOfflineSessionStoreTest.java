package app.l2nx.gs.adapter.core.appsessions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JdbcOfflineSessionStoreTest {

    @Nested
    class IsUniqueViolation {

        @ParameterizedTest(name = "SQLState {0}, vendor code {1} -> {2}")
        @CsvSource({
            "23505, 0, true", // PostgreSQL unique_violation
            "23502, 0, false", // PostgreSQL not_null_violation
            "23503, 0, false", // PostgreSQL foreign_key_violation
            "23000, 1062, true", // MySQL / MariaDB ER_DUP_ENTRY
            "23000, 1048, false", // MySQL / MariaDB ER_BAD_NULL_ERROR
            "22001, 1406, false" // value too long
        })
        void isUniqueViolation_shouldMatchOnlyUniqueKeyConflicts(String sqlState, int vendorCode, boolean expected) {
            assertEquals(
                    expected, JdbcOfflineSessionStore.isUniqueViolation(new SQLException("x", sqlState, vendorCode)));
        }
    }
}
