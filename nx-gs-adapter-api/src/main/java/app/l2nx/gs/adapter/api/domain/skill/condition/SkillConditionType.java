package app.l2nx.gs.adapter.api.domain.skill.condition;

/**
 * Canonical predicate of a skill condition leaf ({@code SkillConditionNode.type}); the engine condition token
 * travels in {@code typeRaw}. Named {@code SUBJECT_PREDICATE}: subjects are {@code CASTER}, {@code TARGET}, {@code
 * SUMMON} (the caster's own summon), {@code CONTEXT} (the evaluation itself) and {@code WORLD}. An inverted check is
 * the same constant with {@code SkillConditionNode.negated} set; engine tokens with no exact predicate stay raw
 * and {@code type} is {@code null}.
 *
 * <p>Constant Javadoc lists the canonical {@code params} keys. A trailing {@code ?} marks an optional key; {@code
 * min?, max?} means at least one bound is present. Bounds are inclusive; {@code minLevel} and {@code maxLevel}
 * bound the level of a referenced skill or abnormal; list-valued keys carry API enum constant names or ids,
 * comma-separated, any-of.</p>
 */
public enum SkillConditionType {
    // CASTER - vitals, progression, identity
    /** Caster level; params: min?, max?. */
    CASTER_LEVEL,
    /** Current HP % of max; params: min?, max? (percent). */
    CASTER_HP_PERCENT,
    /** Current MP %; params: min?, max? (percent). */
    CASTER_MP_PERCENT,
    /** Current CP %; params: min?, max? (percent). */
    CASTER_CP_PERCENT,
    /** Maximum MP; params: min?, max?. */
    CASTER_MAX_MP,
    /** Base stat within bounds; params: stat (Stat: STR DEX CON INT WIT MEN), min?, max?. */
    CASTER_BASE_STAT,
    /** Acting player's race; params: characterRaces. */
    CASTER_RACE,
    /** Acting player's sex; params: characterSex (CharacterSex). */
    CASTER_SEX,
    /** Acting player's active class; params: characterClasses (CharacterClass[]). */
    CASTER_CLASS,
    /** A sub-class (not the main class) is active. */
    CASTER_IS_SUB_CLASS_ACTIVE,
    /** Acting player's expertise grade; params: itemGrades (ItemGrade[]). */
    CASTER_EXPERTISE_GRADE,
    /** Hero status. */
    CASTER_IS_HERO,
    /** Transformed (into a listed form); params: transformationIds?. */
    CASTER_IS_TRANSFORMED,

    // CASTER - alignment, social, clan
    /** Negative alignment (karma above zero). */
    CASTER_IS_CHAOTIC,
    /** PK kills; params: min?, max?. */
    CASTER_PK_COUNT,
    /** PvP flag on. */
    CASTER_IS_PVP_FLAGGED,
    /** In attack stance. */
    CASTER_IN_COMBAT,
    /** In a duel. */
    CASTER_IN_DUEL,
    /** In an Olympiad match. */
    CASTER_IN_OLYMPIAD,
    /** Leader of own clan. */
    CASTER_IS_CLAN_LEADER,
    /** Clan social class (pledge class) is at least min; params: min (SocialClass). */
    CASTER_SOCIAL_CLASS,
    /** Own clan level is at least min (min=0 means the caster has a clan); params: min. */
    CASTER_CLAN_LEVEL,
    /** Caster's clan owns a residence (of type / with id); params: residenceType? (ResidenceType), residenceIds?. */
    CASTER_CLAN_HAS_RESIDENCE,
    /** Territory War side; params: territoryIds. */
    CASTER_TERRITORY_SIDE,

    // CASTER - location, movement
    /** Inside one of the zones; params: zoneIds. */
    CASTER_IN_ZONE,
    /** Inside a zone of the given kind; params: zoneKinds (ZoneKind[]). */
    CASTER_IN_ZONE_KIND,
    /** Inside an in-progress siege battlefield (on that side); params: residenceIds?, siegeSide? (SiegeSide). */
    CASTER_IN_SIEGE,
    /** Inside an instance (of listed templates); params: instanceIds?. */
    CASTER_IN_INSTANCE,
    /** Aboard a boat / airship. */
    CASTER_IN_VEHICLE,
    /** Aboard an airship. */
    CASTER_IN_AIRSHIP,
    /** Riding a mount (of listed kind); params: mountTypes? (MountType[]). */
    CASTER_IS_MOUNTED,
    /** Airborne. */
    CASTER_IS_FLYING,
    /** Current posture/motion; params: movementStates (MovementState[]). */
    CASTER_MOVEMENT_STATE,
    /** Caster's position relative to the target's facing; params: relativePositions (RelativePosition[]). */
    CASTER_POSITION_TO_TARGET,
    /** A listed NPC is within radius; params: npcIds, radius. */
    CASTER_IS_NEAR_NPC,

    // CASTER - effects, skills, resources
    /** Active effect of the skill; params: skillId, minLevel?, maxLevel?. */
    CASTER_HAS_EFFECT,
    /** Abnormal of the type at that abnormal level; params: abnormalType, minLevel?, maxLevel?. */
    CASTER_HAS_ABNORMAL,
    /** Knows the skill; params: skillId, minLevel?, maxLevel?. */
    CASTER_HAS_SKILL,
    /** Charged souls; params: min?, max?, soulType? (SoulType; absent = untyped). */
    CASTER_SOULS,
    /** Souls at the cap; params: soulType?. */
    CASTER_HAS_MAX_SOULS,
    /** Charges (momentum/force); params: min?, max?. */
    CASTER_CHARGES,
    /** Class / character resource points; params: characterResource (CharacterResource), min?, max?. */
    CASTER_RESOURCE,
    /** Vitality points; params: min?, max?. */
    CASTER_VITALITY_POINTS,
    /** Inventory load %; params: min?, max? (percent). */
    CASTER_WEIGHT_PERCENT,
    /** Free inventory slots; params: min. */
    CASTER_FREE_INVENTORY_SLOTS,

    // CASTER - equipment, companions, gates
    /** Equipped gear of a listed type; params: weaponTypes? (WeaponType[]), armorTypes? (ArmorType[]). */
    CASTER_HAS_EQUIPPED_TYPE,
    /** Active weapon occupies both hands. */
    CASTER_HAS_TWO_HANDED_WEAPON,
    /** Active weapon is a magic weapon. */
    CASTER_HAS_MAGIC_WEAPON,
    /** Active weapon has a change-weapon counterpart. */
    CASTER_HAS_CHANGEABLE_WEAPON,
    /** A cursed weapon is equipped. */
    CASTER_HAS_CURSED_WEAPON,
    /**
     * Item(s) equipped (in slot, with at least the enchant); params: itemIds?, itemEquipSlot? (ItemEquipSlot),
     * minCount?, minEnchant?.
     */
    CASTER_HAS_EQUIPPED_ITEM,
    /** Worn armor opens the cloak slot. */
    CASTER_HAS_CLOAK_SLOT,
    /** Has a servitor (of listed NPC ids); params: npcIds?. */
    CASTER_HAS_SERVITOR,
    /** Has a pet (of listed control items); params: controlItemIds?. */
    CASTER_HAS_PET,
    /** Has spawned a listed NPC by skill; params: npcIds. */
    CASTER_HAS_SUMMONED_NPC,
    /** Has a recall creature. */
    CASTER_HAS_RECALL_CREATURE,
    /** An agathion is summoned; params: agathionIds?. */
    CASTER_HAS_AGATHION,
    /** The agathion bracelet has energy left. */
    CASTER_AGATHION_HAS_ENERGY,
    /**
     * Opaque engine gate: the action is currently permitted; params: skillConditionAction (SkillConditionAction),
     * amount?, transformationId?, range?.
     */
    CASTER_CAN_PERFORM,

    // SUMMON
    /** The caster's summon HP percent; params: min?, max? (percent). */
    SUMMON_HP_PERCENT,

    // TARGET
    /** Target level; params: min?, max?. */
    TARGET_LEVEL,
    /** Target HP %; params: min?, max? (percent). */
    TARGET_HP_PERCENT,
    /** Target MP %; params: min?, max? (percent). */
    TARGET_MP_PERCENT,
    /** Target CP %; params: min?, max? (percent). */
    TARGET_CP_PERCENT,
    /** Target race; params: characterRaces?, npcRaces? (NpcRace[]). */
    TARGET_RACE,
    /** Target player's class; params: characterClasses. */
    TARGET_CLASS,
    /** Target player has karma. */
    TARGET_IS_CHAOTIC,
    /** Target PK count; params: min?, max?. */
    TARGET_PK_COUNT,
    /** Target PvP-flagged. */
    TARGET_IS_PVP_FLAGGED,
    /** Target in attack stance. */
    TARGET_IN_COMBAT,
    /** Target monster is aggressive. */
    TARGET_IS_AGGRESSIVE,
    /** Target is under fear. */
    TARGET_IS_FEARED,
    /** Target is a dead monster or summon that is still spawned. */
    TARGET_IS_CORPSE,
    /** Target is an actor of a listed kind; params: actorKinds (ActorKind[]). */
    TARGET_KIND,
    /** Target NPC template id; params: npcIds. */
    TARGET_NPC_ID,
    /** Target door id; params: doorIds. */
    TARGET_DOOR_ID,
    /** Target is in the caster's party; params: includeCaster. */
    TARGET_IN_CASTER_PARTY,
    /** Target player is in the caster's clan. */
    TARGET_IN_CASTER_CLAN,
    /** Caster-to-target distance; params: min?, max? (game units). */
    TARGET_DISTANCE,
    /** Target inside an in-progress siege battlefield; params: residenceIds?, siegeSide?. */
    TARGET_IN_SIEGE,
    /** Target has an active effect of the skill; params: skillId, minLevel?, maxLevel?. */
    TARGET_HAS_EFFECT,
    /** Target has an abnormal of the type; params: abnormalType, minLevel?, maxLevel?. */
    TARGET_HAS_ABNORMAL,
    /** Target shows a listed abnormal visual effect; params: abnormalVisualEffects (engine tokens). */
    TARGET_HAS_ABNORMAL_VISUAL,
    /** Target knows the skill; params: skillId, minLevel?, maxLevel?. */
    TARGET_HAS_SKILL,
    /** Target's weapon / body armor type; params: weaponTypes?, armorTypes?. */
    TARGET_HAS_EQUIPPED_TYPE,
    /** Target has the item equipped; params: itemIds, minCount?. */
    TARGET_HAS_EQUIPPED_ITEM,
    /** Target load %; params: min?, max? (percent). */
    TARGET_WEIGHT_PERCENT,
    /** Target free inventory slots; params: min. */
    TARGET_FREE_INVENTORY_SLOTS,

    // CONTEXT, WORLD
    /** Random roll succeeds; params: chancePercent. */
    CONTEXT_CHANCE,
    /** The skill in evaluation context has a listed id; params: skillIds. */
    CONTEXT_SKILL_ID,
    /** Evaluated within a skill use (vs auto-attack / equip). */
    CONTEXT_IS_SKILL_USE,
    /** In-game time is night. */
    WORLD_IS_NIGHT
}
