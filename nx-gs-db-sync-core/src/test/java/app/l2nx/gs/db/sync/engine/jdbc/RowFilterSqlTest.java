package app.l2nx.gs.db.sync.engine.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.gs.adapter.api.spi.model.RowFilter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RowFilterSqlTest {

    @Test
    void render_shouldReturnEmptyFragment_whenNoFilter() {
        RowFilterSql.Rendered rendered = RowFilterSql.render(null);

        assertEquals("", rendered.sql());
        assertTrue(rendered.params().isEmpty());
    }

    @Test
    void render_shouldEmitEqWithBoundValue() {
        RowFilterSql.Rendered rendered = RowFilterSql.render(RowFilter.eq("kind", 3));

        assertEquals(" AND kind = ?", rendered.sql());
        assertEquals(Arrays.<Object>asList(3L), rendered.params());
    }

    @Test
    void render_shouldEmitNotEqWithBoundValue() {
        RowFilterSql.Rendered rendered = RowFilterSql.render(RowFilter.notEq("name", "x"));

        assertEquals(" AND name <> ?", rendered.sql());
        assertEquals(Arrays.<Object>asList("x"), rendered.params());
    }

    @ParameterizedTest(name = "prefix [{0}] binds as [{1}]")
    @CsvSource({"tmp_,tmp!_%", "50%,50!%%", "a!b,a!!b%", "plain,plain%", "_%!,!_!%!!%"})
    void render_shouldEscapeLikeWildcardsInNotStartsWithPrefix(String prefix, String expected) {
        RowFilterSql.Rendered rendered = RowFilterSql.render(RowFilter.notStartsWith("name", prefix));

        assertEquals(" AND name NOT LIKE ? ESCAPE '!'", rendered.sql());
        assertEquals(Arrays.<Object>asList(expected), rendered.params());
    }

    @Test
    void render_shouldNeverInterpolateValueIntoSql() {
        RowFilterSql.Rendered rendered = RowFilterSql.render(RowFilter.eq("name", "'; DROP TABLE x --"));

        assertEquals(" AND name = ?", rendered.sql());
    }

    @Test
    void bind_shouldUseSetLongForNumbersAndSetStringForText_fromGivenIndex() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);

        RowFilterSql.render(RowFilter.eq("kind", 9)).bind(ps, 4);
        RowFilterSql.render(RowFilter.eq("name", "n")).bind(ps, 6);

        verify(ps).setLong(4, 9L);
        verify(ps).setString(6, "n");
    }
}
