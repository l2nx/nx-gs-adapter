package app.l2nx.gs.adapter.api.spi.model;

import java.util.Objects;

/**
 * Declarative row predicate over one column of a {@link ChildSource}, see {@link ChildSource#rowFilter()}; the value is
 * always a bound parameter, never interpolated into SQL.
 *
 * <p>SQL three-valued logic applies: a row whose column is {@code NULL} satisfies none of the predicates, including the
 * negative ones ({@link #notEq}, {@link #notStartsWith}), and is therefore invisible to the engine.</p>
 */
public final class RowFilter {

    public enum Operator {
        EQ,
        NOT_EQ,
        NOT_STARTS_WITH
    }

    private final String column;
    private final Operator operator;
    private final Object value;

    private RowFilter(String column, Operator operator, Object value) {
        this.column = Objects.requireNonNull(column, "column");
        this.operator = operator;
        this.value = value;
    }

    public static RowFilter eq(String column, String value) {
        return new RowFilter(column, Operator.EQ, Objects.requireNonNull(value, "value"));
    }

    public static RowFilter eq(String column, long value) {
        return new RowFilter(column, Operator.EQ, value);
    }

    public static RowFilter notEq(String column, String value) {
        return new RowFilter(column, Operator.NOT_EQ, Objects.requireNonNull(value, "value"));
    }

    public static RowFilter notEq(String column, long value) {
        return new RowFilter(column, Operator.NOT_EQ, value);
    }

    /**
     * Rows whose column does not start with {@code prefix}, matched literally ({@code %}, {@code _} and the escape character
     * carry no wildcard meaning); a non-empty prefix is required.
     */
    public static RowFilter notStartsWith(String column, String prefix) {
        if (Objects.requireNonNull(prefix, "prefix").isEmpty()) {
            throw new IllegalArgumentException("prefix must not be empty");
        }
        return new RowFilter(column, Operator.NOT_STARTS_WITH, prefix);
    }

    public String getColumn() {
        return column;
    }

    public Operator getOperator() {
        return operator;
    }

    /** A {@link String} or a {@link Long}, matching the factory used. */
    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RowFilter that = (RowFilter) o;
        return column.equals(that.column) && operator == that.operator && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, operator, value);
    }

    @Override
    public String toString() {
        return "RowFilter[" + column + " " + operator + " " + value + "]";
    }
}
