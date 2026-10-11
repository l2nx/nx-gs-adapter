package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectType;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SkillEffectTest {

    @Test
    void build_shouldRejectMissingTypeRaw() {
        assertThrows(
                NullPointerException.class,
                () -> SkillEffect.builder().type(SkillEffectType.STUN).build());
    }

    @Test
    void build_shouldRejectParams_whenTypeNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkillEffect.builder()
                        .typeRaw("CustomEffect")
                        .params(Collections.singletonMap("durationSec", "9"))
                        .build());
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
