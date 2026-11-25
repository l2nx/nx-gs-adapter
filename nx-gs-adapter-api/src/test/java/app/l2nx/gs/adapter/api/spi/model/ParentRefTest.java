package app.l2nx.gs.adapter.api.spi.model;

import static org.junit.jupiter.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParentRefTest {

    @Test
    void of_shouldRoundtripFields() {
        ParentRef ref = ParentRef.of("character", "owner_id");

        assertEquals("character", ref.parentEntityName());
        assertEquals("owner_id", ref.fkColumn());
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource({",owner_id", "character,"})
    void of_shouldRejectNull(String parentEntityName, String fkColumn) {
        assertThrows(NullPointerException.class, () -> ParentRef.of(parentEntityName, fkColumn));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ParentRef.class).suppress(Warning.NULL_FIELDS).verify();
    }
}
