package app.l2nx.gs.adapter.api.domain.skill.effect;

/**
 * Where an escape effect teleports the character (param {@code escapeDestination} of {@link
 * SkillEffectType#ESCAPE}).
 */
public enum EscapeDestination {
    TOWN,
    CASTLE,
    FORTRESS,
    CLAN_HALL,
    SIEGE_HEADQUARTERS
}
