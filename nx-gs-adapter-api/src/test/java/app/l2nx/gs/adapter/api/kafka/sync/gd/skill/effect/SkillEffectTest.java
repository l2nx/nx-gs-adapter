package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectType;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SkillEffectTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEffects")
    void build_shouldReject_whenEffectIsInvalid(
            String name, Class<? extends Exception> expected, SkillEffect.Builder builder) {
        assertThrows(expected, builder::build);
    }

    static Stream<Arguments> invalidEffects() {
        return Stream.of(
                Arguments.of(
                        "missing typeRaw",
                        NullPointerException.class,
                        SkillEffect.builder().type(SkillEffectType.STUN)),
                Arguments.of(
                        "params without a type",
                        IllegalArgumentException.class,
                        SkillEffect.builder()
                                .typeRaw("CustomEffect")
                                .params(Collections.singletonMap("durationSec", "9"))));
    }

    @Test
    void build_shouldAcceptParams_whenTypeSet() {
        SkillEffect actual = SkillEffect.builder()
                .type(SkillEffectType.STUN)
                .typeRaw("engineEffectToken")
                .params(Collections.singletonMap("durationSec", "9"))
                .build();

        assertEquals("9", actual.getParams().get("durationSec"));
    }

    @Test
    void build_shouldAcceptUnmappedEffect_whenTypeNull() {
        SkillEffect actual = SkillEffect.builder().typeRaw("CustomEffect").build();

        assertEquals("CustomEffect", actual.getTypeRaw());
    }
}
