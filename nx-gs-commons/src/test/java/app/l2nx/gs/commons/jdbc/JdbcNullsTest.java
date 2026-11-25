package app.l2nx.gs.commons.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcNullsTest {

    @Test
    void nullableInt_shouldReturnNull_whenWasNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("col")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);

        assertNull(JdbcNulls.nullableInt(rs, "col"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {42, 0})
    void nullableInt_shouldReturnTheValue_whenNotNull(int value) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("col")).thenReturn(value);
        when(rs.wasNull()).thenReturn(false);

        assertEquals(Integer.valueOf(value), JdbcNulls.nullableInt(rs, "col"));
    }

    @Test
    void nullableLong_shouldReturnNull_whenWasNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("col")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);

        assertNull(JdbcNulls.nullableLong(rs, "col"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {123L, 0L})
    void nullableLong_shouldReturnTheValue_whenNotNull(long value) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("col")).thenReturn(value);
        when(rs.wasNull()).thenReturn(false);

        assertEquals(Long.valueOf(value), JdbcNulls.nullableLong(rs, "col"));
    }
}
