package app.l2nx.gs.adapter.api.spi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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

    @Test
    void notStartsWith_shouldRejectEmptyPrefix() {
        assertThrows(IllegalArgumentException.class, () -> RowFilter.notStartsWith("name", ""));
    }

    @Test
    void factories_shouldRejectNulls() {
        assertThrows(NullPointerException.class, () -> RowFilter.eq(null, 1));
        assertThrows(NullPointerException.class, () -> RowFilter.eq("c", (String) null));
        assertThrows(NullPointerException.class, () -> RowFilter.notStartsWith("c", null));
    }

    @Test
    void equals_shouldCompareColumnOperatorAndValue() {
        assertEquals(RowFilter.eq("kind", 1), RowFilter.eq("kind", 1));
        assertEquals(RowFilter.eq("kind", 1).hashCode(), RowFilter.eq("kind", 1).hashCode());
        assertNotEquals(RowFilter.eq("kind", 1), RowFilter.notEq("kind", 1));
        assertNotEquals(RowFilter.eq("kind", 1), RowFilter.eq("kind", 2));
    }
}
