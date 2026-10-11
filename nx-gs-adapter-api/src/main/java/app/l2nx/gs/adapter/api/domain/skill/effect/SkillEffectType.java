package app.l2nx.gs.adapter.api.domain.skill.effect;

/**
 * Canonical effect a skill applies, one per {@code SkillEffect}; the engine effect token travels in {@code
 * typeRaw}. A constant exists only for a general game concept; engine handlers without an exact match stay raw and
 * {@code type} is {@code null}. One engine effect may yield several wire records (for example experience and skill
 * points).
 *
 * <p>Constant Javadoc gives the meaning, the unit of {@code SkillEffect.value} and the canonical keys of {@code
 * SkillEffect.params}, whose format is in the {@code SkillEffect} class doc. Shared keys: {@code chancePercent} (land
 * or proc chance), {@code skillId} with {@code skillLevel}, {@code skills} ({@code id:level} pairs), {@code
 * npcId}, {@code npcCount}, {@code lifetimeSec}, {@code abnormalTypes}, {@code operation} ({@code StatOperation}).
 * Trigger effects share the keys: skillId + skillLevel or skills, chancePercent, targetType (SkillTargetType of
 * the triggered cast), criticalOnly, normalAttack, skillAttack, magicTypes (SkillMagicType list), goodSkills,
 * badSkills, minDamage, minAttackerLevel, maxAttackerLevel, attackerKinds (ActorKind list), sourceSkillIds,
 * attributes (Attribute list), pvpOnly, levelFromSkillId, renewDuration.</p>
 *
 * <p>A modifier-only effect is {@link #STAT_MODIFIER}: its changes live in {@code SkillEffect.statModifiers}.</p>
 */
public enum SkillEffectType {
    // Damage
    /** Physical damage; value: power; params: chancePercent, criticalChancePercent, ignoreShield, overHit. */
    PHYSICAL_DAMAGE,
    /** Physical damage scaled by the number of targets hit; value: power; params: as PHYSICAL_DAMAGE. */
    PHYSICAL_DAMAGE_BY_TARGETS,
    /**
     * Physical damage empowered by consumed souls; value: power; params: criticalChancePercent, ignoreShield, overHit.
     */
    PHYSICAL_SOUL_DAMAGE,
    /** Physical damage that grows as HP drops; value: power; params: criticalChancePercent, overHit. */
    PHYSICAL_DAMAGE_BY_LOW_HP,
    /** Magic damage; value: power; params: overHit, bonusNpcRaces (NpcRace[]), bonusRaceRate, debuffBonusRate. */
    MAGIC_DAMAGE,
    /** Magic damage that the target shield defence partly absorbs; value: power; params: shieldDefPercent. */
    MAGIC_DAMAGE_SHIELDABLE,
    /** Magic damage empowered by consumed souls; value: power. */
    MAGIC_SOUL_DAMAGE,
    /** Magic damage that grows as HP drops; value: power. */
    MAGIC_DAMAGE_BY_LOW_HP,
    /** Magic damage boosted against targets under the listed abnormals; value: power; params: abnormalTypes. */
    MAGIC_DAMAGE_BY_ABNORMAL,
    /** Damage dealt to the target MP; value: power; params: critical, criticalLimit. */
    MP_DAMAGE,
    /** Fixed HP damage not reduced by defence; value: HP; params: operation. */
    FIXED_DAMAGE,
    /**
     * Blow (dagger) damage with a blow chance roll; value: power; params: blowChancePercent, blowChanceBonusPercent,
     * criticalChancePercent, overHit, abnormalTypes + abnormalPower (bonus against abnormals).
     */
    BLOW_DAMAGE,
    /**
     * Damage that requires attacking from behind; value: power; params: blowChanceBonusPercent, criticalChancePercent,
     * overHit.
     */
    BACKSTAB_DAMAGE,
    /** Blow damage empowered by consumed souls; value: power; params: blowChanceBonusPercent, overHit. */
    SOUL_BLOW_DAMAGE,
    /**
     * Physical damage empowered by consumed charges; value: power; params: criticalChancePercent, ignoreShield,
     * overHit.
     */
    CHARGE_DAMAGE,
    /** Damage that returns a share of the dealt amount to the caster as HP; value: power; params: absorbPercent. */
    HP_DRAIN,
    /**
     * Chance to kill the target outright (lethal strike) or to halve its HP; params: lethalChancePercent (full),
     * halfKillChancePercent.
     */
    INSTANT_KILL,
    /** Removes a percentage of the target current CP; value: percent of current CP. */
    CP_DAMAGE_PERCENT,
    /** Removes a percentage of the target current HP; value: percent of current HP. */
    HP_DAMAGE_PERCENT,
    /** Removes a percentage of the target current MP; value: percent of current MP. */
    MP_DAMAGE_PERCENT,
    /** Periodic HP damage; value: HP per tick; params: canKill. */
    DAMAGE_OVER_TIME,
    /** Periodic HP damage as a percentage of HP; value: percent of HP per tick; params: canKill. */
    DAMAGE_OVER_TIME_PERCENT,
    /** Periodic magic damage; value: power per tick; params: canKill. */
    MAGIC_DAMAGE_OVER_TIME,
    /** Periodic MP drain; value: MP per tick. */
    MP_DAMAGE_OVER_TIME,
    /** Periodic drain of the summoned agathion energy; value: energy per tick. */
    AGATHION_ENERGY_DAMAGE_OVER_TIME,

    // Healing, resurrection, death protection
    /** HP restore computed from the heal power formula; value: power. */
    HP_HEAL,
    /** HP restore as a percentage of maximum HP; value: percent of max HP. */
    HP_HEAL_PERCENT,
    /** Periodic HP restore; value: HP per tick. */
    HP_HEAL_OVER_TIME,
    /** Flat HP restore; value: HP. */
    HP_HEAL_FLAT,
    /** Restores HP, and CP once HP is full, from the heal power formula; value: power. */
    HP_CP_HEAL,
    /** CP restore; value: CP. */
    CP_HEAL,
    /** CP restore as a percentage of maximum CP; value: percent of max CP. */
    CP_HEAL_PERCENT,
    /** Periodic CP restore; value: CP per tick. */
    CP_HEAL_OVER_TIME,
    /** Flat CP restore; value: CP. */
    CP_HEAL_FLAT,
    /** MP restore computed from the heal power formula; value: power. */
    MP_HEAL,
    /** MP restore as a percentage of maximum MP; value: percent of max MP. */
    MP_HEAL_PERCENT,
    /** Periodic MP restore; value: MP per tick. */
    MP_HEAL_OVER_TIME,
    /** MP restore scaled by the target level; value: power. */
    MP_HEAL_BY_LEVEL,
    /** Flat MP restore; value: MP. */
    MP_HEAL_FLAT,
    /** Evens out current HP across the party members in range. */
    REBALANCE_PARTY_HP,
    /** Resurrects a dead target; value: EXP recovery percent; params: hpPercent, mpPercent, cpPercent. */
    RESURRECT,
    /**
     * Resurrects the holder after its next death; value: EXP recovery percent; params: hpPercent, mpPercent,
     * cpPercent, instanceIds.
     */
    RESURRECT_ON_DEATH,
    /**
     * Leaves the holder alive with the given vitals when it would take lethal damage; params: hp/hpPercent,
     * mp/mpPercent, cp/cpPercent, skillId+skillLevel (cast on survive).
     */
    SURVIVE_LETHAL_DAMAGE,
    /** Buffs are kept when the holder dies. */
    KEEP_BUFFS_ON_DEATH,
    /** No death penalty on the next death. */
    NO_DEATH_PENALTY,
    /** No death penalty on death in a siege. */
    NO_SIEGE_DEATH_PENALTY,
    /** No death penalty on death to a raid boss. */
    NO_RAID_DEATH_PENALTY,
    /** Protects from the penalty of being killed by a PK. */
    PK_DEATH_PROTECTION,
    /** Reduces the death penalty. */
    REDUCE_DEATH_PENALTY,

    // Stat, slots, shields
    /** Applies the modifiers listed in SkillEffect.statModifiers; params: chancePercent. */
    STAT_MODIFIER,
    /** Adds buff slots; value: slots. */
    INCREASE_BUFF_SLOTS,
    /** Periodic MP upkeep that depends on the skill level; value: MP per tick. */
    MP_UPKEEP_BY_LEVEL,
    /** Shield that absorbs incoming damage until used up; value: damage absorbed; params: hits, operation. */
    DAMAGE_ABSORB_SHIELD,
    /** Reduces incoming damage; value: amount; params: operation. */
    DAMAGE_REDUCTION,
    /** Blocks the next debuffs landing on the holder; value: count. */
    BLOCK_NEXT_DEBUFFS,
    /** Shield block works from every direction. */
    SHIELD_BLOCK_ALL_ANGLES,

    // Control, disable, block
    /** Stun. */
    STUN,
    /** Sleep. */
    SLEEP,
    /** Paralysis. */
    PARALYZE,
    /** Petrification. */
    PETRIFY,
    /** Blocks all actions except the listed skills; params: allowedSkillIds. */
    BLOCK_ALL_ACTIONS,
    /** Blocks the listed client actions; params: actionIds. */
    BLOCK_SPECIFIC_ACTIONS,
    /** Takes away player control of the character. */
    BLOCK_CONTROL,
    /** Root (cannot move). */
    ROOT,
    /** Fear (flees uncontrollably); params: range. */
    FEAR,
    /** Forces the target to run away; params: chancePercent, distance. */
    RUN_AWAY,
    /** Compels the target to act as ordered by the caster. */
    COMPEL,
    /** Confusion (attacks nearby creatures at random); params: chancePercent. */
    CONFUSION,
    /** Makes NPCs attack each other; params: chancePercent. */
    INCITE_NPC_INFIGHTING,
    /** Target turns against its own master or party. */
    BETRAY,
    /** Unequips the weapon. */
    DISARM,
    /** Unequips the shield; params: chancePercent. */
    DISARM_SHIELD,
    /** Unequips the armor in the given slot; params: equipSlot (ItemEquipSlot). */
    DISARM_ARMOR,
    /** Blocks magic skills (silence). */
    BLOCK_MAGIC_SKILLS,
    /** Blocks physical skills. */
    BLOCK_PHYSICAL_SKILLS,
    /** Blocks normal physical attacks. */
    BLOCK_PHYSICAL_ATTACK,
    /** Blocks the listed skills or skill magic types; params: skillIds, magicTypes (SkillMagicType[]). */
    BLOCK_SKILLS,
    /** Blocks being resurrected. */
    BLOCK_RESURRECTION,
    /** Blocks chat. */
    BLOCK_CHAT,
    /** Blocks party invitations and membership. */
    BLOCK_PARTY,
    /** Blocks escape and teleport skills. */
    BLOCK_ESCAPE,
    /** Blocks rush (dash) skills against the holder. */
    BLOCK_RUSH,
    /** Blocks the listed abnormal types from occupying slots; params: abnormalTypes. */
    BLOCK_ABNORMAL_SLOTS,
    /** Blocks incoming debuffs. */
    BLOCK_DEBUFFS,
    /** Blocks incoming buffs. */
    BLOCK_BUFFS,
    /** Blocks incoming HP and/or MP damage; params: blockHp, blockMp. */
    BLOCK_DAMAGE,
    /** Invulnerable to damage; params: blockBuffs, blockDebuffs. */
    INVULNERABILITY,
    /** Immune to the listed skills; params: skills. */
    SKILL_IMMUNITY,
    /** The holder cannot move while the effect lasts. */
    IMMOBILIZE_SELF,
    /** The holder pet or servitor cannot move. */
    IMMOBILIZE_PET,
    /** Pushes the target away from the caster; params: distance, speed, knockDown. */
    KNOCK_BACK,
    /** Throws the target into the air; params: distance, speed. */
    THROW_UP,
    /** Pulls the target to the caster; params: speed. */
    PULL_TO_CASTER,
    /** Pulls the caster to the target; params: radius, speed. */
    PULL_TO_TARGET,
    /** Instant relocation of the caster; params: range, angle. */
    BLINK,
    /** Swaps positions of caster and target. */
    SWAP_POSITIONS,
    /** Caster rushes to the target. */
    RUSH_TO_TARGET,
    /** Turns the target away from the caster; params: chancePercent. */
    TURN_TARGET_AWAY,
    /** Interrupts the target cast; params: chancePercent, fixedChance. */
    INTERRUPT_CAST,
    /** Makes the target lose its current target; params: chancePercent. */
    CANCEL_TARGET,
    /** Forces the target to select the caster. */
    FORCE_TARGET_CASTER,
    /** Chance to force the target to select the caster; params: chancePercent. */
    FORCE_TARGET_CASTER_CHANCE,
    /** Forces the target to attack the caster. */
    FORCE_ATTACK_CASTER,
    /** Locks caster and target into one-on-one combat. */
    FACE_OFF,
    /** Monsters do not aggro the holder. */
    DISABLE_AGGRESSION,
    /** Polearm attacks hit the main target only. */
    POLEARM_SINGLE_TARGET,
    /** The holder cannot be selected as a target. */
    UNTARGETABLE,
    /** The holder cannot select targets. */
    BLOCK_TARGETING,

    // Hate
    /** Adds hate towards the caster; value: hate; params: includeSummoner. */
    ADD_HATE,
    /** Clears all hate of the target; params: chancePercent. */
    REMOVE_ALL_HATE,
    /** Clears the hate the target holds towards the caster; params: chancePercent. */
    REMOVE_CASTER_HATE,
    /** Shuffles the hate list of the target; params: chancePercent. */
    RANDOMIZE_HATE,
    /** Transfers hate from the caster to another creature; params: chancePercent. */
    TRANSFER_HATE,

    // Dispel and abnormal manipulation
    /**
     * Dispels abnormals of an effect category; params: skillEffectCategory (BUFF/DEBUFF; absent = all), chancePercent,
     * minCount, maxCount.
     */
    DISPEL_BY_CATEGORY,
    /** Dispels every dispellable abnormal; params: chancePercent. */
    DISPEL_ALL,
    /** Dispels abnormals by abnormal type up to the given abnormal level; params: abnormalTypeMaxLevels. */
    DISPEL_BY_SLOT,
    /** Dispels abnormals by abnormal type with a chance; params: abnormalTypes, chancePercent. */
    DISPEL_BY_SLOT_PROBABILITY,
    /** Dispels abnormals of the listed types from the caster; params: abnormalTypes. */
    DISPEL_BY_SLOT_SELF,
    /** Dispels the effects of the listed skills; params: skillIds, chancePercent. */
    DISPEL_BY_SKILL,
    /**
     * Steals abnormals from the target and applies them to the caster; params: skillEffectCategory, chancePercent,
     * minCount, maxCount.
     */
    STEAL_ABNORMALS,
    /** Ends the current transformation. */
    UNTRANSFORM,
    /**
     * Changes the remaining duration of abnormals; value: seconds; params: abnormalTypes or skillIds, operation
     * (ADD/SET).
     */
    ABNORMAL_TIME_CHANGE,

    // Stealth, state, alignment
    /** Invisible to other players. */
    INVISIBILITY,
    /** Reveals hidden creatures in range. */
    REVEAL_HIDDEN,
    /** Movement does not alert monsters. */
    SILENT_MOVEMENT,
    /** Fake death stance. */
    FAKE_DEATH,
    /** Sit and recover (relax) state. */
    RELAX,
    /** Sit state that breaks when the character acts. */
    STEALTH_REST,
    /** Raises the PvP flag. */
    SET_PVP_FLAG,
    /** Changes karma; value: karma; params: operation. */
    MODIFY_KARMA,
    /** Changes the PK kill count; value: count. */
    MODIFY_PK_COUNT,
    /** Cosmetic oversized head. */
    BIG_HEAD,

    // Summons, movement, teleport
    /** Summons a servitor; params: npcId, lifetimeSec, consumeItemId, consumeItemCount, consumeIntervalSec, expRate. */
    SUMMON_SERVITOR,
    /** Summons a pet. */
    SUMMON_PET,
    /** Summons NPCs; params: npcId, npcCount, lifetimeSec, randomOffset, onTarget, singleInstance, aggressive. */
    SUMMON_NPC,
    /** Summons guardian NPCs; params: npcId, npcCount, lifetimeSec, singleInstance, clone. */
    SUMMON_GUARDIAN,
    /** Summons a shadow NPC; params: npcId, lifetimeSec. */
    SUMMON_SHADOW,
    /** Places a trap; params: npcId, lifetimeSec. */
    SUMMON_TRAP,
    /** Summons a cubic; params: cubicId, cubicLevel, lifetimeSec, maxCount, chancePercent. */
    SUMMON_CUBIC,
    /** Summons an agathion; params: npcId. */
    SUMMON_AGATHION,
    /** Dismisses the servitor; params: chancePercent. */
    UNSUMMON_SERVITOR,
    /** Dismisses the agathion. */
    UNSUMMON_AGATHION,
    /** Dismisses a cubic; params: cubicId. */
    UNSUMMON_CUBIC,
    /** Dismisses the summoned guardians. */
    UNSUMMON_GUARDIANS,
    /** Servitor inherits a share of the owner stats. */
    SHARE_STATS_WITH_SUMMON,
    /** Pet inherits a share of the owner stats. */
    SHARE_STATS_WITH_PET,
    /** Summon can use the owner skills. */
    SHARE_SKILLS_WITH_SUMMON,
    /** Summon casts a skill on the owner command; params: skills, ownerSkillId, npcId. */
    SUMMON_ASSIST_CAST,
    /** Summon attacks on the owner command; params: npcId. */
    SUMMON_ASSIST_ATTACK,
    /** Summoned NPCs cast the given skill; params: skillId, skillLevel. */
    SUMMONED_NPCS_CAST_SKILL,
    /** Mounts the character; params: npcId. */
    MOUNT,
    /** Feeds the pet; value: food. */
    FEED_PET,
    /** Teleports to fixed coordinates; params: x, y, z. */
    TELEPORT_TO_LOCATION,
    /** Teleports to a destination chosen from the teleport list. */
    TELEPORT_TO_LIST_DESTINATION,
    /** Escape teleport to a kind of place; params: escapeDestination (EscapeDestination). */
    ESCAPE,
    /** Teleports to the target. */
    TELEPORT_TO_TARGET,
    /** Teleports to the own summon; params: distance. */
    TELEPORT_TO_SUMMON,
    /** Summons another player to the caster; params: itemId, itemCount (fee). */
    SUMMON_PLAYER,
    /** Summons party members to the caster. */
    SUMMON_PARTY,
    /** Opens a clan gate for clan members. */
    CLAN_GATE,

    // Skill chaining and triggers
    /** Casts the listed skills; params: skills, abnormalTypes or requiredEffectSkillId (optional checks). */
    CAST_SKILL,
    /** Casts one skill picked at random from the list; params: skills. */
    CAST_RANDOM_SKILL,
    /** Casts a skill selected by the target race; params: skillsByRace (CharacterRace:skillId), skillLevel. */
    CAST_SKILL_BY_TARGET_RACE,
    /** Casts one skill on allies and another on enemies; params: allySkills, enemySkills. */
    CAST_SKILL_BY_TARGET_RELATION,
    /** Casts a skill selected by the caster-to-target distance; params: skillsByMaxDistance (distance:id:level). */
    CAST_SKILL_BY_DISTANCE,
    /** Casts a combo skill when the required effects are present; params: requiredEffectSkillIds, comboSize (2/3). */
    CAST_COMBO_SKILL,
    /** Casts a skill after a delay; params: delayMs. */
    CAST_SKILL_DELAYED,
    /** Applies the effects of the listed skills without casting them; params: skills. */
    APPLY_SKILL_EFFECTS,
    /** Temporarily replaces one skill with another; params: replacementSkillId, replacementSkillLevel. */
    REPLACE_SKILL,
    /** Teaches skills for the duration of the effect. */
    LEARN_SKILL,
    /** Changes the reuse delay of skills; value: ms. */
    MODIFY_SKILL_REUSE,
    /**
     * Synergy bonus when party members hold the required abnormals; params: requiredAbnormalTypes,
     * optionalAbnormalTypes, minCount.
     */
    PARTY_BUFF_SYNERGY,
    /** Casts a skill when the holder attacks; params: common trigger keys. */
    TRIGGER_SKILL_ON_ATTACK,
    /** Casts a skill when the holder is attacked; params: common trigger keys, hpPercent. */
    TRIGGER_SKILL_ON_ATTACKED,
    /** Casts a skill when the holder takes lethal damage; params: common trigger keys. */
    TRIGGER_SKILL_ON_LETHAL_DAMAGE,
    /** Casts a skill when the holder evades an attack; params: common trigger keys. */
    TRIGGER_SKILL_ON_EVADE,
    /** Casts a skill when the holder uses a skill; params: common trigger keys. */
    TRIGGER_SKILL_ON_SKILL_USE,
    /** Casts a skill when a skill lands on the holder; params: common trigger keys. */
    TRIGGER_SKILL_ON_SKILL_RECEIVED,
    /** Casts a skill when the holder kills a creature; params: common trigger keys, victimKinds (ActorKind[]). */
    TRIGGER_SKILL_ON_KILL,
    /** Casts a skill when an enemy dies nearby; params: common trigger keys. */
    TRIGGER_SKILL_ON_ENEMY_DEATH,
    /** Casts a skill periodically; params: common trigger keys (period = tickIntervalMs). */
    TRIGGER_SKILL_ON_INTERVAL,
    /** Casts a skill when the effect starts; params: common trigger keys. */
    TRIGGER_SKILL_ON_EFFECT_START,
    /** Casts a skill when the effect ends; params: common trigger keys. */
    TRIGGER_SKILL_ON_EFFECT_END,
    /** Casts a skill when HP crosses a percentage band; params: common trigger keys, min, max (HP percent). */
    TRIGGER_SKILL_ON_HP_PERCENT,
    /** Casts a skill when a stat is inside a range; params: common trigger keys, stat (Stat), min, max. */
    TRIGGER_SKILL_ON_STAT_RANGE,
    /** Casts a skill when combat power is inside a range; params: common trigger keys, min, max. */
    TRIGGER_SKILL_ON_COMBAT_POWER,

    // Resources, economy, appearance
    /** Adds charges; value: charges; params: maxCharges. */
    GAIN_CHARGES,
    /** Fills charges up to the cap. */
    FILL_CHARGES,
    /** Adds souls; value: souls; params: soulType. */
    GAIN_SOULS,
    /** Collects souls from nearby deaths; params: expNeeded, soulType. */
    GAIN_SOULS_FROM_DEATHS,
    /** Changes a character resource pool; value: amount; params: characterResource (CharacterResource). */
    MODIFY_CHARACTER_RESOURCE,
    /** Changes the cap of a character resource pool; value: amount; params: characterResource. */
    MODIFY_CHARACTER_RESOURCE_MAX,
    /** Consumes the target corpse. */
    CONSUME_CORPSE,
    /** Spoils a monster so it can be swept. */
    SPOIL,
    /** Sweeps the spoiled items of a corpse. */
    SWEEP,
    /** Takes items from a monster. */
    PLUNDER,
    /** Sows a seed on a monster. */
    SOW_SEED,
    /** Harvests a sown monster. */
    HARVEST,
    /** Casts the fishing line. */
    FISHING_CAST,
    /** Fishing pumping action. */
    FISHING_PUMP,
    /** Fishing reeling action. */
    FISHING_REEL,
    /** Changes the fishing mastery level. */
    CHANGE_FISHING_MASTERY,
    /** Unlocks a door; params: chancePercent. */
    OPEN_DOOR,
    /** Unlocks a chest. */
    OPEN_CHEST,
    /** Detects hidden traps. */
    DETECT_TRAP,
    /** Disarms a trap. */
    DISARM_TRAP,
    /** Gives the fixed items of SkillLevel.producedItems. */
    GIVE_ITEMS,
    /** Gives items picked at random from SkillLevel.producedItems. */
    GIVE_RANDOM_ITEMS,
    /** Converts one item into another. */
    CONVERT_ITEM,
    /** Gives experience; value: EXP; params: percent (percentage of the level EXP), maxLevel. */
    GIVE_EXP,
    /** Gives skill points; value: SP. */
    GIVE_SP,
    /** Gives experience to the pet; value: EXP. */
    GIVE_PET_EXP,
    /** Gives recommendations; value: count. */
    GIVE_RECOMMENDATIONS,
    /** Gives fame; value: fame. */
    GIVE_FAME,
    /** Gives clan reputation; value: reputation. */
    GIVE_CLAN_REPUTATION,
    /** Gives PC cafe points; value: points. */
    GIVE_PC_CAFE_POINTS,
    /** Gives vitality points; value: points. */
    GIVE_VITALITY_POINTS,
    /** Gives VIP points; value: points. */
    GIVE_VIP_POINTS,
    /** Grants Sayha Grace time. */
    GRANT_SAYHA_GRACE,
    /** Pauses the hunting bonus timer. */
    PAUSE_HUNTING_BONUS,
    /** Adds time to a hunting zone allowance; value: seconds; params: zoneId. */
    ADD_HUNTING_ZONE_TIME,
    /** Adds teleport bookmark slots; value: slots. */
    EXPAND_TELEPORT_BOOKMARKS,
    /** Resets the entry limit of instances; params: instanceIds. */
    RESET_INSTANCE_ENTRY,
    /** Opens the common recipe book. */
    OPEN_COMMON_RECIPE_BOOK,
    /** Opens the dwarven recipe book. */
    OPEN_DWARVEN_RECIPE_BOOK,
    /** Adds airship fuel; value: fuel. */
    REFUEL_AIRSHIP,
    /** Transforms the character; params: transformationId. */
    TRANSFORM,
    /** Changes the face style; value: style index. */
    CHANGE_FACE,
    /** Changes the hair color; value: style index. */
    CHANGE_HAIR_COLOR,
    /** Changes the hair style; value: style index. */
    CHANGE_HAIR_STYLE,
    /** Shows or hides the hair accessory. */
    TOGGLE_HAIR_ACCESSORY,
    /** Changes the character model size. */
    GROW_SIZE,
    /** Switches the active class; params: classIndex. */
    SWITCH_CLASS,
    /** Sends a system message to the clan; params: systemMessageId. */
    NOTIFY_CLAN,

    // Siege and events
    /** Builds a siege headquarters; params: advanced. */
    CREATE_SIEGE_HEADQUARTERS,
    /** Builds a Territory War outpost. */
    CREATE_TERRITORY_OUTPOST,
    /** Destroys a Territory War outpost. */
    DESTROY_TERRITORY_OUTPOST,
    /** Engraves the castle artifact. */
    TAKE_CASTLE,
    /** Starts engraving the castle artifact. */
    TAKE_CASTLE_START,
    /** Captures the fortress flag. */
    TAKE_FORTRESS,
    /** Starts capturing the fortress flag. */
    TAKE_FORTRESS_START,
    /** Captures a Territory War flag. */
    TAKE_TERRITORY_FLAG,
    /** Flips a block in the block game. */
    FLIP_BLOCK
}
