package app.l2nx.gs.adapter.api.domain.skill;

/**
 * What a produced-item line of a skill actually gives, derived from the engine id carried in {@code
 * SkillProducedItem.itemTemplateId}. Ids below zero are pseudo-currencies, not item templates.
 */
public enum SkillRewardKind {
    /** A real item template (id above zero). */
    ITEM,
    /** PC cafe points (pseudo id -100). */
    PC_CAFE_POINTS,
    /** Clan reputation (pseudo id -200). */
    CLAN_REPUTATION,
    /** Fame (pseudo id -300). */
    FAME
}
