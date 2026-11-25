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
import org.junit.jupiter.params.provider.Arguments;
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

    static Stream<Arguments> invalidContainers() {
        return Stream.of(
                Arguments.of("children missing", SkillConditionNode.builder().logic(SkillConditionLogic.AND)),
                Arguments.of(
                        "children empty",
                        SkillConditionNode.builder()
                                .logic(SkillConditionLogic.AND)
                                .children(new ArrayList<SkillConditionNode>())),
                Arguments.of(
                        "logic missing", SkillConditionNode.builder().children(Collections.singletonList(leaf()))));
    }

    static Stream<Arguments> invalidLeaves() {
        return Stream.of(
                Arguments.of("typeRaw missing", SkillConditionNode.builder().type(SkillConditionType.CASTER_LEVEL)),
                Arguments.of(
                        "params without type",
                        SkillConditionNode.builder()
                                .typeRaw("CustomGate")
                                .params(Collections.singletonMap("min", "40"))),
                Arguments.of(
                        "children set",
                        SkillConditionNode.builder()
                                .typeRaw("PlayerLevel")
                                .children(Collections.singletonList(leaf()))));
    }

    @Nested
    class Container {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition.SkillConditionNodeTest#invalidContainers")
        void build_shouldReject_whenContainerIncomplete(String reason, SkillConditionNode.Builder builder) {
            assertThrows(IllegalArgumentException.class, builder::build);
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
        void build_shouldAcceptUnmappedPredicate_whenTypeNull() {
            SkillConditionNode actual =
                    SkillConditionNode.builder().typeRaw("CustomGate").build();

            assertEquals("CustomGate", actual.getTypeRaw());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition.SkillConditionNodeTest#invalidLeaves")
        void build_shouldReject_whenLeafInvalid(String reason, SkillConditionNode.Builder builder) {
            assertThrows(IllegalArgumentException.class, builder::build);
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
        assertEquals(SkillConditionLogic.AND, actual.getLogic());
        assertTrue(actual.isNegated());
        assertEquals(Integer.valueOf(113), actual.getSystemMessageId());
        assertEquals(Collections.singletonList(leaf()), actual.getChildren());
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
        assertEquals(SkillConditionType.CASTER_LEVEL, actual.getType());
        assertEquals("PlayerLevel", actual.getTypeRaw());
        assertEquals(Collections.singletonMap("min", "40"), actual.getParams());
        assertEquals(Collections.singletonMap("level", "40"), actual.getParamsRaw());
        assertEquals(Integer.valueOf(113), actual.getSystemMessageId());
        assertTrue(actual.isNegated());
    }
}
