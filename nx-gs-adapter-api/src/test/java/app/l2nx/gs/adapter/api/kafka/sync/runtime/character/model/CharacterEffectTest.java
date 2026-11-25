package app.l2nx.gs.adapter.api.kafka.sync.runtime.character.model;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.skill.SkillEnchant;
import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectCategory;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CharacterEffectTest {

    private static CharacterEffect.Builder haste() {
        return CharacterEffect.builder()
                .skillId(1086)
                .skillLevel(2)
                .category(SkillEffectCategory.BUFF)
                .remainingSec(1180)
                .offline(CharacterEffectOffline.FROZEN);
    }

    @Test
    void toBuilder_shouldRoundtripEveryField() {
        CharacterEffect original = haste().enchant(new SkillEnchant(2, 3)).build();

        CharacterEffect actual = original.toBuilder().build();

        assertEquals(original, actual);
        assertEquals(original.hashCode(), actual.hashCode());
        assertEquals(1086, actual.getSkillId());
        assertEquals(2, actual.getSkillLevel());
        assertEquals(SkillEffectCategory.BUFF, actual.getCategory());
        assertEquals(Integer.valueOf(1180), actual.getRemainingSec());
        assertEquals(CharacterEffectOffline.FROZEN, actual.getOffline());
        assertEquals(new SkillEnchant(2, 3), actual.getEnchant());
        assertEquals(haste().build(), haste().build().toBuilder().build());
    }

    @Test
    void build_shouldAcceptNullEnchant_forNotEnchantedSkills() {
        assertNull(haste().build().getEnchant());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterEffect.class).verify();
    }

    @Test
    void build_shouldAcceptNullRemainingSec_forEffectsWithoutDuration() {
        assertNull(haste().category(SkillEffectCategory.TOGGLE)
                .remainingSec(null)
                .build()
                .getRemainingSec());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingFields")
    void build_shouldReject_whenRequiredFieldIsMissing(String field, CharacterEffect.Builder builder) {
        assertThrows(NullPointerException.class, builder::build);
    }

    static Stream<Arguments> missingFields() {
        return Stream.of(
                Arguments.of("category", haste().category(null)), Arguments.of("offline", haste().offline(null)));
    }
}
