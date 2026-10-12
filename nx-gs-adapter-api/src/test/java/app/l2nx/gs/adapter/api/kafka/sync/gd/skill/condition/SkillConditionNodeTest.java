package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.skill.condition.SkillConditionLogic;
import app.l2nx.gs.adapter.api.domain.skill.condition.SkillConditionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SkillConditionNodeTest {

    private static SkillConditionNode leaf() {
        return SkillConditionNode.builder()
                .type(SkillConditionType.CASTER_LEVEL)
                .typeRaw("PlayerLevel")
                .params(Collections.singletonMap("min", "40"))
                .build();
    }

    static Stream<UnaryOperator<SkillConditionNode.Builder>> predicateFields() {
        return Stream.of(
                b -> b.typeRaw("and"),
                b -> b.type(SkillConditionType.CASTER_LEVEL),
                b -> b.params(Collections.singletonMap("min", "40")),
                b -> b.paramsRaw(Collections.singletonMap("level", "40")));
    }

    @Nested
    class Container {

        @Test
        void build_shouldAcceptChildren_whenLogicSet() {
            SkillConditionNode actual = SkillConditionNode.builder()
                    .logic(SkillConditionLogic.OR)
                    .negated(true)
                    .children(Collections.singletonList(leaf()))
                    .build();

            assertEquals(1, actual.getChildren().size());
            assertTrue(actual.isNegated());
        }

        @Test
        void build_shouldReject_whenChildrenMissing() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkillConditionNode.builder()
                            .logic(SkillConditionLogic.AND)
                            .build());
        }

        @Test
        void build_shouldReject_whenChildrenEmpty() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkillConditionNode.builder()
                            .logic(SkillConditionLogic.AND)
                            .children(new ArrayList<SkillConditionNode>())
                            .build());
        }

        @Test
        void build_shouldReject_whenLogicMissing() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkillConditionNode.builder()
                            .children(Collections.singletonList(leaf()))
                            .build());
        }

        @ParameterizedTest
        @MethodSource("app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition.SkillConditionNodeTest#predicateFields")
        void build_shouldReject_whenPredicateFieldSet(UnaryOperator<SkillConditionNode.Builder> predicateField) {
            SkillConditionNode.Builder builder = SkillConditionNode.builder()
                    .logic(SkillConditionLogic.AND)
                    .children(Collections.singletonList(leaf()));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> predicateField.apply(builder).build());
        }
    }

    @Nested
    class Leaf {

        @Test
        void build_shouldAcceptPredicate_whenTypeRawSet() {
            SkillConditionNode actual = leaf();

            assertEquals("PlayerLevel", actual.getTypeRaw());
            assertEquals("40", actual.getParams().get("min"));
        }

        @Test
        void build_shouldAcceptUnmappedPredicate_whenTypeNull() {
            SkillConditionNode actual =
                    SkillConditionNode.builder().typeRaw("CustomGate").build();

            assertEquals("CustomGate", actual.getTypeRaw());
        }

        @Test
        void build_shouldReject_whenTypeRawMissing() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkillConditionNode.builder()
                            .type(SkillConditionType.CASTER_LEVEL)
                            .build());
        }

        @Test
        void build_shouldReject_whenParamsSetWithoutType() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkillConditionNode.builder()
                            .typeRaw("CustomGate")
                            .params(Collections.singletonMap("min", "40"))
                            .build());
        }

        @Test
        void build_shouldReject_whenChildrenSet() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkillConditionNode.builder()
                            .typeRaw("PlayerLevel")
                            .children(Collections.singletonList(leaf()))
                            .build());
        }
    }

    @Test
    void toBuilder_shouldCopyEveryField_whenContainer() {
        SkillConditionNode expected = SkillConditionNode.builder()
                .logic(SkillConditionLogic.AND)
                .negated(true)
                .systemMessageId(113)
                .children(Collections.singletonList(leaf()))
                .build();

        SkillConditionNode actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void toBuilder_shouldCopyEveryField_whenLeaf() {
        SkillConditionNode expected = leaf().toBuilder()
                .paramsRaw(Collections.singletonMap("level", "40"))
                .systemMessageId(113)
                .negated(true)
                .build();

        SkillConditionNode actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
