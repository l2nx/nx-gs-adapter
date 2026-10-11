package app.l2nx.gs.adapter.api.kafka.sync.gd.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkillEnchantRouteTest {

    @Test
    void build_shouldRejectMissingNode() {
        assertThrows(
                NullPointerException.class,
                () -> SkillEnchantRoute.builder()
                        .baseLevel(37)
                        .route(1)
                        .enchantLevel(1)
                        .build());
    }

    @Test
    void build_shouldRejectNodeLevelMismatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkillEnchantRoute.builder()
                        .baseLevel(37)
                        .route(1)
                        .enchantLevel(1)
                        .node(SkillLevel.builder().level(36).build())
                        .build());
    }

    @Test
    void toBuilder_shouldCopyEveryField() {
        SkillEnchantRoute expected = SkillEnchantRoute.builder()
                .baseLevel(37)
                .route(2)
                .enchantLevel(5)
                .enchantSp(1_200_000L)
                .node(SkillLevel.builder().level(37).power(512.0).build())
                .build();

        SkillEnchantRoute actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
