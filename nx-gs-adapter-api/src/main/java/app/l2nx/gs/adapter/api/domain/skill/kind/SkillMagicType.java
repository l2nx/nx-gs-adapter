package app.l2nx.gs.adapter.api.domain.skill.kind;

/**
 * Magic type of a skill ({@code Skill.magicType}), which decides the damage formula and the speed and shot stats
 * that apply; the engine integer travels in {@code magicTypeRaw} as a decimal string. Integers with no established
 * client meaning stay raw.
 */
public enum SkillMagicType {
    /** P. Atk. formula, Atk. Spd., soulshots. */
    PHYSICAL,
    /** M. Atk. formula, Casting Spd., spiritshots. */
    MAGIC,
    /** Fixed cast time, not affected by speed or shots. */
    STATIC,
    /** Song or dance. */
    SONG_DANCE
}
