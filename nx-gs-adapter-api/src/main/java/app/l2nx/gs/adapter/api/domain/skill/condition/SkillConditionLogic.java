package app.l2nx.gs.adapter.api.domain.skill.condition;

/**
 * Logic of a container node in a skill condition tree ({@code SkillConditionNode.logic}). There is no NOT
 * container: negation is the {@code negated} flag of any node.
 */
public enum SkillConditionLogic {
    /** All children must hold; also the implicit list of conditions of a level. */
    AND,
    /** At least one child must hold. */
    OR
}
