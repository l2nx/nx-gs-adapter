package app.l2nx.gs.adapter.api.spi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RowFilterTest {

    @Test
    void factories_shouldCaptureColumnOperatorAndValue() {
        RowFilter filter = RowFilter.notStartsWith("name", "tmp_");

        assertEquals("name", filter.getColumn());
        assertEquals(RowFilter.Operator.NOT_STARTS_WITH, filter.getOperator());
        assertEquals("tmp_", filter.getValue());
        assertEquals(Long.valueOf(4), RowFilter.eq("kind", 4).getValue());
        assertEquals(RowFilter.Operator.NOT_EQ, RowFilter.notEq("kind", "x").getOperator());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFilters")
    void factories_shouldReject_whenArgumentIsNullOrEmptyPrefix(
            String name, Class<? extends Exception> expected, Executable factory) {
        assertThrows(expected, factory);
    }

    static Stream<Arguments> invalidFilters() {
        return Stream.of(
                invalid("empty prefix", IllegalArgumentException.class, () -> RowFilter.notStartsWith("name", "")),
                invalid("null column", NullPointerException.class, () -> RowFilter.eq(null, 1)),
                invalid("null value", NullPointerException.class, () -> RowFilter.eq("c", (String) null)),
                invalid("null prefix", NullPointerException.class, () -> RowFilter.notStartsWith("c", null)));
    }

    private static Arguments invalid(String name, Class<? extends Exception> expected, Executable factory) {
        return Arguments.of(name, expected, factory);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(RowFilter.class).suppress(Warning.NULL_FIELDS).verify();
    }
}
