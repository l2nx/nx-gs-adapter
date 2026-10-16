package app.l2nx.gs.adapter.core.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;

/** Portable parameter and column binding for the adapter-owned tables; instants are stored as UTC wall time. */
public final class JdbcValues {

    private JdbcValues() {}

    public static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        ps.setTimestamp(index, Timestamp.from(value), utc());
    }

    public static @Nullable Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column, utc());
        return ts == null ? null : ts.toInstant();
    }

    public static void setNullableString(PreparedStatement ps, int index, @Nullable String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    public static void setNullableInt(PreparedStatement ps, int index, @Nullable Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public static void setNullableLong(PreparedStatement ps, int index, @Nullable Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    public static @Nullable Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static @Nullable Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** A fresh instance per call: {@link Calendar} is mutable and drivers may write to it. */
    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
    }
}
