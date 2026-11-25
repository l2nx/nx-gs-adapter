package app.l2nx.gs.db.sync.engine.phase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import app.l2nx.gs.adapter.api.spi.model.ChildSource;
import app.l2nx.gs.adapter.api.spi.model.PrimarySource;
import app.l2nx.gs.adapter.api.spi.model.RowFilter;
import app.l2nx.gs.db.sync.engine.jdbc.JdbcDialect;
import it.unimi.dsi.fastutil.longs.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Phase2FetcherTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "3 | SELECT * FROM clan_data WHERE clan_id IN (?, ?, ?)",
                "1 | SELECT * FROM clan_data WHERE clan_id IN (?)"
            })
    void buildSql_shouldEmitInClauseWithRequestedPlaceholderCount(int placeholderCount, String expected) {
        assertEquals(expected, Phase2Fetcher.buildSql("clan_data", "clan_id", placeholderCount, null));
    }

    @Test
    void buildSql_shouldAppendBoundPredicate_whenRowFilterSet() {
        String sql = Phase2Fetcher.buildSql("clan_skills", "clan_id", 2, RowFilter.notStartsWith("name", "tmp_"));

        assertEquals("SELECT * FROM clan_skills WHERE clan_id IN (?, ?) AND name NOT LIKE ? ESCAPE '!'", sql);
    }

    @Test
    void buildSql_shouldThrow_whenPlaceholderCountZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> Phase2Fetcher.buildSql("t", "id", 0, null));
        assertThrows(IllegalArgumentException.class, () -> Phase2Fetcher.buildSql("t", "id", -1, null));
    }

    @Test
    void fetchPrimary_shouldPrepareStatementOnce_andPadLastChunk() throws SQLException {
        PrimarySource<TestRow> primary = clanPrimary();
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet firstChunk = mock(ResultSet.class);
        ResultSet lastChunk = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(firstChunk, lastChunk);
        when(firstChunk.next()).thenReturn(true, true, false);
        when(firstChunk.getLong("clan_id")).thenReturn(1L, 1L, 2L, 2L);
        when(firstChunk.getString("clan_name")).thenReturn("A", "B");
        when(firstChunk.getInt("clan_level")).thenReturn(5, 7);
        when(lastChunk.next()).thenReturn(true, false);
        when(lastChunk.getLong("clan_id")).thenReturn(1002L);

        LongList pks = new LongArrayList();
        for (long pk = 1L; pk <= 1002L; pk++) {
            pks.add(pk);
        }

        Long2ObjectMap<Object> result =
                new Phase2Fetcher().fetchPrimary(conn, primary, pks, 5, 10_000, JdbcDialect.OTHER);

        verify(conn, times(1)).prepareStatement(anyString());
        verify(ps, times(2)).executeQuery();
        verify(ps, times(2 * 1000)).setLong(anyInt(), anyLong());
        verify(ps).setLong(1, 1L);
        verify(ps).setLong(1000, 1000L);
        verify(ps).setLong(1, 1001L);
        verify(ps).setLong(2, 1002L);
        verify(ps, times(999)).setLong(anyInt(), eq(1002L));
        verify(ps).setQueryTimeout(5);
        verify(ps).setFetchSize(10_000);

        assertEquals(new LongOpenHashSet(new long[] {1L, 2L, 1002L}), result.keySet());
    }

    @Test
    void fetchChild_shouldGroupRowsByFk() throws SQLException {
        ChildSource<TestSkill> child = skillChild();
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, true, false);
        when(rs.getLong("clan_id")).thenReturn(1L, 1L, 2L);
        when(rs.getInt("skill_id")).thenReturn(101, 102, 201);
        when(rs.getInt("skill_level")).thenReturn(1, 2, 1);

        LongList fks = new LongArrayList(new long[] {1L, 2L, 3L});

        Long2ObjectMap<List<Object>> result =
                new Phase2Fetcher().fetchChild(conn, child, fks, 5, 10_000, JdbcDialect.OTHER);

        assertEquals(2, result.size(), "FK 3 absent from result map (no children)");
        assertEquals(2, result.get(1L).size(), "FK 1 has 2 skills");
        assertEquals(1, result.get(2L).size(), "FK 2 has 1 skill");
        assertNull(result.get(3L), "FK 3 absent — caller substitutes empty list");
    }

    @Test
    void fetchChild_shouldBindRowFilterValueAfterChunkPlaceholders() throws SQLException {
        ChildSource<TestSkill> inner = skillChild();
        ChildSource<TestSkill> child = new ChildSource<TestSkill>() {
            @Override
            public String tableName() {
                return inner.tableName();
            }

            @Override
            public String fkColumn() {
                return inner.fkColumn();
            }

            @Override
            public List<String> hashedColumns() {
                return inner.hashedColumns();
            }

            @Override
            public RowFilter rowFilter() {
                return RowFilter.notStartsWith("name", "a_b%");
            }

            @Override
            public TestSkill mapRow(ResultSet rs) throws SQLException {
                return inner.mapRow(rs);
            }
        };
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sql.capture())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        new Phase2Fetcher().fetchChild(conn, child, new LongArrayList(new long[] {1L}), 5, 10_000, JdbcDialect.OTHER);

        assertTrue(sql.getValue().endsWith("AND name NOT LIKE ? ESCAPE '!'"));
        verify(ps).setString(Phase2Fetcher.CHUNK_SIZE + 1, "a!_b!%%");
    }

    @Test
    void fetchPrimary_shouldReturnEmptyMap_whenPksEmpty() throws SQLException {
        Connection conn = mock(Connection.class);

        Long2ObjectMap<Object> result = new Phase2Fetcher()
                .fetchPrimary(conn, clanPrimary(), new LongArrayList(), 5, 10_000, JdbcDialect.OTHER);

        assertTrue(result.isEmpty());
        verify(conn, times(0)).prepareStatement(anyString());
    }

    @Test
    void fetchChild_shouldReturnEmptyMap_whenFksEmpty() throws SQLException {
        Connection conn = mock(Connection.class);

        Long2ObjectMap<List<Object>> result =
                new Phase2Fetcher().fetchChild(conn, skillChild(), new LongArrayList(), 5, 10_000, JdbcDialect.OTHER);

        assertTrue(result.isEmpty());
        verify(conn, times(0)).prepareStatement(anyString());
    }

    private static PrimarySource<TestRow> clanPrimary() {
        return new PrimarySource<TestRow>() {
            @Override
            public String tableName() {
                return "clan_data";
            }

            @Override
            public String pkColumn() {
                return "clan_id";
            }

            @Override
            public List<String> hashedColumns() {
                return Arrays.asList("clan_name", "clan_level");
            }

            @Override
            public TestRow mapRow(ResultSet rs) throws SQLException {
                return new TestRow(rs.getLong("clan_id"), rs.getString("clan_name"), rs.getInt("clan_level"));
            }
        };
    }

    private static ChildSource<TestSkill> skillChild() {
        return new ChildSource<TestSkill>() {
            @Override
            public String tableName() {
                return "clan_skills";
            }

            @Override
            public String fkColumn() {
                return "clan_id";
            }

            @Override
            public List<String> hashedColumns() {
                return Arrays.asList("skill_id", "skill_level");
            }

            @Override
            public TestSkill mapRow(ResultSet rs) throws SQLException {
                return new TestSkill(rs.getInt("skill_id"), rs.getInt("skill_level"));
            }
        };
    }

    private static final class TestRow {
        final long clanId;
        final String clanName;
        final int clanLevel;

        TestRow(long clanId, String clanName, int clanLevel) {
            this.clanId = clanId;
            this.clanName = clanName;
            this.clanLevel = clanLevel;
        }
    }

    private static final class TestSkill {
        final int skillId;
        final int skillLevel;

        TestSkill(int skillId, int skillLevel) {
            this.skillId = skillId;
            this.skillLevel = skillLevel;
        }
    }
}
