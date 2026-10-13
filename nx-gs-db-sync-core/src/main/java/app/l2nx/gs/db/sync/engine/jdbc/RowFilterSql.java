package app.l2nx.gs.db.sync.engine.jdbc;

import app.l2nx.gs.adapter.api.spi.model.RowFilter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class RowFilterSql {

    static final char LIKE_ESCAPE = '!';

    private static final Rendered NONE = new Rendered("", Collections.<Object>emptyList());

    private RowFilterSql() {}

    public static Rendered render(@Nullable RowFilter filter) {
        if (filter == null) {
            return NONE;
        }
        String column = filter.getColumn();
        Object value = filter.getValue();
        switch (filter.getOperator()) {
            case EQ:
                return new Rendered(" AND " + column + " = ?", Collections.singletonList(value));
            case NOT_EQ:
                return new Rendered(" AND " + column + " <> ?", Collections.singletonList(value));
            case NOT_STARTS_WITH:
                return new Rendered(
                        " AND " + column + " NOT LIKE ? ESCAPE '" + LIKE_ESCAPE + "'",
                        Collections.<Object>singletonList(escapeLike((String) value) + "%"));
            default:
                throw new IllegalStateException("Unsupported operator " + filter.getOperator());
        }
    }

    static String escapeLike(String literal) {
        StringBuilder sb = new StringBuilder(literal.length() + 4);
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** SQL fragment (empty or starting with {@code " AND "}) plus the parameters it binds, in order. */
    public static final class Rendered {

        private final String sql;
        private final List<Object> params;

        Rendered(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        public static Rendered none() {
            return NONE;
        }

        public String sql() {
            return sql;
        }

        public List<Object> params() {
            return params;
        }

        public void bind(PreparedStatement ps, int firstIndex) throws SQLException {
            int index = firstIndex;
            for (Object param : params) {
                if (param instanceof Long) {
                    ps.setLong(index++, (Long) param);
                } else {
                    ps.setString(index++, (String) param);
                }
            }
        }
    }
}
