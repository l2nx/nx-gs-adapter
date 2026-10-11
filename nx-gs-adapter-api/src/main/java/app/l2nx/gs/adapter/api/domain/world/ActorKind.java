package app.l2nx.gs.adapter.api.domain.world;

/**
 * Kind of world actor, used by skill conditions and trigger filters. Kinds overlap by design: {@code SUMMON} is a
 * servitor or a pet, {@code PLAYABLE} is a player or a summon. A filter lists several kinds as any-of.
 */
public enum ActorKind {
    PLAYER,
    /** A player or a summon. */
    PLAYABLE,
    /** A servitor or a pet. */
    SUMMON,
    SERVITOR,
    PET,
    NPC,
    /** Any attackable NPC. */
    ATTACKABLE,
    MONSTER,
    RAID_BOSS,
    /** Top-tier boss. */
    EPIC_BOSS,
    CHEST,
    TRAP,
    DOOR
}
