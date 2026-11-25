package app.l2nx.gs.adapter.api.kafka.sync.db.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.l2nx.gs.adapter.api.domain.character.CharacterRace;
import app.l2nx.gs.adapter.api.domain.character.CharacterSex;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class CharacterDisplayDbDtoTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        CharacterDisplayDbDto display = CharacterDisplayDbDto.builder()
                .race(CharacterRace.KAMAEL)
                .sex(CharacterSex.FEMALE)
                .face(2)
                .hairStyle(6)
                .hairColor(3)
                .nameColor(0xFFFF00)
                .titleColor(0x00FFFF)
                .build();

        assertEquals(CharacterRace.KAMAEL, display.getRace());
        assertEquals(CharacterSex.FEMALE, display.getSex());
        assertEquals(Integer.valueOf(2), display.getFace());
        assertEquals(Integer.valueOf(6), display.getHairStyle());
        assertEquals(Integer.valueOf(3), display.getHairColor());
        assertEquals(Integer.valueOf(0xFFFF00), display.getNameColor());
        assertEquals(Integer.valueOf(0x00FFFF), display.getTitleColor());
        assertEquals(display, display.toBuilder().build());
    }

    @Test
    void allFields_shouldBeNullable_whenProviderCannotReadThem() {
        CharacterDisplayDbDto display = CharacterDisplayDbDto.builder().build();

        assertNull(display.getRace());
        assertNull(display.getSex());
        assertNull(display.getFace());
        assertNull(display.getHairStyle());
        assertNull(display.getHairColor());
        assertNull(display.getNameColor());
        assertNull(display.getTitleColor());
    }

    @Test
    void toBuilder_shouldRoundtrip_whenPartlyPopulated() {
        CharacterDisplayDbDto original =
                CharacterDisplayDbDto.builder().face(1).nameColor(0xFF0000).build();

        CharacterDisplayDbDto copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterDisplayDbDto.class).verify();
    }
}
