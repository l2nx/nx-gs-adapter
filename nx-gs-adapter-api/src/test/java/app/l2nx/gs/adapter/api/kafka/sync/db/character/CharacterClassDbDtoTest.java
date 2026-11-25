package app.l2nx.gs.adapter.api.kafka.sync.db.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClassKind;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CharacterClassDbDtoTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        CharacterClassDbDto entry = CharacterClassDbDto.builder()
                .classId(CharacterClass.CARDINAL)
                .kind(CharacterClassKind.SUB)
                .level(82)
                .exp(123_456L)
                .sp(789L)
                .build();

        assertEquals(CharacterClass.CARDINAL, entry.getClassId());
        assertEquals(CharacterClassKind.SUB, entry.getKind());
        assertEquals(Integer.valueOf(82), entry.getLevel());
        assertEquals(Long.valueOf(123_456L), entry.getExp());
        assertEquals(Long.valueOf(789L), entry.getSp());
        assertEquals(entry, entry.toBuilder().build());
    }

    @Test
    void levelExpSp_shouldBeNullable_whenTenantOmitsTheColumns() {
        CharacterClassDbDto entry = CharacterClassDbDto.builder()
                .classId(CharacterClass.CARDINAL)
                .kind(CharacterClassKind.MAIN)
                .build();

        assertNull(entry.getLevel());
        assertNull(entry.getExp());
        assertNull(entry.getSp());
    }

    private static Stream<Arguments> withoutRequiredField() {
        return Stream.of(
                Arguments.of("classId", CharacterClassDbDto.builder().kind(CharacterClassKind.MAIN)),
                Arguments.of("kind", CharacterClassDbDto.builder().classId(CharacterClass.CARDINAL)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withoutRequiredField")
    void build_shouldThrowNpe_whenRequiredFieldIsNull(String missing, CharacterClassDbDto.Builder builder) {
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterClassDbDto.class).verify();
    }
}
