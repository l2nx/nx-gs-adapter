package app.l2nx.gs.adapter.api.kafka.sync.gd.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SkillEnchantRouteTest {

    private static Stream<Arguments> invalidNodes() {
        return Stream.of(
                Arguments.of("missing", NullPointerException.class, null),
                Arguments.of(
                        "level mismatch",
                        IllegalArgumentException.class,
                        SkillLevel.builder().level(36).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNodes")
    void build_shouldReject_whenNodeInvalid(String reason, Class<? extends Exception> expected, SkillLevel node) {
        assertThrows(
                expected,
                () -> SkillEnchantRoute.builder()
                        .baseLevel(37)
                        .route(1)
                        .enchantLevel(1)
                        .node(node)
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
