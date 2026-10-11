# gd-sync `skill` contract (nx-gs-adapter-api 0.101.0)

This file is the living, build-agnostic contract of the `skill` gd-sync record. It is kept in sync with the `gd.skill`
DTOs and the skill/stat vocabulary enums (`domain.skill.*`, `domain.stat.*`, `domain.world`, `domain.clan`,
`domain.character.state`, `domain.character.resource`, `domain.item`) in the same commit: any change to one updates
the other. The feature spec is [`spec.md`](spec.md); this file holds the wire shape and the vocabulary tables only.

0.101.0 is a **breaking** release: types moved to sub-packages, fields were renamed or removed, several fields kept
their name with a new type. Nothing is kept for compatibility: no deprecated fields or classes, no legacy names. See
"Changes from 0.100" (§2.11) and "Compatibility" at the end.

## 1. Naming rules

**N1. Canonical / raw pair.** Every vocabulary value travels as the canonical value under the plain field name and the
host engine's own token under `<name>Raw`. One rule, no exceptions:

- `<name>`: canonical value, an API enum (`operateType: SkillOperateType`) or a canonical map (`params`); nullable.
- `<name>Raw`: the engine token exactly as the engine has it: `String`, `List<String>` or `Map<String,String>`.
- A DTO whose single vocabulary value is its identity uses `type` + `typeRaw` (`SkillEffect`, `SkillConditionNode`).
- A canonical field without a `Raw` companion is derived or 1:1 lossless (`effectCategory`, `clazz`, `races`,
  `socialClass`), or its engine value is already on the wire in another field (`SkillProducedItem.rewardKind`, raw =
  `itemTemplateId`).
- A raw field without a canonical companion still carries the suffix: `skillTypeRaw` (a raw skill type with no
  canonical enum), `abnormalTypeRaw`, `abnormalVisualEffectsRaw`, `negateAbnormalTypesRaw` (abnormal types and visual
  effects are client data per chronicle, never API enums, N4). So `Raw` on the wire always means "engine token, per
  host" and a plain vocabulary name is always host-agnostic.
- No `Code` suffix anywhere in the API.

**N2. No lossy mapping.** `<name> = null` when the engine token has no exact canonical meaning; `<name>Raw` is always
sent when the engine has it. The host never invents a mapping. Inversions are allowed only when exact (N7).

**N3. One engine object to N wire records.** One engine effect / func / condition object may yield several
`SkillEffect` / `SkillStatModifier` / `SkillConditionNode` records; each carries the same raw token (`typeRaw`,
`statRaw`) and its own params. Examples: an experience-and-SP effect yields `GIVE_EXP` + `GIVE_SP`; a shot-bonus stat
handler yields modifiers `SOULSHOT_DAMAGE` + `SPIRITSHOT_DAMAGE`; a trigger effect whose mask covers two trigger kinds
yields two trigger effects.

**N4. Params.** On both `SkillEffect` and `SkillConditionNode`: `params` = canonical map, keys defined in §3.10 /
§3.11 per type; `paramsRaw` = raw engine attribute map exactly as the engine has it (keys and values untouched).
`params` is `null` when `type` is `null`. Value formatting of `params`:

- keys lowerCamel, stable per type; a key is absent when it does not apply; never an empty string (null-over-blank);
- `min` / `max`: inclusive bounds of the node's own measure; a strict engine bound is normalized (`< 80` becomes
  `max=79`);
- `minLevel` / `maxLevel`: inclusive bounds on a referenced sub-object's level; exact level = both equal;
- integers decimal; decimals with `.`; booleans `true` / `false`; percents as `<name>Percent`, int 0-100 unless the
  key says otherwise; durations carry the unit in the key (`...Ms`, `...Sec`);
- id lists: key `<thing>Ids`, comma-separated decimal ints, any-of; single id `<thing>Id`;
- skill references: single `skillId` + `skillLevel`; several `skills` = `id:level,id:level` (level `0` = any);
- map-valued: `KEY:VALUE,KEY:VALUE` (e.g. `abnormalTypeMaxLevels = STUN:9,ROOT:-1`, `-1` = any level);
- enum-valued key = lowerCamel of the enum's simple name, singular for one value (`soulType`), plural for an any-of
  list (`characterRaces`, `actorKinds`); values are API enum constant names, comma-separated;
- engine data ids (npc, item, skill, zone, door, instance, residence, transformation, cubic, agathion) pass as is;
- abnormal types and abnormal visual effects are engine tokens (client data, per chronicle), never API enums.

**N5. Constant naming.** UPPER_SNAKE, named by meaning, full words. Allowed abbreviations (one list for every enum):
`P M ATK DEF SPD HP MP CP PVP PVE PK NPC EXP SP AOE CRIT RES INV ID MAX MIN STR DEX CON INT WIT MEN CHA LUC LCOIN`.
Suffix rules: probability `_CHANCE`; multiplier `_RATE`; flat variant of a relative stat `_ADD`; defender-side axis
`_RES` (higher = more resistant, N7); PvP/PvE defender multipliers `_DEF`; cap `_LIMIT` or `MAX_`; slot counts
`_SLOTS`. Magic-type splits use the `SkillMagicType` words: `P_SKILL_`, `M_SKILL_`, `STATIC_SKILL_`, `SONG_DANCE_`.

**N6. Fixed terms** (one word per concept across all enums):

| concept                         | term                                   | not                                |
| ------------------------------- | -------------------------------------- | ---------------------------------- |
| remove abnormals                | `DISPEL`                               | CANCEL                             |
| restore HP                      | `HP_HEAL`                              | HEAL                               |
| servitor (not pet)              | `SERVITOR`; `SUMMON` = servitor or pet | SUMMON for servitor                |
| top boss                        | `EPIC_BOSS`                            | GRAND_BOSS                         |
| clan / alliance                 | `CLAN`, `ALLY`                         | PLEDGE, UNION                      |
| fortress                        | `FORTRESS`                             | FORT                               |
| momentum / energy / force       | `CHARGES`                              | MOMENTUM, ENERGY                   |
| dead creature as target         | `CORPSE`                               | BODY, DEAD (except affect scopes)  |
| player                          | `PLAYER`                               | PC                                 |
| point-blank (centred on caster) | `POINT_BLANK`                          | PB, AURA (outside SkillTargetType) |

**N7. `_RES` orientation.** On the wire every `_RES` stat means "higher = more resistant". An engine stat oriented the
other way (vulnerability-style stats, crit-defence stats, cancel/debuff/damage-zone vulnerability, resist-abnormal
percent stats) maps to `_RES` only through an exact inversion done by the host:

- `ADD x` and `SUBTRACT x` swap (value untouched);
- `PERCENT x` becomes `PERCENT -x` (exact: percents of one stat are summed linearly);
- `MULTIPLY`, `DIVIDE`, `SET`, `BASE_MULTIPLY` on such a stat give `stat = null`, `statRaw` kept.

Stats already "higher = more resistant" in the engine (element resistances, trait defences, magic success
resistance, abnormal resistances, real-damage resistance, shot resistances) map as is. Each Stat group in §3.1 states
the orientation.

**N8. Condition predicates.** `<SUBJECT>_<PREDICATE>`. Subjects: `CASTER`, `TARGET`, `SUMMON` (the caster's own
summon), `CONTEXT` (the evaluation itself), `WORLD`. Measure becomes a noun with `min`/`max`; status `IS_`;
possession `HAS_`; location/activity `IN_`; engine gate `CAN_PERFORM`. Every engine boolean (`val`, `required`,
`isWithin`, `not`, `less`) becomes `negated`; constants are stated affirmatively.

**N9. Inclusion.** A concept is canonical if it is a general L2 concept (HighFive lineage or Essence) with at least one
retail data row (handlers / enum values with zero data rows in every known datapack are dead and stay raw). Excluded:
server customs, engine-internal flags without a game meaning, unimplemented stubs.

**N10. Compatibility.** Wire JSON of 0.101.0 is not compatible with 0.100; consumers deserializing `skill` must ship
together with the producer. Existing enum constant names are unchanged. Vocabulary lives in adapter-api (`domain.*`);
platform-side libraries keep only presentation text keyed by these enums.

## 1a. Package layout

Platform canon: 2+ files of one kind go to their own sub-package; a single file stays in the parent; no flat dumps.
Applied to every type in this release, existing ones included (moved, no copies left behind).

Prefix `app.l2nx.gs.adapter.api.`:

| package                           | new types                                                                | moved here (from)                                          | stays                                                                             |
| --------------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `domain`                          |                                                                          |                                                            | `Attribute` (single file)                                                         |
| `domain.skill`                    | `SkillTreeKind`, `SkillRewardKind`                                       |                                                            | `SkillEnchant`                                                                    |
| `domain.skill.kind`               | `SkillOperateType`, `SkillMagicType`, `SkillBasicProperty`               | `SkillOperation`, `SkillTrait` (`domain.skill`)            |                                                                                   |
| `domain.skill.effect`             | `SkillEffectType`, `SkillEffectScope`, `EscapeDestination`               | `SkillEffectCategory` (`domain.skill`)                     |                                                                                   |
| `domain.skill.condition`          | `SkillConditionType`, `SkillConditionLogic`, `SkillConditionAction`      |                                                            |                                                                                   |
| `domain.skill.target`             | `SkillAffectScope`, `SkillAffectObject`                                  | `SkillTargetType` (`domain.skill`)                         |                                                                                   |
| `domain.stat`                     | `StatOperation`                                                          |                                                            | `Stat`, `StatType`                                                                |
| `domain.item`                     | `ItemGrade`                                                              | `WeaponType` (`domain`)                                    | `ArmorType`, `EtcItemType`, `ItemClass`, `ItemEquipSlot`, `ItemLocation`          |
| `domain.world` (new)              | `ActorKind`, `ZoneKind`                                                  |                                                            |                                                                                   |
| `domain.clan` (new)               | `SocialClass`, `ResidenceType`, `SiegeSide`                              |                                                            |                                                                                   |
| `domain.character.state` (new)    | `MovementState`, `RelativePosition`, `MountType`                         |                                                            |                                                                                   |
| `domain.character.resource` (new) | `SoulType`, `CharacterResource`                                          |                                                            |                                                                                   |
| `kafka.sync.gd.skill`             |                                                                          |                                                            | `Skill`, `SkillLevel`, `SkillFlags`, `SkillEnchantRoute`, `GearScoreContribution` |
| `kafka.sync.gd.skill.effect`      |                                                                          | `SkillEffect`, `SkillStatModifier` (`gd.skill`)            |                                                                                   |
| `kafka.sync.gd.skill.condition`   | `SkillConditionNode`                                                     |                                                            |                                                                                   |
| `kafka.sync.gd.skill.learn`       | `SkillLearn`, `SkillLearnItemSet`, `SkillLearnSubClass`, `SkillLevelRef` | `SkillLearnItem` (`gd.skill`)                              |                                                                                   |
| `kafka.sync.gd.skill.produced`    |                                                                          | `SkillProducedItem`, `SkillProducedItemGroup` (`gd.skill`) |                                                                                   |

Placement notes: `SkillTreeKind` / `SkillRewardKind` are the only enum of their concept (learn / produced) and stay in
the `domain.skill` root with `SkillEnchant` (a runtime value class, not a vocabulary). `SkillOperation` sits in `kind`
because it is the fold of `SkillOperateType` (§3.4). `SkillEffectCategory` sits in `effect`: it is the status-bar
group the runtime `CharacterEffect` carries. `domain.character` keeps `CharacterPrivateStore`, `CharacterRace`,
`CharacterSex` and `clazz/` as they are. Removed classes (`SkillCondition`, `SkillClassLearn`): §2.11.

## 2. DTO changes

Root package of the skill DTOs: `app.l2nx.gs.adapter.api.kafka.sync.gd.skill` (sub-packages per §1a). All fields are
`@Nullable` unless the table says `non-null`. `null` = "the host does not supply it" unless the semantics column says
otherwise. Removed and retyped fields: §2.11.

### 2.1 `Skill` (aggregate)

| field                      | type                  | null     | semantics                                                                                                                                                                       |
| -------------------------- | --------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `operateType`              | `SkillOperateType`    | nullable | client operate type                                                                                                                                                             |
| `operateTypeRaw`           | `String`              | nullable | engine operate-type token                                                                                                                                                       |
| `skillTypeRaw`             | `String`              | nullable | raw skill-type token; no canonical enum (hosts may derive effects from it, see Host guidance)                                                                                   |
| `targetType`               | `SkillTargetType`     | nullable | canonical target type: single-axis tokens embed the shape, selection tokens do not. Shape is read from `affectScope`/`affectObject`, which every host fills (see Host guidance) |
| `targetTypeRaw`            | `String`              | nullable | engine target-type token                                                                                                                                                        |
| `affectScope`              | `SkillAffectScope`    | nullable | which creatures around the selected target are affected; derived from the target type when the engine has no scope token (see Host guidance)                                    |
| `affectScopeRaw`           | `String`              | nullable | engine affect scope token; `null` when derived (no engine token)                                                                                                                |
| `affectObject`             | `SkillAffectObject`   | nullable | relation filter on affected creatures; derived from the target type when the engine has no object token (see Host guidance)                                                     |
| `affectObjectRaw`          | `String`              | nullable | engine affect object token; `null` when derived                                                                                                                                 |
| `trait`                    | `SkillTrait`          | nullable |                                                                                                                                                                                 |
| `traitRaw`                 | `String`              | nullable | engine trait token                                                                                                                                                              |
| `magicType`                | `SkillMagicType`      | nullable |                                                                                                                                                                                 |
| `magicTypeRaw`             | `String`              | nullable | engine `isMagic` / `magicType` int as decimal string                                                                                                                            |
| `basicProperty`            | `SkillBasicProperty`  | nullable | which abnormal-resist roll the skill uses                                                                                                                                       |
| `basicPropertyRaw`         | `String`              | nullable | engine basic-property token; `null` for hosts that express the roll through `saveVs`                                                                                            |
| `saveVs`                   | `Stat`                | nullable | base stat of the save roll: only `STR DEX CON INT WIT MEN`                                                                                                                      |
| `saveVsRaw`                | `String`              | nullable | engine save-vs token                                                                                                                                                            |
| `abnormalTypeRaw`          | `String`              | nullable | engine abnormal type (client data per chronicle, N1)                                                                                                                            |
| `abnormalVisualEffectsRaw` | `List<String>`        | nullable | engine abnormal visual effect tokens                                                                                                                                            |
| `effectCategory`           | `SkillEffectCategory` | nullable | same host function as runtime character effects                                                                                                                                 |
| `learns`                   | `List<SkillLearn>`    | nullable | every skill-tree entry that teaches this skill (all tree kinds); `null` = taught by no tree                                                                                     |

Unchanged: `id`, `sharedReuseGroup`, `icon`, `maxLevel`, `flags`, `levels`, `enchantRoutes`, `gearScoreContributions`.

Header vs level: target / affect / magic type / basic property / trait are read from the skill's canonical level
(as `operateType` is). If the levels of one skill differ in these values, the host emits the canonical level's values.

### 2.2 `SkillLevel`

| field                      | type                  | null     | semantics                                                                                         |
| -------------------------- | --------------------- | -------- | ------------------------------------------------------------------------------------------------- |
| `conditionTree`            | `SkillConditionNode`  | nullable | all use conditions of this level as one tree (implicit list → `AND` root); `null` = unconditional |
| `blowChancePercent`        | `Integer`             | nullable | base blow chance of blow skills                                                                   |
| `attribute`                | `Attribute`           | nullable | element of the level                                                                              |
| `attributeRaw`             | `String`              | nullable | engine element token                                                                              |
| `channelingMpConsume`      | `Integer`             | nullable | MP per channeling tick                                                                            |
| `channelingSkillId`        | `Integer`             | nullable | skill applied on every channeling tick                                                            |
| `channelingTickIntervalMs` | `Integer`             | nullable | server-effective tick period                                                                      |
| `channelingStartDelayMs`   | `Integer`             | nullable | delay before the first tick                                                                       |
| `negateAbnormalTypesRaw`   | `Map<String,Integer>` | nullable | keys = engine abnormal-type tokens, value = max abnormal level negated                            |

`statModifiers` keeps its meaning (modifiers applied directly by the skill level without an effect wrapper: level
funcs, item-like passives), and hosts do **not** synthesize `STAT_MODIFIER` effects from it (no double counting: an
engine modifier lives in exactly one place). All other fields unchanged.

### 2.3 `SkillFlags`

| field         | type      | null     | semantics                                                          |
| ------------- | --------- | -------- | ------------------------------------------------------------------ |
| `staticReuse` | `Boolean` | nullable | reuse delay is not affected by reuse stats                         |
| `triggered`   | `Boolean` | nullable | the skill is cast only by a trigger and uses the trigger buff slot |

Kept unchanged: `debuff`, `offensive`, `blockedInOlympiad`, `overHit`, `ignoreShield`, `nextActionAttack`,
`dispellable`, `reflectable`, `stayAfterDeath`. Removed: `magic`, `staticSkill`, `passive`, `toggle`, `heroSkill`,
`clanSkill` (§2.11).

### 2.4 `SkillEnchantRoute`

The route carries only route identity, enchant costs and the enchanted level as a whole `SkillLevel`. Complete field
list in 0.101:

| field                                     | type                   | null     | semantics                                                                                                                                                              |
| ----------------------------------------- | ---------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `baseLevel`, `route`, `enchantLevel`      | `int`                  | non-null | route identity                                                                                                                                                         |
| `enchantAdena`, `enchantExp`, `enchantSp` | `Long`                 | nullable | cost of one enchant step                                                                                                                                               |
| `enchantChanceByCharLevelPercent`         | `Map<Integer,Integer>` | nullable | keys are character levels as the engine defines them; the host converts its own indexing                                                                               |
| `node`                                    | `SkillLevel`           | non-null | full per-level payload of the enchanted variant (effects, stat modifiers, condition tree, icon, name, description, attribute, all scalars); `node.level` = `baseLevel` |

Everything per level moved to `node`: the former flat fields (consumes, ranges, magic level, abnormal level/time,
hit/cool/reuse, power, attribute) are the same-named fields of `node`, and the former `enchantIcon` /
`enchantName` / `enchantDescription` are `node.icon` / `node.name` / `node.description` (the host fills them from the
enchanted skill's own icon / name / description). The engines have no route presentation that is not per level, so
the route keeps none.

### 2.5 `SkillEffect` (package `gd.skill.effect`)

Complete field list in 0.101:

| field                 | type                      | null     | semantics                                                                                                                                                                                                  |
| --------------------- | ------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `type`                | `SkillEffectType`         | nullable | canonical effect                                                                                                                                                                                           |
| `typeRaw`             | `String`                  | non-null | engine effect token. For a derived effect = the source raw token                                                                                                                                           |
| `scope`               | `SkillEffectScope`        | nullable | when and to whom the effect applies; effects of an operate-type `P` skill get `PASSIVE` in every build                                                                                                     |
| `scopeRaw`            | `String`                  | nullable | engine effect-scope token; `null` when the engine has none (the scope is then derived)                                                                                                                     |
| `params`              | `Map<String,String>`      | nullable | canonical params, keys per type (§3.10); `null` when `type` is `null`                                                                                                                                      |
| `paramsRaw`           | `Map<String,String>`      | nullable | raw engine attribute map                                                                                                                                                                                   |
| `value`               | `Double`                  | nullable | the effect's main magnitude in the unit fixed per type (§3.10 "value" column); `null` when the type has no magnitude or the engine computes it at runtime                                                  |
| `valueRaw`            | `String`                  | nullable | engine expression when the magnitude is not a constant (then `value` is `null`)                                                                                                                            |
| `activateRatePercent` | `Integer`                 | nullable | per-effect base land rate; `null` when the skill-level `activateRatePercent` applies                                                                                                                       |
| `abnormalTypeRaw`     | `String`                  | nullable | engine abnormal type                                                                                                                                                                                       |
| `abnormalLevel`       | `Integer`                 | nullable |                                                                                                                                                                                                            |
| `abnormalTimeSec`     | `Integer`                 | nullable | the effect's own total duration when it differs from the level's; `null` = level value applies                                                                                                             |
| `tickIntervalMs`      | `Integer`                 | nullable | server-effective period of a periodic effect: the host's server-effective tick period; `null` = not periodic. Tick count = duration / interval                                                             |
| `conditionTree`       | `SkillConditionNode`      | nullable | attach condition of the whole effect. Per-modifier conditions go to `SkillStatModifier.conditionTree`                                                                                                      |
| `derived`             | `Boolean`                 | nullable | `true` when the host synthesized the effect with no engine effect object; `null` otherwise                                                                                                                 |
| `statModifiers`       | `List<SkillStatModifier>` | nullable | modifiers the effect applies while active (canonical `stat`/`operation`/`value` on each): the whole behaviour of `STAT_MODIFIER`, side modifiers of other types (a stun that also slows); `null` when none |

### 2.6 `SkillStatModifier` (package `gd.skill.effect`)

Complete field list in 0.101:

| field           | type                 | null     | semantics                                                                                                                                                                                    |
| --------------- | -------------------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `stat`          | `Stat`               | nullable | canonical stat (after the N7 inversion)                                                                                                                                                      |
| `statRaw`       | `String`             | non-null | engine stat enum name                                                                                                                                                                        |
| `operation`     | `StatOperation`      | nullable | canonical operation (after the N7 inversion)                                                                                                                                                 |
| `operationRaw`  | `String`             | non-null | engine operation token                                                                                                                                                                       |
| `value`         | `Double`             | nullable | constant operand                                                                                                                                                                             |
| `valueRaw`      | `String`             | nullable | engine expression when the operand is not a constant (`value` stays `null`)                                                                                                                  |
| `order`         | `Integer`            | nullable |                                                                                                                                                                                              |
| `conditionTree` | `SkillConditionNode` | nullable | the modifier applies only while the tree holds: engine func / handler filters (weapon type, armor type, two-handed or magic weapon, in combat, HP percent, position, movement, night, level) |

Skill stat modifiers are their own applicability context of `Stat`: a `MULTIPLY MP_CONSUME 0.9` modifier is a bonus,
not the skill's own MP cost (`SkillLevel.mpConsume`).

### 2.7 New `SkillConditionNode` (package `gd.skill.condition`)

| field             | type                       | null           | semantics                                                                    |
| ----------------- | -------------------------- | -------------- | ---------------------------------------------------------------------------- |
| `logic`           | `SkillConditionLogic`      | container only | `AND` / `OR`; `null` on leaves                                               |
| `type`            | `SkillConditionType`       | leaf, nullable | canonical predicate; `null` when unmapped                                    |
| `typeRaw`         | `String`                   | leaf, non-null | engine condition token; `null` on containers                                 |
| `negated`         | `boolean`                  | non-null       | the node's result is inverted (engine `not` and every engine boolean flag)   |
| `params`          | `Map<String,String>`       | nullable       | canonical params per `type` (§3.11)                                          |
| `paramsRaw`       | `Map<String,String>`       | nullable       | raw engine attributes of the leaf                                            |
| `systemMessageId` | `Integer`                  | nullable       | client system message shown when this node (leaf or subtree) fails (`msgId`) |
| `children`        | `List<SkillConditionNode>` | container only | ≥ 1 child                                                                    |

### 2.8 New `SkillLearn` (package `gd.skill.learn`)

| field                      | type                       | null     | semantics                                                                                                                                      |
| -------------------------- | -------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `treeKind`                 | `SkillTreeKind`            | nullable | canonical tree kind                                                                                                                            |
| `treeKindRaw`              | `String`                   | non-null | engine tree token                                                                                                                              |
| `treeName`                 | `String`                   | nullable | distinguishes several engine trees of one kind; `null` when the kind has one tree                                                              |
| `clazz`                    | `CharacterClass`           | nullable | `null` = tree not class-keyed (common / clan / hero / …)                                                                                       |
| `skillLevel`               | `int`                      | non-null | level taught by this entry                                                                                                                     |
| `requiredLevel`            | `Integer`                  | nullable | character level; `null` on `CLAN`/`CLAN_SUBUNIT` trees                                                                                         |
| `requiredClanLevel`        | `Integer`                  | nullable | clan level (clan trees only)                                                                                                                   |
| `learnSp`                  | `Long`                     | nullable | SP cost; `null` on clan trees                                                                                                                  |
| `learnClanReputation`      | `Long`                     | nullable | clan reputation cost (clan trees only)                                                                                                         |
| `autoLearn`                | `Boolean`                  | nullable | granted on reaching the level                                                                                                                  |
| `learnedByNpc`             | `Boolean`                  | nullable | learnable at a trainer NPC                                                                                                                     |
| `learnedByForgottenScroll` | `Boolean`                  | nullable | requires a Forgotten Scroll                                                                                                                    |
| `requiredItemSets`         | `List<SkillLearnItemSet>`  | nullable | any-of list of item sets; a set is all-of. a host with a single alternative emits one set, one with alternatives emits one set per alternative |
| `prerequisites`            | `List<SkillLevelRef>`      | nullable | skills that must be known (all-of)                                                                                                             |
| `races`                    | `List<CharacterRace>`      | nullable | restricts the entry to these races; `null` = any                                                                                               |
| `residenceIds`             | `List<Integer>`            | nullable | residence skills: residences that grant it                                                                                                     |
| `socialClass`              | `SocialClass`              | nullable | minimum clan social class (pledge class)                                                                                                       |
| `subClassRequirements`     | `List<SkillLearnSubClass>` | nullable | sub-class certification requirements (all-of)                                                                                                  |
| `removedSkillIds`          | `List<Integer>`            | nullable | skills removed when this entry is learned                                                                                                      |
| `requiredDualClassLevel`   | `Integer`                  | nullable | dual-class level required (`DUAL_CLASS` tree)                                                                                                  |
| `abilityTreeId`            | `Integer`                  | nullable | ability tree id (`ABILITY` tree)                                                                                                               |
| `abilityRow`               | `Integer`                  | nullable | row of the entry in the ability tree                                                                                                           |
| `abilityColumn`            | `Integer`                  | nullable | column of the entry in the ability tree                                                                                                        |
| `abilityPointsRequired`    | `Integer`                  | nullable | ability points spent in the tree before this entry opens                                                                                       |

Small DTOs in `gd.skill.learn`: `SkillLearnItemSet { items: List<SkillLearnItem> (non-null, ≥1) }`,
`SkillLevelRef { skillId: int, skillLevel: int }` (non-null both),
`SkillLearnSubClass { slot: int, level: int }` (sub-class slot index 1..3, required sub-class level);
`SkillLearnItem` moved here unchanged (`itemTemplateId`, `itemTemplateCount`).

The 0.100 class-learn list maps 1:1 onto `learns` entries with `treeKind = CLASS` and a non-null `clazz`.

### 2.9 `SkillProducedItem` (package `gd.skill.produced`)

| field        | type              | null     | semantics                                                                                                                                                                                   |
| ------------ | ----------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rewardKind` | `SkillRewardKind` | nullable | what the line gives, derived from the engine id. `itemTemplateId` keeps the engine id (the raw value, N1), which for a pseudo-currency is the negative id and then not a template reference |

`SkillProducedItemGroup` moved to the same package unchanged.

### 2.10 Other gd entities

`ItemTemplate`, `ItemSkillRef`, `ArmorSetSkill`: wire unchanged in 0.101.0; `ItemTemplate` and `NpcTemplate`
re-import `WeaponType` from `domain.item`. New enum `ItemGrade` and the `WeaponType.PISTOLS` constant are added now so
the item follow-up only adds fields.

### 2.11 Changes from 0.100

Removed outright; nothing is filled for a transition.

| removed                                                                                                                                                                                                                                                                | successor                                                                                                                |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| class `SkillCondition` + `Skill.conditions`                                                                                                                                                                                                                            | `SkillLevel.conditionTree` (`SkillConditionNode`)                                                                        |
| class `SkillClassLearn` + `Skill.classes`                                                                                                                                                                                                                              | `Skill.learns` (`SkillLearn`); class tree = `treeKind CLASS` with non-null `clazz`; `requiredItems` → `requiredItemSets` |
| `Skill.triggeredSkillId`, `triggeredSkillLevel`, `triggeredChanceType`, `triggeredChancePercent`                                                                                                                                                                       | trigger effects `TRIGGER_SKILL_*` (`params.skillId` / `skillLevel` / `chancePercent`, §3.10)                             |
| `Skill.minPledgeClass`                                                                                                                                                                                                                                                 | `SkillLearn.socialClass`; condition `CASTER_SOCIAL_CLASS{min}`                                                           |
| `Skill.skillType`, `Skill.abnormalType`, `Skill.abnormalVisualEffects`                                                                                                                                                                                                 | `skillTypeRaw`, `abnormalTypeRaw`, `abnormalVisualEffectsRaw` (same content)                                             |
| `SkillFlags.magic`, `SkillFlags.staticSkill`                                                                                                                                                                                                                           | `Skill.magicType`                                                                                                        |
| `SkillFlags.passive`, `SkillFlags.toggle`                                                                                                                                                                                                                              | `Skill.operateType` (fold §3.4)                                                                                          |
| `SkillFlags.heroSkill`, `SkillFlags.clanSkill`                                                                                                                                                                                                                         | `Skill.learns[].treeKind` (`HERO`; `CLAN` / `CLAN_SUBUNIT`)                                                              |
| `SkillLevel.negateAbnormalTypes`                                                                                                                                                                                                                                       | `negateAbnormalTypesRaw` (same content)                                                                                  |
| `SkillEffect.name`                                                                                                                                                                                                                                                     | `typeRaw` (same content); canonical identity in `type`                                                                   |
| `SkillEffect.kind`                                                                                                                                                                                                                                                     | `scope` + `scopeRaw`                                                                                                     |
| `SkillEffect.effectPower`                                                                                                                                                                                                                                              | `activateRatePercent`                                                                                                    |
| `SkillEffect.abnormalType`                                                                                                                                                                                                                                             | `abnormalTypeRaw` (same content)                                                                                         |
| `SkillStatModifier.op`                                                                                                                                                                                                                                                 | `operationRaw` (same content) + canonical `operation`                                                                    |
| `SkillEnchantRoute.mpConsume`, `mpInitialConsume`, `hpConsume`, `itemTemplateId`, `itemTemplateCount`, `castRange`, `effectRange`, `magicLevel`, `abnormalLevel`, `abnormalTimeSec`, `hitTimeMs`, `coolTimeMs`, `reuseDelayMs`, `power`, `attribute`, `attributePower` | same-named fields of `SkillEnchantRoute.node`                                                                            |
| `SkillEnchantRoute.enchantIcon`, `enchantName`, `enchantDescription`                                                                                                                                                                                                   | `node.icon`, `node.name`, `node.description`                                                                             |

Same name, new type or meaning (a consumer that maps them by name must change the mapping):

| field                    | 0.100                                  | 0.101                                       |
| ------------------------ | -------------------------------------- | ------------------------------------------- |
| `Skill.operateType`      | raw token `String`                     | `SkillOperateType`; raw in `operateTypeRaw` |
| `Skill.targetType`       | mapped `SkillTargetType` name `String` | `SkillTargetType`; raw in `targetTypeRaw`   |
| `Skill.trait`            | raw token `String`                     | `SkillTrait`; raw in `traitRaw`             |
| `Skill.saveVs`           | raw token `String`                     | `Stat`; raw in `saveVsRaw`                  |
| `SkillLevel.attribute`   | raw token `String`                     | `Attribute`; raw in `attributeRaw`          |
| `SkillEffect.params`     | raw engine map                         | canonical map; raw in `paramsRaw`           |
| `SkillStatModifier.stat` | raw engine stat, non-null              | `Stat`, nullable; raw in `statRaw`          |

## 3. Enums

Packages are under `app.l2nx.gs.adapter.api.domain`. Constants are listed in declaration order (new constants of an
existing enum are appended after the frozen ones).

### 3.1 `Stat`

Package `domain.stat`. 89 pre-existing constants are frozen (see the enum); this release appends the constants below.
The Javadoc of `Stat` lists the applicability contexts (item stats, NPC stats, skill characteristics, skill stat
modifiers) and rule N7. "orientation": **as is** = the engine value maps unchanged; **invert** = vulnerability-oriented
engine token, the host applies N7.

#### Base stats

| constant | orientation | meaning                      |
| -------- | ----------- | ---------------------------- |
| CHA      | as is       | charisma (GoD-era base stat) |
| LUC      | as is       | luck (GoD-era base stat)     |

#### Vitals and recovery

| constant               | orientation | meaning                                       |
| ---------------------- | ----------- | --------------------------------------------- |
| MAX_CP                 | as is       | maximum CP                                    |
| MAX_RECOVERABLE_HP     | as is       | cap of HP restorable by heals                 |
| MAX_RECOVERABLE_MP     | as is       | cap of MP restorable by heals                 |
| MAX_RECOVERABLE_CP     | as is       | cap of CP restorable by heals                 |
| CP_REGEN               | as is       | CP regeneration                               |
| HP_HEAL_RECEIVED       | as is       | effectiveness of HP heals received (relative) |
| HP_HEAL_RECEIVED_ADD   | as is       | flat bonus to HP heals received               |
| HP_HEAL_POWER          | as is       | outgoing heal power of the healer             |
| MP_HEAL_RECEIVED       | as is       | effectiveness of MP recovery received         |
| HP_POTION_RECOVERY     | as is       | relative bonus to HP restored by potions      |
| HP_POTION_RECOVERY_ADD | as is       | flat bonus to HP restored by potions          |
| MP_POTION_RECOVERY     | as is       | relative bonus to MP restored by potions      |
| MP_POTION_RECOVERY_ADD | as is       | flat bonus to MP restored by potions          |
| CP_POTION_RECOVERY     | as is       | relative bonus to CP restored by potions      |
| CP_POTION_RECOVERY_ADD | as is       | flat bonus to CP restored by potions          |
| DAMAGE_TAKEN_LIMIT     | as is       | cap of damage taken per hit                   |

#### Attack, defence, speed, skill power

| constant                | orientation | meaning                                               |
| ----------------------- | ----------- | ----------------------------------------------------- |
| P_SKILL_POWER           | as is       | power multiplier of physical skills                   |
| M_SKILL_POWER           | as is       | power multiplier of magic skills                      |
| SKILL_POWER_ADD         | as is       | flat skill power added                                |
| CHARGE_SKILL_POWER      | as is       | power bonus of charge (momentum) skills               |
| WEAPON_P_ATK_BONUS      | as is       | flat bonus to the weapon's P. Atk.                    |
| WEAPON_P_ATK_BONUS_RATE | as is       | multiplier of the weapon's P. Atk.                    |
| WEAPON_M_ATK_BONUS      | as is       | flat bonus to the weapon's M. Atk.                    |
| WEAPON_M_ATK_BONUS_RATE | as is       | multiplier of the weapon's M. Atk.                    |
| M_ATK_BY_P_ATK          | as is       | share of P. Atk. added to M. Atk.                     |
| ATK_REUSE               | as is       | reuse of bow/crossbow normal attacks                  |
| P_SKILL_REUSE           | as is       | reuse delay of physical skills                        |
| M_SKILL_REUSE           | as is       | reuse delay of magic skills                           |
| STATIC_SKILL_REUSE      | as is       | reuse delay of static skills                          |
| SONG_DANCE_REUSE        | as is       | reuse delay of songs/dances                           |
| STATIC_ATK_SPD          | as is       | fixed attack speed override                           |
| STATIC_CAST_SPD         | as is       | fixed casting speed override                          |
| M_ACCURACY              | as is       | magic accuracy                                        |
| M_EVASION               | as is       | magic evasion                                         |
| P_SKILL_EVASION         | as is       | chance to evade physical skills                       |
| M_SKILL_EVASION         | as is       | chance to evade magic skills                          |
| STATIC_SKILL_EVASION    | as is       | chance to evade static skills                         |
| ATK_MAX_TARGETS         | as is       | max targets of a normal attack (polearm)              |
| SHIELD_DEF_ANGLE        | as is       | shield block angle                                    |
| CAST_INTERRUPT_CHANCE   | as is       | chance that damage taken interrupts casting           |
| P_DEF_IGNORE            | as is       | relative share of the target's P. Def. ignored        |
| P_DEF_IGNORE_ADD        | as is       | flat P. Def. ignored                                  |
| SHIELD_DEF_IGNORE       | as is       | relative share of the target's shield defence ignored |
| SHIELD_DEF_IGNORE_ADD   | as is       | flat shield defence ignored                           |
| ACCESSORY_M_DEF         | as is       | M. Def. bonus of jewellery                            |
| ARMOR_P_DEF             | as is       | P. Def. bonus of armor pieces                         |
| REAR_DAMAGE             | as is       | damage multiplier when attacking from behind          |
| IGNORE_DAMAGE_REDUCTION | as is       | ignores the target's flat damage reduction            |
| AOE_DAMAGE              | as is       | damage multiplier of AoE skills dealt                 |
| AOE_DAMAGE_RES          | invert      | resistance to AoE damage (relative)                   |
| AOE_DAMAGE_ADD_RES      | invert      | resistance to AoE damage (flat)                       |
| IMMOBILE_DAMAGE         | as is       | damage bonus against immobilized targets              |
| IMMOBILE_DAMAGE_RES     | as is       | damage reduction while immobilized                    |
| REAL_DAMAGE_RES         | as is       | resistance to fixed (real) damage                     |
| M_DAMAGE_RES            | invert      | resistance to magic damage taken                      |
| COMBAT_POWER            | as is       | combat power rating                                   |

#### Critical, blow, lethal

| constant                    | orientation | meaning                                              |
| --------------------------- | ----------- | ---------------------------------------------------- |
| CRIT_DAMAGE                 | as is       | physical critical damage (relative)                  |
| CRIT_DAMAGE_ADD             | as is       | physical critical damage (flat)                      |
| M_CRIT_DAMAGE               | as is       | magic critical damage (relative)                     |
| M_CRIT_DAMAGE_ADD           | as is       | magic critical damage (flat)                         |
| P_SKILL_CRIT_RATE           | as is       | critical rate of physical skills                     |
| P_SKILL_CRIT_DAMAGE         | as is       | critical damage of physical skills (relative)        |
| P_SKILL_CRIT_DAMAGE_ADD     | as is       | critical damage of physical skills (flat)            |
| CRIT_RATE_CAP_ADD           | as is       | raises the physical critical rate cap                |
| M_CRIT_RATE_CAP_ADD         | as is       | raises the magic critical rate cap                   |
| M_CRIT_RATE_BY_CRIT_RATE    | as is       | share of physical crit rate added to magic crit rate |
| CRIT_RATE_RES               | invert      | resistance to incoming physical crit rate (relative) |
| CRIT_RATE_ADD_RES           | invert      | same, flat                                           |
| CRIT_DAMAGE_RES             | invert      | resistance to incoming physical crit damage          |
| CRIT_DAMAGE_ADD_RES         | invert      | same, flat                                           |
| M_CRIT_RATE_RES             | invert      | resistance to incoming magic crit rate (relative)    |
| M_CRIT_RATE_ADD_RES         | invert      | same, flat                                           |
| M_CRIT_DAMAGE_RES           | invert      | resistance to incoming magic crit damage             |
| M_CRIT_DAMAGE_ADD_RES       | invert      | same, flat                                           |
| P_SKILL_CRIT_RATE_RES       | invert      | resistance to incoming physical-skill crit rate      |
| P_SKILL_CRIT_RATE_ADD_RES   | invert      | same, flat                                           |
| P_SKILL_CRIT_DAMAGE_RES     | invert      | resistance to incoming physical-skill crit damage    |
| P_SKILL_CRIT_DAMAGE_ADD_RES | invert      | same, flat                                           |
| BLOW_CHANCE_RES             | invert      | resistance to incoming blows                         |
| LETHAL_STRIKE_RES           | as is       | resistance to lethal strike                          |

#### PvP / PvE (`_DEF` and `_DAMAGE_TAKEN` = damage-taken multipliers; as is)

| constant                 | meaning                                     |
| ------------------------ | ------------------------------------------- |
| PVP_P_DAMAGE             | PvP normal-attack damage                    |
| PVP_P_SKILL_DAMAGE       | PvP physical-skill damage                   |
| PVP_M_DAMAGE             | PvP magic damage                            |
| PVP_P_DEF                | PvP defence vs normal attacks               |
| PVP_P_SKILL_DEF          | PvP defence vs physical skills              |
| PVP_M_DEF                | PvP defence vs magic                        |
| PVE_P_DAMAGE             | PvE normal-attack damage                    |
| PVE_P_SKILL_DAMAGE       | PvE physical-skill damage                   |
| PVE_M_DAMAGE             | PvE magic damage                            |
| PVE_BOW_DAMAGE           | PvE bow normal-attack damage                |
| PVE_BOW_SKILL_DAMAGE     | PvE bow skill damage                        |
| PVE_P_DEF                | PvE defence vs normal attacks               |
| PVE_P_SKILL_DEF          | PvE defence vs physical skills              |
| PVE_M_DEF                | PvE defence vs magic                        |
| PVE_BOW_DEF              | PvE defence vs bow normal attacks           |
| PVE_BOW_SKILL_DEF        | PvE defence vs bow skills                   |
| PVE_RAID_P_DAMAGE        | damage vs raid bosses, normal attacks       |
| PVE_RAID_P_SKILL_DAMAGE  | damage vs raid bosses, physical skills      |
| PVE_RAID_M_DAMAGE        | damage vs raid bosses, magic                |
| PVE_RAID_P_DEF           | defence vs raid bosses, normal attacks      |
| PVE_RAID_P_SKILL_DEF     | defence vs raid bosses, physical skills     |
| PVE_RAID_M_DEF           | defence vs raid bosses, magic               |
| PVP_DAMAGE_TAKEN         | multiplier of all damage taken from players |
| PVE_DAMAGE_TAKEN         | multiplier of all damage taken from NPCs    |
| PVE_MONSTER_DAMAGE_TAKEN | same, from monsters                         |
| PVE_RAID_DAMAGE_TAKEN    | same, from raid bosses                      |

#### Movement

| constant     | meaning                       |
| ------------ | ----------------------------- |
| SPEED_LIMIT  | cap of movement speed         |
| STATIC_SPEED | fixed movement speed override |

#### Immunities, abnormal resistance

| constant        | orientation | meaning                                              |
| --------------- | ----------- | ---------------------------------------------------- |
| DEBUFF_IMMUNITY | as is       | immune to debuffs                                    |
| BUFF_IMMUNITY   | as is       | immune to buffs                                      |
| P_ABNORMAL_RES  | as is       | resistance to abnormals of basic property `PHYSICAL` |
| M_ABNORMAL_RES  | as is       | resistance to abnormals of basic property `MAGIC`    |

#### Elemental spirits

| constant                     | meaning                |
| ---------------------------- | ---------------------- |
| ELEMENTAL_SPIRIT_FIRE_POWER  | fire spirit attack     |
| ELEMENTAL_SPIRIT_WATER_POWER | water spirit attack    |
| ELEMENTAL_SPIRIT_WIND_POWER  | wind spirit attack     |
| ELEMENTAL_SPIRIT_EARTH_POWER | earth spirit attack    |
| ELEMENTAL_SPIRIT_FIRE_RES    | fire spirit defence    |
| ELEMENTAL_SPIRIT_WATER_RES   | water spirit defence   |
| ELEMENTAL_SPIRIT_WIND_RES    | wind spirit defence    |
| ELEMENTAL_SPIRIT_EARTH_RES   | earth spirit defence   |
| ELEMENTAL_SPIRIT_CRIT_RATE   | spirit critical rate   |
| ELEMENTAL_SPIRIT_CRIT_DAMAGE | spirit critical damage |
| ELEMENTAL_SPIRIT_EXP_BONUS   | spirit EXP gain bonus  |

#### Trait axes (`<SkillTrait>_POWER` attacker / `<SkillTrait>_RES` defender)

Attacker side `<SkillTrait>_POWER` and defender side `<SkillTrait>_RES`; every `_RES` follows N7 (a
vulnerability-oriented engine token is inverted by the host, a defence-oriented one maps as is).

| constant              | meaning                                 |
| --------------------- | --------------------------------------- |
| BLEED_POWER           | power to land bleed                     |
| BLEED_RES             | resistance to bleed                     |
| POISON_POWER          | power to land poison                    |
| POISON_RES            | resistance to poison                    |
| SHOCK_POWER           | power to land stun                      |
| SHOCK_RES             | resistance to stun                      |
| PARALYZE_POWER        | power to land paralysis                 |
| PARALYZE_RES          | resistance to paralysis                 |
| HOLD_POWER            | power to land root/hold                 |
| HOLD_RES              | resistance to root/hold                 |
| SLEEP_POWER           | power to land sleep                     |
| SLEEP_RES             | resistance to sleep                     |
| DERANGEMENT_POWER     | power to land mental effects            |
| DERANGEMENT_RES       | resistance to mental effects            |
| VALAKAS_POWER         | Valakas-trait attack                    |
| VALAKAS_RES           | Valakas-trait resistance                |
| DISARM_WEAPON_POWER   | power to land disarm                    |
| DISARM_WEAPON_RES     | resistance to disarm                    |
| PHYSICAL_BLOCKADE_RES | resistance to physical-blockade effects |
| BOSS_RES              | resistance to boss-trait skills         |
| GUST_RES              | resistance to gust                      |
| DEATH_RES             | resistance to death-trait skills        |
| TURN_STONE_POWER      | power to land petrification             |
| TURN_STONE_RES        | resistance to petrification             |
| ROOT_PHYSICALLY_RES   | resistance to physical root             |
| KNOCK_BACK_POWER      | power to land knock-back                |
| KNOCK_BACK_RES        | resistance to knock-back                |
| KNOCK_DOWN_POWER      | power to land knock-down                |
| KNOCK_DOWN_RES        | resistance to knock-down                |
| PULL_POWER            | power to land pull                      |
| PULL_RES              | resistance to pull                      |
| AIR_BIND_RES          | resistance to air bind                  |
| FEAR_POWER            | power to land fear                      |
| FEAR_RES              | resistance to fear                      |
| SILENCE_POWER         | power to land silence                   |
| SILENCE_RES           | resistance to silence                   |
| SUPPRESSION_POWER     | power to land suppression               |
| SUPPRESSION_RES       | resistance to suppression               |
| INFECTION_POWER       | power to land infection                 |
| INFECTION_RES         | resistance to infection                 |
| ANOMALY_POWER         | power to land anomaly                   |
| ANOMALY_RES           | resistance to anomaly                   |
| IMPRISON_POWER        | power to land imprison                  |
| IMPRISON_RES          | resistance to imprison                  |
| DEPORT_RES            | resistance to deport                    |
| CHANGE_BODY_RES       | resistance to body change               |
| ZONE_RES              | resistance to zone-trait skills         |
| PSYCHIC_RES           | resistance to psychic-trait skills      |

#### Effect-category axes

| constant        | orientation | meaning                                  |
| --------------- | ----------- | ---------------------------------------- |
| DISPEL_POWER    | as is       | power of dispel skills                   |
| DISPEL_RES      | invert      | resistance to having buffs dispelled     |
| DEBUFF_POWER    | as is       | power to land debuffs in general         |
| DEBUFF_RES      | invert      | resistance to debuffs in general         |
| BUFF_RES        | invert      | resistance to being buffed               |
| MOVEMENT_POWER  | as is       | power of movement-impairing effects      |
| MOVEMENT_RES    | invert      | resistance to movement-impairing effects |
| DAMAGE_ZONE_RES | invert      | resistance to damage zones               |

#### Weapon-type axes (`_RES` follows N7; `_POWER` as is)

| constant            | meaning                          |
| ------------------- | -------------------------------- |
| SWORD_RES           | resistance to swords             |
| BIG_SWORD_RES       | resistance to two-handed swords  |
| BLUNT_RES           | resistance to blunts             |
| BIG_BLUNT_RES       | resistance to two-handed blunts  |
| DUAL_BLUNT_RES      | resistance to dual blunts        |
| DAGGER_RES          | resistance to daggers            |
| DUAL_DAGGER_RES     | resistance to dual daggers       |
| BOW_RES             | resistance to bows               |
| CROSSBOW_RES        | resistance to crossbows          |
| POLE_RES            | resistance to polearms           |
| FIST_RES            | resistance to fists              |
| DUAL_FIST_RES       | resistance to dual fists         |
| DUAL_SWORD_RES      | resistance to dual swords        |
| RAPIER_RES          | resistance to rapiers            |
| ANCIENT_SWORD_RES   | resistance to ancient swords     |
| PISTOLS_RES         | resistance to pistols            |
| ETC_RES             | resistance to other weapons      |
| PET_RES             | resistance to pet attacks        |
| SWORD_POWER         | attack bonus with swords         |
| BLUNT_POWER         | attack bonus with blunts         |
| DAGGER_POWER        | attack bonus with daggers        |
| DUAL_DAGGER_POWER   | attack bonus with dual daggers   |
| BOW_POWER           | attack bonus with bows           |
| POLE_POWER          | attack bonus with polearms       |
| FIST_POWER          | attack bonus with fists          |
| DUAL_FIST_POWER     | attack bonus with dual fists     |
| DUAL_SWORD_POWER    | attack bonus with dual swords    |
| RAPIER_POWER        | attack bonus with rapiers        |
| ANCIENT_SWORD_POWER | attack bonus with ancient swords |
| PISTOLS_POWER       | attack bonus with pistols        |

#### Race axes (`<NpcRace>_POWER` only)

Only the attacker side exists. There is no `<RACE>_RES`: defence against attackers of a race and "the holder is of
that race" are different engine concepts, so both travel raw.

| constant             | meaning                         |
| -------------------- | ------------------------------- |
| PLANT_POWER          | bonus damage vs plants          |
| BUG_POWER            | bonus damage vs insects         |
| ANIMAL_POWER         | bonus damage vs animals         |
| BEAST_POWER          | bonus damage vs beasts          |
| DRAGON_POWER         | bonus damage vs dragons         |
| GIANT_POWER          | bonus damage vs giants          |
| MAGIC_CREATURE_POWER | bonus damage vs magic creatures |
| UNDEAD_POWER         | bonus damage vs undead          |
| DEMON_POWER          | bonus damage vs demons          |

#### Reflect, absorb, transfer (all as is)

| constant                  | meaning                                         |
| ------------------------- | ----------------------------------------------- |
| REFLECT_DAMAGE            | share of melee damage reflected to the attacker |
| REFLECT_DAMAGE_RES        | reduces damage reflected back at the actor      |
| REFLECT_P_SKILL_CHANCE    | chance to reflect a physical skill              |
| REFLECT_M_SKILL_CHANCE    | chance to reflect a magic skill                 |
| VENGEANCE_P_SKILL_CHANCE  | chance to return full physical-skill damage     |
| VENGEANCE_M_SKILL_CHANCE  | chance to return full magic-skill damage        |
| HP_ABSORB                 | share of dealt damage returned as HP            |
| HP_ABSORB_RES             | reduces HP absorbed from the actor              |
| MP_ABSORB                 | share of dealt damage returned as MP            |
| MANA_SHIELD               | share of damage taken from MP instead of HP     |
| DAMAGE_TRANSFER_TO_SUMMON | share of damage passed to the own summon        |
| DAMAGE_TRANSFER_TO_PLAYER | share of damage passed to a linked player       |

#### Skill-use economy and resources (all as is)

| constant                | meaning                                   |
| ----------------------- | ----------------------------------------- |
| P_SKILL_MP_CONSUME      | MP cost of physical skills                |
| M_SKILL_MP_CONSUME      | MP cost of magic skills                   |
| STATIC_SKILL_MP_CONSUME | MP cost of static skills                  |
| SONG_DANCE_MP_CONSUME   | MP cost of songs/dances                   |
| BOW_MP_CONSUME          | MP spent per bow shot                     |
| SKILL_MASTERY           | Skill Mastery proc stat                   |
| SKILL_MASTERY_RATE      | Skill Mastery chance multiplier           |
| MAX_SOULS               | soul charge cap                           |
| MAX_CHARGES             | charge (momentum) cap                     |
| MAX_CUBICS              | simultaneous cubics                       |
| SOULSHOT_DAMAGE         | soulshot damage bonus                     |
| SPIRITSHOT_DAMAGE       | spiritshot damage bonus                   |
| SOULSHOT_DAMAGE_RES     | resistance to the soulshot damage bonus   |
| SPIRITSHOT_DAMAGE_RES   | resistance to the spiritshot damage bonus |

#### Character economy, environment, slots (all as is)

| constant                     | meaning                             |
| ---------------------------- | ----------------------------------- |
| EXP_SP_RATE                  | multiplier of EXP and SP from kills |
| EXP_BONUS                    | EXP gain bonus                      |
| SP_BONUS                     | SP gain bonus                       |
| PET_EXP_BONUS                | pet EXP gain bonus                  |
| DROP_CHANCE                  | item drop chance multiplier         |
| DROP_AMOUNT                  | dropped item amount multiplier      |
| ADENA_DROP_AMOUNT            | adena amount multiplier             |
| SPOIL_CHANCE                 | spoil chance multiplier             |
| RAID_DROP_CHANCE             | raid boss drop chance multiplier    |
| LCOIN_DROP_CHANCE            | L-Coin drop chance multiplier       |
| FAME_RATE                    | fame gain multiplier                |
| VITALITY_CONSUME_RATE        | vitality drain speed                |
| VITALITY_EXP_RATE            | EXP bonus from vitality             |
| MAGIC_LAMP_EXP_RATE          | magic lamp gauge fill multiplier    |
| FISHING_EXP_SP_BONUS         | EXP/SP bonus from fishing           |
| FISHING_EXPERTISE            | fishing expertise                   |
| ENCHANT_CHANCE               | item enchant success bonus          |
| CRAFT_CHANCE                 | crafting success bonus              |
| CRAFT_CRIT_CHANCE            | crafting critical chance            |
| RESURRECTION_FEE_RATE        | resurrection fee multiplier         |
| EXPERTISE_GRADE              | expertise grade override            |
| PVP_EXP_LOSS_REDUCTION       | less EXP lost on PvP death          |
| PVE_EXP_LOSS_REDUCTION       | less EXP lost on death to a monster |
| RAID_EXP_LOSS_REDUCTION      | less EXP lost on death to a raid    |
| PVP_DEATH_PENALTY_REDUCTION  | lower death-penalty chance, PvP     |
| PVE_DEATH_PENALTY_REDUCTION  | same, monster                       |
| RAID_DEATH_PENALTY_REDUCTION | same, raid                          |
| WEIGHT_LIMIT                 | carry weight capacity               |
| WEIGHT_PENALTY               | overweight penalty modifier         |
| BREATH                       | underwater breath gauge             |
| FALL_DAMAGE                  | fall damage modifier                |
| PET_FEED_BONUS               | pet food efficiency                 |
| WAREHOUSE_LIMIT              | private warehouse slots             |
| FREIGHT_LIMIT                | freight slots                       |
| PRIVATE_SELL_LIMIT           | private store (sell) slots          |
| PRIVATE_BUY_LIMIT            | private store (buy) slots           |
| DWARVEN_RECIPE_LIMIT         | dwarven recipe book slots           |
| COMMON_RECIPE_LIMIT          | common recipe book slots            |
| TALISMAN_SLOTS               | talisman slots unlocked             |
| CLOAK_SLOT                   | cloak slot unlocked                 |
| BROOCH_JEWEL_SLOTS           | brooch jewel slots                  |
| AGATHION_SLOTS               | agathion slots                      |
| HENNA_SLOTS                  | dye slots                           |
| WORLD_CHAT_LIMIT             | world chat uses per day             |
| ELIXIR_LIMIT                 | elixir use limit                    |

#### Stat modifier notes

- One engine handler may write several stats (N3), e.g. a shot bonus writes `SOULSHOT_DAMAGE` and
  `SPIRITSHOT_DAMAGE`; a "reduce drop penalty" handler by type writes the matching `*_EXP_LOSS_REDUCTION` /
  `*_DEATH_PENALTY_REDUCTION` stat (or all three PvP/PvE/raid stats for the "any" type).
- Per-magic-type handlers (MP cost, reuse, evasion) map to the `P_SKILL_` / `M_SKILL_` / `STATIC_SKILL_` /
  `SONG_DANCE_` variant; other magic types give `stat = null`.
- UI breakdown buckets of one value (active / passive experience bonus, magic-lamp bonus split) are not emitted;
  only the real stat is.
- Damage-taken-by-attacker-kind handlers map to `PVP_DAMAGE_TAKEN`, `PVE_DAMAGE_TAKEN`,
  `PVE_MONSTER_DAMAGE_TAKEN`, `PVE_RAID_DAMAGE_TAKEN`; slot-enlarge handlers by type map to `INV_LIMIT`,
  `WAREHOUSE_LIMIT`, `PRIVATE_SELL_LIMIT`, `PRIVATE_BUY_LIMIT`, `DWARVEN_RECIPE_LIMIT`, `COMMON_RECIPE_LIMIT`.
- Percent resist-abnormal / resist-dispel handlers by category are emitted as `PERCENT -amount` on `DEBUFF_RES` /
  `DISPEL_RES` (N7 exact inversion).
- Positional critical bonuses are `CRIT_DAMAGE` / `CRIT_RATE` with a modifier `conditionTree`
  `CASTER_POSITION_TO_TARGET{relativePositions}`.
- Two-handed weapon bonuses are one `STAT_MODIFIER` effect with N modifiers, each
  `conditionTree = AND(CASTER_HAS_EQUIPPED_TYPE{weaponTypes}, CASTER_HAS_TWO_HANDED_WEAPON)`.
- Level / base-stat / MP / movement-type / night bonuses are `STAT_MODIFIER` with a modifier `conditionTree` of
  `CASTER_LEVEL{min}` / `CASTER_BASE_STAT{stat,min,max}` / `CASTER_MAX_MP{min}` /
  `CASTER_MOVEMENT_STATE{movementStates}` / `WORLD_IS_NIGHT`.
- Element defence / attack handlers map to the existing `<ELEMENT>_RES` / `<ELEMENT>_POWER`; elemental-spirit
  handlers map to `ELEMENTAL_SPIRIT_<TYPE>_POWER` / `_RES`.

### 3.2 `SkillTrait`

Package `domain.skill.kind` (moved). 15 pre-existing constants unchanged, 17 appended.

| constant          | meaning                         |
| ----------------- | ------------------------------- |
| NONE              | no trait                        |
| BLEED             | bleed                           |
| BOSS              | boss-trait skills               |
| DEATH             | death-trait skills              |
| DERANGEMENT       | mental effects                  |
| ETC               | other                           |
| GUST              | gust                            |
| HOLD              | root / hold                     |
| PARALYZE          | paralysis                       |
| PHYSICAL_BLOCKADE | physical blockade               |
| POISON            | poison                          |
| SHOCK             | stun                            |
| SLEEP             | sleep                           |
| VALAKAS           | Valakas-trait skills            |
| DISARM_WEAPON     | disarm                          |
| TURN_STONE        | petrification (appended)        |
| ROOT_PHYSICALLY   | physical root (appended)        |
| ROOT_MAGICALLY    | magic root (appended)           |
| KNOCK_BACK        | knock-back (appended)           |
| KNOCK_DOWN        | knock-down (appended)           |
| PULL              | pull (appended)                 |
| AIR_BIND          | air bind (appended)             |
| FEAR              | fear (appended)                 |
| SILENCE           | silence (appended)              |
| SUPPRESSION       | suppression (appended)          |
| INFECTION         | infection (appended)            |
| ANOMALY           | anomaly (appended)              |
| IMPRISON          | imprison (appended)             |
| DEPORT            | banish (appended)               |
| CHANGE_BODY       | polymorph (appended)            |
| ZONE              | zone-trait skills (appended)    |
| PSYCHIC           | psychic-trait skills (appended) |

Weapon-type and race traits (`SWORD`, `PLANT_WEAKNESS`, ...) are never a skill trait; they map only to Stat axes (§3.1).
Trait tokens without data in any known datapack stay raw (`traitRaw`).

### 3.3 `SkillTargetType`

Package `domain.skill.target` (moved). 42 pre-existing constants unchanged, 7 appended.

Canonical target type. Single-axis tokens embed the AoE shape (`AOE_FRONT`); selection-only tokens (`ENEMY`) describe
just the initial selection. The affected set is always read from `SkillAffectScope` + `SkillAffectObject`.

| constant           | meaning                                                        |
| ------------------ | -------------------------------------------------------------- |
| ONE                | the selected creature                                          |
| SELF               | the caster                                                     |
| NONE               | no target                                                      |
| ENEMY_ONLY         | a hostile creature only                                        |
| AOE                | hostile creatures in a circle around the selected target       |
| AOE_FRONT          | hostile creatures in a cone in front of the caster             |
| AOE_BEHIND         | hostile creatures in a cone behind the caster                  |
| AOE_FRIENDLY       | non-hostile creatures in a circle around the selected target   |
| AOE_SUMMON         | hostile creatures in a circle around the caster's summon       |
| AOE_CORPSE_MOB     | NPC corpses in a circle around the selected target             |
| AOE_MOB            | monsters in a circle around the selected target                |
| AURA               | hostile creatures in a circle around the caster                |
| AURA_FRONT         | hostile creatures in a cone around the caster, facing forward  |
| AURA_BEHIND        | hostile creatures in a cone around the caster, facing backward |
| AURA_FRIENDLY      | non-hostile creatures in a circle around the caster            |
| AURA_CORPSE_MOB    | NPC corpses in a circle around the caster                      |
| AURA_UNDEAD_ENEMY  | hostile undead in a circle around the caster                   |
| PARTY              | the caster's party members in range                            |
| PARTY_MEMBER       | one party member                                               |
| PARTY_OTHER        | one party member other than the caster                         |
| AOE_PARTY_NOT_ME   | party members in range, caster excluded                        |
| PARTY_CLAN         | party and clan members in range                                |
| CLAN               | clan members in range                                          |
| CLAN_MEMBER        | one clan member                                                |
| ALLY               | alliance members in range                                      |
| COMMAND_CHANNEL    | command-channel members in range                               |
| SUMMON             | the caster's summon (servitor or pet)                          |
| SERVITOR           | the caster's servitor                                          |
| PET                | the caster's pet                                               |
| PET_OWNER          | the owner of the caster's pet                                  |
| SUMMON_ENEMY       | a hostile summon                                               |
| CORPSE             | a dead creature                                                |
| CORPSE_PLAYER      | a dead player                                                  |
| CORPSE_MOB         | a dead monster                                                 |
| CORPSE_PET         | a dead pet                                                     |
| CORPSE_PARTY       | dead party members                                             |
| CORPSE_CLAN        | dead clan members                                              |
| CORPSE_ALLY        | dead alliance members                                          |
| SIEGE_HOLY         | the holy artifact of a castle siege                            |
| SIEGE_FLAGPOLE     | the flagpole of a fortress siege                               |
| UNLOCKABLE         | a door or chest that can be unlocked                           |
| GROUND             | a ground point                                                 |
| ENEMY              | (appended) an auto-attackable creature                         |
| NOT_ENEMY          | (appended) a non-hostile creature or self                      |
| TARGET_OR_SELF     | (appended) the selected creature, else self                    |
| NOT_SELF           | (appended) any creature except self                            |
| ITEM               | (appended) an item on the ground or in the inventory           |
| SIEGE_ARTILLERY    | (appended) an enemy siege door / artillery object              |
| SIEGE_ADVANCE_BASE | (appended) the advanced siege base NPC                         |

Selection tokens that are stubs, have no data in any known datapack, or denote engine-internal tracking objects stay
raw (`targetTypeRaw`, `targetType = null`).

### 3.4 `SkillOperateType`

Package `domain.skill.kind`. 18 constants. Client `operate_type` codes, identical across hosts; the deliberate
exception to "named by meaning" (the code is the cross-host identity). The fold maps to the frozen `SkillOperation`
(`ACTIVE`, `PASSIVE`, `TOGGLE`) described in its Javadoc.

| constant | meaning                                        | fold    |
| -------- | ---------------------------------------------- | ------- |
| A1       | active, instant effect                         | ACTIVE  |
| A2       | active, continuous (+ instant) effect          | ACTIVE  |
| A3       | active, instant on target + continuous on self | ACTIVE  |
| A4       | active, instant + continuous (event herbs)     | ACTIVE  |
| A5       | active aura                                    | ACTIVE  |
| A6       | active synergy                                 | ACTIVE  |
| CA1      | channeling, instant effect per tick            | ACTIVE  |
| CA2      | channeling, variant (client meaning unknown)   | ACTIVE  |
| CA5      | channeling, continuous effect per tick         | ACTIVE  |
| DA1      | directional (rush), instant effect             | ACTIVE  |
| DA2      | directional (rush), continuous effect          | ACTIVE  |
| DA3      | directional blink                              | ACTIVE  |
| DA4      | directional, left, continuous effect           | ACTIVE  |
| DA5      | directional, right, continuous effect          | ACTIVE  |
| P        | passive                                        | PASSIVE |
| T        | toggle                                         | TOGGLE  |
| TG       | toggle with group                              | TOGGLE  |
| AU       | aura toggle                                    | TOGGLE  |

A directional variant `DA6` has no data and stays raw.

### 3.5 `SkillMagicType`

Package `domain.skill.kind`. 4 constants.

- `PHYSICAL` (0): P. Atk. formula, Atk. Spd., soulshots
- `MAGIC` (1): M. Atk. formula, Casting Spd., spiritshots
- `STATIC` (2): fixed cast time, not affected by speed or shots
- `SONG_DANCE` (3): song / dance

Other ints have no established client meaning and stay raw (`magicTypeRaw`).

### 3.6 `SkillBasicProperty`

Package `domain.skill.kind`. 3 constants: which abnormal-resist roll the skill uses.

- `NONE`: no abnormal-resist roll
- `PHYSICAL`: rolled against `P_ABNORMAL_RES`
- `MAGIC`: rolled against `M_ABNORMAL_RES`

Hosts whose engine expresses the roll through `Skill.saveVs` (a base-stat-keyed model) leave `basicProperty` `null`;
the two models are not converted into each other.

### 3.7 `SkillAffectScope`

Package `domain.skill.target`. 19 constants.

| constant           | meaning                                                       |
| ------------------ | ------------------------------------------------------------- |
| NONE               | no affected creatures (self-contained effect, passives)       |
| SINGLE             | the selected target only                                      |
| RANGE              | circle around the selected target                             |
| RANGE_SORT_BY_HP   | circle around the target, lowest HP first up to the limit     |
| RING_RANGE         | ring (min/max radius) around the target                       |
| POINT_BLANK        | circle around the caster                                      |
| FAN                | cone from the caster towards the target (angles on the level) |
| FAN_POINT_BLANK    | cone around the caster in its facing (no target needed)       |
| SQUARE             | rectangle from the caster towards the target                  |
| SQUARE_POINT_BLANK | rectangle from the caster in its facing                       |
| STATIC_OBJECT      | static world objects (doors, siege objects) around the target |
| PARTY              | caster's party members in range                               |
| CLAN               | caster's clan members in range                                |
| PARTY_CLAN         | party and clan members in range                               |
| ALLY               | alliance members in range                                     |
| COMMAND_CHANNEL    | command-channel members in range                              |
| DEAD_PARTY         | dead party members in range                                   |
| DEAD_CLAN          | dead clan members in range                                    |
| DEAD_ALLY          | dead alliance members in range                                |

Boss-specific scopes and scopes with no data in any known datapack stay raw (`affectScopeRaw`).

### 3.8 `SkillAffectObject`

Package `domain.skill.target`. 11 constants.

| constant          | meaning                             |
| ----------------- | ----------------------------------- |
| ALL               | every creature in scope             |
| FRIEND            | non-hostile creatures               |
| FRIEND_PLAYER     | non-hostile players only            |
| NOT_FRIEND        | hostile creatures                   |
| NOT_FRIEND_PLAYER | hostile players only                |
| CLAN              | members of the caster's clan        |
| MONSTER           | monsters only                       |
| CORPSE_NPC        | NPC corpses                         |
| UNDEAD_ENEMY      | hostile undead                      |
| HIDDEN_PLACE      | hidden objects / places (detection) |
| INVISIBLE         | invisible creatures                 |

Boss-specific objects and objects with no data in any known datapack stay raw (`affectObjectRaw`).

### 3.9 `SkillEffectScope`

Package `domain.skill.effect`. 8 constants: when and to whom an effect applies.

| constant     | meaning                                                   |
| ------------ | --------------------------------------------------------- |
| TARGET       | applied to each affected target on cast                   |
| SELF         | applied to the caster on cast                             |
| PASSIVE      | applied to the owner while the skill is known             |
| CAST_START   | applied to the target when casting begins                 |
| CHANNELING   | applied to channel targets on every channel tick          |
| PVP          | applied only when caster and target are playable          |
| PVE          | applied only when caster is playable and target is an NPC |
| ABNORMAL_END | applied when the skill's abnormal ends                    |

Effects of an operate-type `P` skill get `PASSIVE` in every host.

### 3.10 `SkillEffectType`

Package `domain.skill.effect`.

Membership rule: a handler maps to `STAT_MODIFIER` iff its whole behaviour is changing quantities that exist in
`Stat`; the modifiers travel in `SkillEffect.statModifiers` (canonical `stat`/`operation`/`value`, per-modifier
`conditionTree`), and `STAT_MODIFIER` has no own `params` except `chancePercent` (proc-gated modifiers).
Columns: value = unit of `SkillEffect.value` (`—` = no magnitude); params = canonical keys (N4 formatting).
Shared keys: `chancePercent` (land / proc chance), `skillId`+`skillLevel` / `skills`, `npcId`, `npcCount`,
`lifetimeSec`, `abnormalTypes`, `operation` (`StatOperation`: `ADD` for an absolute difference, `PERCENT` for a
percent change).

#### Damage

| constant                         | value                  | params                                                                                                               |
| -------------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------------------- |
| PHYSICAL_DAMAGE                  | power                  | `chancePercent`, `criticalChancePercent`, `ignoreShield`, `overHit`                                                  |
| PHYSICAL_DAMAGE_BY_TARGETS       | power                  | as PHYSICAL_DAMAGE                                                                                                   |
| PHYSICAL_SOUL_DAMAGE             | power                  | `criticalChancePercent`, `ignoreShield`, `overHit`                                                                   |
| PHYSICAL_DAMAGE_BY_LOW_HP        | power                  | `criticalChancePercent`, `overHit`                                                                                   |
| MAGIC_DAMAGE                     | power                  | `overHit`, `bonusNpcRaces` (`NpcRace[]`), `bonusRaceRate`, `debuffBonusRate`                                         |
| MAGIC_DAMAGE_SHIELDABLE          | power                  | `shieldDefPercent`                                                                                                   |
| MAGIC_SOUL_DAMAGE                | power                  | —                                                                                                                    |
| MAGIC_DAMAGE_BY_LOW_HP           | power                  | —                                                                                                                    |
| MAGIC_DAMAGE_BY_ABNORMAL         | power                  | `abnormalTypes`                                                                                                      |
| MP_DAMAGE                        | power                  | `critical`, `criticalLimit`                                                                                          |
| FIXED_DAMAGE                     | HP                     | `operation`                                                                                                          |
| BLOW_DAMAGE                      | power                  | `blowChancePercent`, `blowChanceBonusPercent`, `criticalChancePercent`, `overHit`, `abnormalTypes` + `abnormalPower` |
| BACKSTAB_DAMAGE                  | power                  | `blowChanceBonusPercent`, `criticalChancePercent`, `overHit`                                                         |
| SOUL_BLOW_DAMAGE                 | power                  | `blowChanceBonusPercent`, `overHit`                                                                                  |
| CHARGE_DAMAGE                    | power                  | `criticalChancePercent`, `ignoreShield`, `overHit`                                                                   |
| HP_DRAIN                         | power                  | `absorbPercent`                                                                                                      |
| INSTANT_KILL                     | —                      | `lethalChancePercent` (full), `halfKillChancePercent`                                                                |
| CP_DAMAGE_PERCENT                | percent of current CP  | —                                                                                                                    |
| HP_DAMAGE_PERCENT                | percent of current HP  | —                                                                                                                    |
| MP_DAMAGE_PERCENT                | percent of current MP  | —                                                                                                                    |
| DAMAGE_OVER_TIME                 | HP per tick            | `canKill`                                                                                                            |
| DAMAGE_OVER_TIME_PERCENT         | percent of HP per tick | `canKill`                                                                                                            |
| MAGIC_DAMAGE_OVER_TIME           | power per tick         | `canKill`                                                                                                            |
| MP_DAMAGE_OVER_TIME              | MP per tick            | —                                                                                                                    |
| AGATHION_ENERGY_DAMAGE_OVER_TIME | energy per tick        | —                                                                                                                    |

#### Healing, resurrection, death protection

| constant               | value                | params                                                                                         |
| ---------------------- | -------------------- | ---------------------------------------------------------------------------------------------- |
| HP_HEAL                | power                | —                                                                                              |
| HP_HEAL_PERCENT        | percent of max HP    | —                                                                                              |
| HP_HEAL_OVER_TIME      | HP per tick          | —                                                                                              |
| HP_HEAL_FLAT           | HP                   | —                                                                                              |
| HP_CP_HEAL             | power                | —                                                                                              |
| CP_HEAL                | CP                   | —                                                                                              |
| CP_HEAL_PERCENT        | percent of max CP    | —                                                                                              |
| CP_HEAL_OVER_TIME      | CP per tick          | —                                                                                              |
| CP_HEAL_FLAT           | CP                   | —                                                                                              |
| MP_HEAL                | power                | —                                                                                              |
| MP_HEAL_PERCENT        | percent of max MP    | —                                                                                              |
| MP_HEAL_OVER_TIME      | MP per tick          | —                                                                                              |
| MP_HEAL_BY_LEVEL       | power                | —                                                                                              |
| MP_HEAL_FLAT           | MP                   | —                                                                                              |
| REBALANCE_PARTY_HP     | —                    | —                                                                                              |
| RESURRECT              | EXP recovery percent | `hpPercent`, `mpPercent`, `cpPercent`                                                          |
| RESURRECT_ON_DEATH     | EXP recovery percent | `hpPercent`, `mpPercent`, `cpPercent`, `instanceIds`                                           |
| SURVIVE_LETHAL_DAMAGE  | —                    | `hp`/`hpPercent`, `mp`/`mpPercent`, `cp`/`cpPercent`, `skillId`+`skillLevel` (cast on survive) |
| KEEP_BUFFS_ON_DEATH    | —                    | —                                                                                              |
| NO_DEATH_PENALTY       | —                    | —                                                                                              |
| NO_SIEGE_DEATH_PENALTY | —                    | —                                                                                              |
| NO_RAID_DEATH_PENALTY  | —                    | —                                                                                              |
| PK_DEATH_PROTECTION    | —                    | —                                                                                              |
| REDUCE_DEATH_PENALTY   | —                    | —                                                                                              |

#### Stat, slots, shields

| constant                | value           | params              |
| ----------------------- | --------------- | ------------------- |
| STAT_MODIFIER           | —               | `chancePercent`     |
| INCREASE_BUFF_SLOTS     | slots           | —                   |
| MP_UPKEEP_BY_LEVEL      | MP per tick     | —                   |
| DAMAGE_ABSORB_SHIELD    | damage absorbed | `hits`, `operation` |
| DAMAGE_REDUCTION        | amount          | `operation`         |
| BLOCK_NEXT_DEBUFFS      | count           | —                   |
| SHIELD_BLOCK_ALL_ANGLES | —               | —                   |

#### Control, disable, block

| constant                   | value | params                                        |
| -------------------------- | ----- | --------------------------------------------- |
| STUN                       | —     | —                                             |
| SLEEP                      | —     | —                                             |
| PARALYZE                   | —     | —                                             |
| PETRIFY                    | —     | —                                             |
| BLOCK_ALL_ACTIONS          | —     | `allowedSkillIds`                             |
| BLOCK_SPECIFIC_ACTIONS     | —     | `actionIds`                                   |
| BLOCK_CONTROL              | —     | —                                             |
| ROOT                       | —     | —                                             |
| FEAR                       | —     | `range`                                       |
| RUN_AWAY                   | —     | `chancePercent`, `distance`                   |
| COMPEL                     | —     | —                                             |
| CONFUSION                  | —     | `chancePercent`                               |
| INCITE_NPC_INFIGHTING      | —     | `chancePercent`                               |
| BETRAY                     | —     | —                                             |
| DISARM                     | —     | —                                             |
| DISARM_SHIELD              | —     | `chancePercent`                               |
| DISARM_ARMOR               | —     | `equipSlot` (`ItemEquipSlot`)                 |
| BLOCK_MAGIC_SKILLS         | —     | —                                             |
| BLOCK_PHYSICAL_SKILLS      | —     | —                                             |
| BLOCK_PHYSICAL_ATTACK      | —     | —                                             |
| BLOCK_SKILLS               | —     | `skillIds`, `magicTypes` (`SkillMagicType[]`) |
| BLOCK_RESURRECTION         | —     | —                                             |
| BLOCK_CHAT                 | —     | —                                             |
| BLOCK_PARTY                | —     | —                                             |
| BLOCK_ESCAPE               | —     | —                                             |
| BLOCK_RUSH                 | —     | —                                             |
| BLOCK_ABNORMAL_SLOTS       | —     | `abnormalTypes`                               |
| BLOCK_DEBUFFS              | —     | —                                             |
| BLOCK_BUFFS                | —     | —                                             |
| BLOCK_DAMAGE               | —     | `blockHp`, `blockMp`                          |
| INVULNERABILITY            | —     | `blockBuffs`, `blockDebuffs`                  |
| SKILL_IMMUNITY             | —     | `skills`                                      |
| IMMOBILIZE_SELF            | —     | —                                             |
| IMMOBILIZE_PET             | —     | —                                             |
| KNOCK_BACK                 | —     | `distance`, `speed`, `knockDown`              |
| THROW_UP                   | —     | `distance`, `speed`                           |
| PULL_TO_CASTER             | —     | `speed`                                       |
| PULL_TO_TARGET             | —     | `radius`, `speed`                             |
| BLINK                      | —     | `range`, `angle`                              |
| SWAP_POSITIONS             | —     | —                                             |
| RUSH_TO_TARGET             | —     | —                                             |
| TURN_TARGET_AWAY           | —     | `chancePercent`                               |
| INTERRUPT_CAST             | —     | `chancePercent`, `fixedChance`                |
| CANCEL_TARGET              | —     | `chancePercent`                               |
| FORCE_TARGET_CASTER        | —     | —                                             |
| FORCE_TARGET_CASTER_CHANCE | —     | `chancePercent`                               |
| FORCE_ATTACK_CASTER        | —     | —                                             |
| FACE_OFF                   | —     | —                                             |
| DISABLE_AGGRESSION         | —     | —                                             |
| POLEARM_SINGLE_TARGET      | —     | —                                             |
| UNTARGETABLE               | —     | —                                             |
| BLOCK_TARGETING            | —     | —                                             |

#### Hate

| constant           | value | params            |
| ------------------ | ----- | ----------------- |
| ADD_HATE           | hate  | `includeSummoner` |
| REMOVE_ALL_HATE    | —     | `chancePercent`   |
| REMOVE_CASTER_HATE | —     | `chancePercent`   |
| RANDOMIZE_HATE     | —     | `chancePercent`   |
| TRANSFER_HATE      | —     | `chancePercent`   |

#### Dispel and abnormal manipulation

| constant                   | value   | params                                                                                         |
| -------------------------- | ------- | ---------------------------------------------------------------------------------------------- |
| DISPEL_BY_CATEGORY         | —       | `skillEffectCategory` (`BUFF`/`DEBUFF`; absent = all), `chancePercent`, `minCount`, `maxCount` |
| DISPEL_ALL                 | —       | `chancePercent`                                                                                |
| DISPEL_BY_SLOT             | —       | `abnormalTypeMaxLevels`                                                                        |
| DISPEL_BY_SLOT_PROBABILITY | —       | `abnormalTypes`, `chancePercent`                                                               |
| DISPEL_BY_SLOT_SELF        | —       | `abnormalTypes`                                                                                |
| DISPEL_BY_SKILL            | —       | `skillIds`, `chancePercent`                                                                    |
| STEAL_ABNORMALS            | —       | `skillEffectCategory`, `chancePercent`, `minCount`, `maxCount`                                 |
| UNTRANSFORM                | —       | —                                                                                              |
| ABNORMAL_TIME_CHANGE       | seconds | `abnormalTypes` or `skillIds`, `operation` (`ADD`/`SET`)                                       |

#### Stealth, state, alignment

| constant        | value | params      |
| --------------- | ----- | ----------- |
| INVISIBILITY    | —     | —           |
| REVEAL_HIDDEN   | —     | —           |
| SILENT_MOVEMENT | —     | —           |
| FAKE_DEATH      | —     | —           |
| RELAX           | —     | —           |
| STEALTH_REST    | —     | —           |
| SET_PVP_FLAG    | —     | —           |
| MODIFY_KARMA    | karma | `operation` |
| MODIFY_PK_COUNT | count | —           |
| BIG_HEAD        | —     | —           |

#### Summons, movement, teleport

| constant                     | value | params                                                                                         |
| ---------------------------- | ----- | ---------------------------------------------------------------------------------------------- |
| SUMMON_SERVITOR              | —     | `npcId`, `lifetimeSec`, `consumeItemId`, `consumeItemCount`, `consumeIntervalSec`, `expRate`   |
| SUMMON_PET                   | —     | —                                                                                              |
| SUMMON_NPC                   | —     | `npcId`, `npcCount`, `lifetimeSec`, `randomOffset`, `onTarget`, `singleInstance`, `aggressive` |
| SUMMON_GUARDIAN              | —     | `npcId`, `npcCount`, `lifetimeSec`, `singleInstance`, `clone`                                  |
| SUMMON_SHADOW                | —     | `npcId`, `lifetimeSec`                                                                         |
| SUMMON_TRAP                  | —     | `npcId`, `lifetimeSec`                                                                         |
| SUMMON_CUBIC                 | —     | `cubicId`, `cubicLevel`, `lifetimeSec`, `maxCount`, `chancePercent`                            |
| SUMMON_AGATHION              | —     | `npcId`                                                                                        |
| UNSUMMON_SERVITOR            | —     | `chancePercent`                                                                                |
| UNSUMMON_AGATHION            | —     | —                                                                                              |
| UNSUMMON_CUBIC               | —     | `cubicId`                                                                                      |
| UNSUMMON_GUARDIANS           | —     | —                                                                                              |
| SHARE_STATS_WITH_SUMMON      | —     | —                                                                                              |
| SHARE_STATS_WITH_PET         | —     | —                                                                                              |
| SHARE_SKILLS_WITH_SUMMON     | —     | —                                                                                              |
| SUMMON_ASSIST_CAST           | —     | `skills`, `ownerSkillId`, `npcId`                                                              |
| SUMMON_ASSIST_ATTACK         | —     | `npcId`                                                                                        |
| SUMMONED_NPCS_CAST_SKILL     | —     | `skillId`, `skillLevel`                                                                        |
| MOUNT                        | —     | `npcId`                                                                                        |
| FEED_PET                     | food  | —                                                                                              |
| TELEPORT_TO_LOCATION         | —     | `x`, `y`, `z`                                                                                  |
| TELEPORT_TO_LIST_DESTINATION | —     | —                                                                                              |
| ESCAPE                       | —     | `escapeDestination` (`EscapeDestination`)                                                      |
| TELEPORT_TO_TARGET           | —     | —                                                                                              |
| TELEPORT_TO_SUMMON           | —     | `distance`                                                                                     |
| SUMMON_PLAYER                | —     | `itemId`, `itemCount` (fee)                                                                    |
| SUMMON_PARTY                 | —     | —                                                                                              |
| CLAN_GATE                    | —     | —                                                                                              |

#### Skill chaining and triggers

Trigger kind lives in the type; filters in `params`. Common trigger keys: `skillId`+`skillLevel` (skill cast by
the trigger) or `skills`; `chancePercent`; `targetType` (`SkillTargetType` of the triggered cast); `criticalOnly`;
`normalAttack`, `skillAttack` (which hit sources count); `magicTypes` (`SkillMagicType[]`); `goodSkills`,
`badSkills`; `minDamage`; `minAttackerLevel`, `maxAttackerLevel`; `attackerKinds` (`ActorKind[]`); `sourceSkillIds`
(activating skills); `attributes` (`Attribute[]`, activation elements); `pvpOnly`; `levelFromSkillId` (triggered
level = the holder's level of that skill); `renewDuration`.

| constant                        | value | extra params                                                 |
| ------------------------------- | ----- | ------------------------------------------------------------ |
| CAST_SKILL                      | —     | `skills`, `abnormalTypes`/`requiredEffectSkillId`            |
| CAST_RANDOM_SKILL               | —     | `skills`                                                     |
| CAST_SKILL_BY_TARGET_RACE       | —     | `skillsByRace` (`CharacterRace:skillId`), `skillLevel`       |
| CAST_SKILL_BY_TARGET_RELATION   | —     | `allySkills`, `enemySkills`                                  |
| CAST_SKILL_BY_DISTANCE          | —     | `skillsByMaxDistance` (`distance:id:level`)                  |
| CAST_COMBO_SKILL                | —     | `requiredEffectSkillIds`, `comboSize` (2/3)                  |
| CAST_SKILL_DELAYED              | —     | `delayMs`                                                    |
| APPLY_SKILL_EFFECTS             | —     | `skills`                                                     |
| REPLACE_SKILL                   | —     | `replacementSkillId`, `replacementSkillLevel`                |
| LEARN_SKILL                     | —     | —                                                            |
| MODIFY_SKILL_REUSE              | ms    | —                                                            |
| PARTY_BUFF_SYNERGY              | —     | `requiredAbnormalTypes`, `optionalAbnormalTypes`, `minCount` |
| TRIGGER_SKILL_ON_ATTACK         | —     | common keys                                                  |
| TRIGGER_SKILL_ON_ATTACKED       | —     | common keys, `hpPercent`                                     |
| TRIGGER_SKILL_ON_LETHAL_DAMAGE  | —     | common keys                                                  |
| TRIGGER_SKILL_ON_EVADE          | —     | common keys                                                  |
| TRIGGER_SKILL_ON_SKILL_USE      | —     | common keys                                                  |
| TRIGGER_SKILL_ON_SKILL_RECEIVED | —     | common keys                                                  |
| TRIGGER_SKILL_ON_KILL           | —     | common keys, `victimKinds` (`ActorKind[]`)                   |
| TRIGGER_SKILL_ON_ENEMY_DEATH    | —     | common keys                                                  |
| TRIGGER_SKILL_ON_INTERVAL       | —     | common keys (period = `tickIntervalMs`)                      |
| TRIGGER_SKILL_ON_EFFECT_START   | —     | common keys                                                  |
| TRIGGER_SKILL_ON_EFFECT_END     | —     | common keys                                                  |
| TRIGGER_SKILL_ON_HP_PERCENT     | —     | common keys, `min`, `max` (HP percent)                       |
| TRIGGER_SKILL_ON_STAT_RANGE     | —     | common keys, `stat` (`Stat`), `min`, `max`                   |
| TRIGGER_SKILL_ON_COMBAT_POWER   | —     | common keys, `min`, `max`                                    |

#### Resources, economy, appearance

| constant                      | value       | params                                    |
| ----------------------------- | ----------- | ----------------------------------------- |
| GAIN_CHARGES                  | charges     | `maxCharges`                              |
| FILL_CHARGES                  | —           | —                                         |
| GAIN_SOULS                    | souls       | `soulType`                                |
| GAIN_SOULS_FROM_DEATHS        | —           | `expNeeded`, `soulType`                   |
| MODIFY_CHARACTER_RESOURCE     | amount      | `characterResource` (`CharacterResource`) |
| MODIFY_CHARACTER_RESOURCE_MAX | amount      | `characterResource`                       |
| CONSUME_CORPSE                | —           | —                                         |
| SPOIL                         | —           | —                                         |
| SWEEP                         | —           | —                                         |
| PLUNDER                       | —           | —                                         |
| SOW_SEED                      | —           | —                                         |
| HARVEST                       | —           | —                                         |
| FISHING_CAST                  | —           | —                                         |
| FISHING_PUMP                  | —           | —                                         |
| FISHING_REEL                  | —           | —                                         |
| CHANGE_FISHING_MASTERY        | —           | —                                         |
| OPEN_DOOR                     | —           | `chancePercent`                           |
| OPEN_CHEST                    | —           | —                                         |
| DETECT_TRAP                   | —           | —                                         |
| DISARM_TRAP                   | —           | —                                         |
| GIVE_ITEMS                    | —           | items on `SkillLevel.producedItems`       |
| GIVE_RANDOM_ITEMS             | —           | items on `SkillLevel.producedItems`       |
| CONVERT_ITEM                  | —           | —                                         |
| GIVE_EXP                      | EXP         | `percent`, `maxLevel`                     |
| GIVE_SP                       | SP          | —                                         |
| GIVE_PET_EXP                  | EXP         | —                                         |
| GIVE_RECOMMENDATIONS          | count       | —                                         |
| GIVE_FAME                     | fame        | —                                         |
| GIVE_CLAN_REPUTATION          | reputation  | —                                         |
| GIVE_PC_CAFE_POINTS           | points      | —                                         |
| GIVE_VITALITY_POINTS          | points      | —                                         |
| GIVE_VIP_POINTS               | points      | —                                         |
| GRANT_SAYHA_GRACE             | —           | —                                         |
| PAUSE_HUNTING_BONUS           | —           | —                                         |
| ADD_HUNTING_ZONE_TIME         | seconds     | `zoneId`                                  |
| EXPAND_TELEPORT_BOOKMARKS     | slots       | —                                         |
| RESET_INSTANCE_ENTRY          | —           | `instanceIds`                             |
| OPEN_COMMON_RECIPE_BOOK       | —           | —                                         |
| OPEN_DWARVEN_RECIPE_BOOK      | —           | —                                         |
| REFUEL_AIRSHIP                | fuel        | —                                         |
| TRANSFORM                     | —           | `transformationId`                        |
| CHANGE_FACE                   | style index | —                                         |
| CHANGE_HAIR_COLOR             | style index | —                                         |
| CHANGE_HAIR_STYLE             | style index | —                                         |
| TOGGLE_HAIR_ACCESSORY         | —           | —                                         |
| GROW_SIZE                     | —           | —                                         |
| SWITCH_CLASS                  | —           | `classIndex`                              |
| NOTIFY_CLAN                   | —           | `systemMessageId`                         |

#### Siege and events

| constant                  | value | params     |
| ------------------------- | ----- | ---------- |
| CREATE_SIEGE_HEADQUARTERS | —     | `advanced` |
| CREATE_TERRITORY_OUTPOST  | —     | —          |
| DESTROY_TERRITORY_OUTPOST | —     | —          |
| TAKE_CASTLE               | —     | —          |
| TAKE_CASTLE_START         | —     | —          |
| TAKE_FORTRESS             | —     | —          |
| TAKE_FORTRESS_START       | —     | —          |
| TAKE_TERRITORY_FLAG       | —     | —          |
| FLIP_BLOCK                | —     | —          |

Notes:

- Hosts without dedicated stun / sleep / paralyze / petrify handlers express them as `BLOCK_ALL_ACTIONS` (+ abnormal
  type + `SkillTrait`). Platform crowd-control logic keys on abnormal type + `trait`, not on the effect type.
- Heal / restore handlers split by mode (absolute, percent) map to one record whose type is chosen by the mode.
- Side data with no canonical meaning (e.g. the side of a castle-take effect) stays raw in `paramsRaw`.

### 3.11 `SkillConditionType`

Package `domain.skill.condition`. `params` per N4. `min?`/`max?` = at least one present.

#### CASTER — vitals, progression, identity

| constant                   | params                                                   | meaning                                    |
| -------------------------- | -------------------------------------------------------- | ------------------------------------------ |
| CASTER_LEVEL               | `min?`, `max?`                                           | caster level                               |
| CASTER_HP_PERCENT          | `min?`, `max?` (percent)                                 | current HP % of max                        |
| CASTER_MP_PERCENT          | `min?`, `max?` (percent)                                 | current MP %                               |
| CASTER_CP_PERCENT          | `min?`, `max?` (percent)                                 | current CP %                               |
| CASTER_MAX_MP              | `min?`, `max?`                                           | maximum MP                                 |
| CASTER_BASE_STAT           | `stat` (`Stat`: STR DEX CON INT WIT MEN), `min?`, `max?` | base stat within bounds                    |
| CASTER_RACE                | `characterRaces`                                         | acting player's race                       |
| CASTER_SEX                 | `characterSex` (`CharacterSex`)                          | acting player's sex                        |
| CASTER_CLASS               | `characterClasses` (`CharacterClass[]`)                  | acting player's active class               |
| CASTER_IS_SUB_CLASS_ACTIVE | —                                                        | a sub-class (not the main class) is active |
| CASTER_EXPERTISE_GRADE     | `itemGrades` (`ItemGrade[]`)                             | acting player's expertise grade            |
| CASTER_IS_HERO             | —                                                        | hero status                                |
| CASTER_IS_TRANSFORMED      | `transformationIds?`                                     | transformed (into a listed form)           |

#### CASTER — alignment, social, clan

| constant                  | params                                              | meaning                                            |
| ------------------------- | --------------------------------------------------- | -------------------------------------------------- |
| CASTER_IS_CHAOTIC         | —                                                   | karma > 0 / reputation < 0                         |
| CASTER_PK_COUNT           | `min?`, `max?`                                      | PK kills                                           |
| CASTER_IS_PVP_FLAGGED     | —                                                   | PvP flag on                                        |
| CASTER_IN_COMBAT          | —                                                   | in attack stance                                   |
| CASTER_IN_DUEL            | —                                                   | in a duel                                          |
| CASTER_IN_OLYMPIAD        | —                                                   | in an Olympiad match                               |
| CASTER_IS_CLAN_LEADER     | —                                                   | leader of own clan                                 |
| CASTER_SOCIAL_CLASS       | `min` (`SocialClass`)                               | clan social class (pledge class) ≥ min             |
| CASTER_CLAN_LEVEL         | `min`                                               | own clan level ≥ min (`min=0` = has a clan)        |
| CASTER_CLAN_HAS_RESIDENCE | `residenceType?` (`ResidenceType`), `residenceIds?` | caster's clan owns a residence (of type / with id) |
| CASTER_TERRITORY_SIDE     | `territoryIds`                                      | Territory War side                                 |

#### CASTER — location, movement

| constant                  | params                                      | meaning                                                |
| ------------------------- | ------------------------------------------- | ------------------------------------------------------ |
| CASTER_IN_ZONE            | `zoneIds`                                   | inside one of the zones                                |
| CASTER_IN_ZONE_KIND       | `zoneKinds` (`ZoneKind[]`)                  | inside a zone of the given kind                        |
| CASTER_IN_SIEGE           | `residenceIds?`, `siegeSide?` (`SiegeSide`) | inside an in-progress siege battlefield (on that side) |
| CASTER_IN_INSTANCE        | `instanceIds?`                              | inside an instance (of listed templates)               |
| CASTER_IN_VEHICLE         | —                                           | aboard a boat / airship                                |
| CASTER_IN_AIRSHIP         | —                                           | aboard an airship                                      |
| CASTER_IS_MOUNTED         | `mountTypes?` (`MountType[]`)               | riding a mount (of listed kind)                        |
| CASTER_IS_FLYING          | —                                           | airborne                                               |
| CASTER_MOVEMENT_STATE     | `movementStates` (`MovementState[]`)        | current posture/motion                                 |
| CASTER_POSITION_TO_TARGET | `relativePositions` (`RelativePosition[]`)  | caster's position relative to the target's facing      |
| CASTER_IS_NEAR_NPC        | `npcIds`, `radius`                          | a listed NPC is within radius                          |

#### CASTER — effects, skills, resources

| constant                    | params                                                     | meaning                                     |
| --------------------------- | ---------------------------------------------------------- | ------------------------------------------- |
| CASTER_HAS_EFFECT           | `skillId`, `minLevel?`, `maxLevel?`                        | active effect of the skill                  |
| CASTER_HAS_ABNORMAL         | `abnormalType`, `minLevel?`, `maxLevel?`                   | abnormal of the type at that abnormal level |
| CASTER_HAS_SKILL            | `skillId`, `minLevel?`, `maxLevel?`                        | knows the skill                             |
| CASTER_SOULS                | `min?`, `max?`, `soulType?` (`SoulType`; absent = untyped) | charged souls                               |
| CASTER_HAS_MAX_SOULS        | `soulType?`                                                | souls at the cap                            |
| CASTER_CHARGES              | `min?`, `max?`                                             | charges (momentum/force)                    |
| CASTER_RESOURCE             | `characterResource` (`CharacterResource`), `min?`, `max?`  | class / character resource points           |
| CASTER_VITALITY_POINTS      | `min?`, `max?`                                             | vitality points                             |
| CASTER_WEIGHT_PERCENT       | `min?`, `max?` (percent)                                   | inventory load %                            |
| CASTER_FREE_INVENTORY_SLOTS | `min`                                                      | free inventory slots                        |

#### CASTER — equipment, companions, gates

| constant                     | params                                                                                    | meaning                                               |
| ---------------------------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| CASTER_HAS_EQUIPPED_TYPE     | `weaponTypes?` (`WeaponType[]`), `armorTypes?` (`ArmorType[]`)                            | equipped gear of a listed type                        |
| CASTER_HAS_TWO_HANDED_WEAPON | —                                                                                         | active weapon occupies both hands                     |
| CASTER_HAS_MAGIC_WEAPON      | —                                                                                         | active weapon is a magic weapon                       |
| CASTER_HAS_CHANGEABLE_WEAPON | —                                                                                         | active weapon has a change-weapon counterpart         |
| CASTER_HAS_CURSED_WEAPON     | —                                                                                         | a cursed weapon is equipped                           |
| CASTER_HAS_EQUIPPED_ITEM     | `itemIds?`, `itemEquipSlot?` (`ItemEquipSlot`), `minCount?`, `minEnchant?`                | item(s) equipped (in slot, enchant ≥)                 |
| CASTER_HAS_CLOAK_SLOT        | —                                                                                         | worn armor opens the cloak slot                       |
| CASTER_HAS_SERVITOR          | `npcIds?`                                                                                 | has a servitor (of listed NPC ids)                    |
| CASTER_HAS_PET               | `controlItemIds?`                                                                         | has a pet (of listed control items)                   |
| CASTER_HAS_SUMMONED_NPC      | `npcIds`                                                                                  | has spawned a listed NPC by skill                     |
| CASTER_HAS_RECALL_CREATURE   | —                                                                                         | has a recall creature                                 |
| CASTER_HAS_AGATHION          | `agathionIds?`                                                                            | an agathion is summoned                               |
| CASTER_AGATHION_HAS_ENERGY   | —                                                                                         | the agathion bracelet has energy left                 |
| CASTER_CAN_PERFORM           | `skillConditionAction` (`SkillConditionAction`), `amount?`, `transformationId?`, `range?` | opaque engine gate: the action is currently permitted |

#### SUMMON

| constant          | params                   | meaning                  |
| ----------------- | ------------------------ | ------------------------ |
| SUMMON_HP_PERCENT | `min?`, `max?` (percent) | the caster's summon HP % |

#### TARGET

| constant                    | params                                       | meaning                                        |
| --------------------------- | -------------------------------------------- | ---------------------------------------------- |
| TARGET_LEVEL                | `min?`, `max?`                               | target level                                   |
| TARGET_HP_PERCENT           | `min?`, `max?` (percent)                     | target HP %                                    |
| TARGET_MP_PERCENT           | `min?`, `max?` (percent)                     | target MP %                                    |
| TARGET_CP_PERCENT           | `min?`, `max?` (percent)                     | target CP %                                    |
| TARGET_RACE                 | `characterRaces?`, `npcRaces?` (`NpcRace[]`) | target race                                    |
| TARGET_CLASS                | `characterClasses`                           | target player's class                          |
| TARGET_IS_CHAOTIC           | —                                            | target player has karma                        |
| TARGET_PK_COUNT             | `min?`, `max?`                               | target PK count                                |
| TARGET_IS_PVP_FLAGGED       | —                                            | target PvP-flagged                             |
| TARGET_IN_COMBAT            | —                                            | target in attack stance                        |
| TARGET_IS_AGGRESSIVE        | —                                            | target monster is aggressive                   |
| TARGET_IS_FEARED            | —                                            | target is under fear                           |
| TARGET_IS_CORPSE            | —                                            | target is a dead monster/summon still spawned  |
| TARGET_KIND                 | `actorKinds` (`ActorKind[]`)                 | target is an actor of a listed kind            |
| TARGET_NPC_ID               | `npcIds`                                     | target NPC template id                         |
| TARGET_DOOR_ID              | `doorIds`                                    | target door id                                 |
| TARGET_IN_CASTER_PARTY      | `includeCaster`                              | target is in the caster's party                |
| TARGET_IN_CASTER_CLAN       | —                                            | target player is in the caster's clan          |
| TARGET_DISTANCE             | `min?`, `max?` (game units)                  | caster↔target distance                         |
| TARGET_IN_SIEGE             | `residenceIds?`, `siegeSide?`                | target inside an in-progress siege battlefield |
| TARGET_HAS_EFFECT           | `skillId`, `minLevel?`, `maxLevel?`          | target has an active effect of the skill       |
| TARGET_HAS_ABNORMAL         | `abnormalType`, `minLevel?`, `maxLevel?`     | target has an abnormal of the type             |
| TARGET_HAS_ABNORMAL_VISUAL  | `abnormalVisualEffects` (engine tokens)      | target shows a listed abnormal visual effect   |
| TARGET_HAS_SKILL            | `skillId`, `minLevel?`, `maxLevel?`          | target knows the skill                         |
| TARGET_HAS_EQUIPPED_TYPE    | `weaponTypes?`, `armorTypes?`                | target's weapon / body armor type              |
| TARGET_HAS_EQUIPPED_ITEM    | `itemIds`, `minCount?`                       | target has the item equipped                   |
| TARGET_WEIGHT_PERCENT       | `min?`, `max?` (percent)                     | target load %                                  |
| TARGET_FREE_INVENTORY_SLOTS | `min`                                        | target free inventory slots                    |

#### CONTEXT, WORLD

| constant             | params          | meaning                                               |
| -------------------- | --------------- | ----------------------------------------------------- |
| CONTEXT_CHANCE       | `chancePercent` | random roll succeeds                                  |
| CONTEXT_SKILL_ID     | `skillIds`      | the skill in evaluation context has a listed id       |
| CONTEXT_IS_SKILL_USE | —               | evaluated within a skill use (vs auto-attack / equip) |
| WORLD_IS_NIGHT       | —               | in-game time is night                                 |

### 3.12 `SkillConditionLogic`

Package `domain.skill.condition`. 2 constants.

- `AND`: all children hold; also the implicit list of a level's conditions.
- `OR`: any child holds.

`NOT` is the node's `negated`, never a container.

### 3.13 `SkillConditionAction`

Package `domain.skill.condition`. 18 constants (argument of `CASTER_CAN_PERFORM`).

| constant                  | meaning (engine gate permits…)                      |
| ------------------------- | --------------------------------------------------- |
| ESCAPE                    | escaping (no escape block, not in restricted state) |
| SUMMON_PLAYER             | summoning a player to the caster                    |
| SUMMON_SERVITOR           | summoning a servitor                                |
| SUMMON_PET                | summoning a pet                                     |
| SUMMON_CUBIC              | summoning another cubic                             |
| SUMMON_SIEGE_GOLEM        | summoning a siege golem                             |
| TRANSFORM                 | transforming (`transformationId?`)                  |
| UNTRANSFORM               | leaving the transformation                          |
| SWEEP                     | sweeping a spoiled corpse                           |
| RESURRECT                 | resurrecting the target                             |
| REFUEL_AIRSHIP            | refuelling (`amount`)                               |
| TAKE_CASTLE               | engraving the castle artifact                       |
| TAKE_FORTRESS             | capturing the fortress flag                         |
| CREATE_SIEGE_HEADQUARTERS | building a siege HQ / camp / advanced base          |
| CREATE_TERRITORY_OUTPOST  | building a Territory War outpost                    |
| BLINK                     | blinking (geodata path free over `range`)           |
| EXPAND_TELEPORT_BOOKMARKS | adding teleport bookmark slots (`amount`)           |
| GAIN_PC_CAFE_POINTS       | earning PC café points                              |

### 3.14 `SkillTreeKind`

Package `domain.skill`. 20 constants.

| constant         | meaning                                                                  |
| ---------------- | ------------------------------------------------------------------------ |
| CLASS            | class skills (no class = common tree)                                    |
| TRANSFER         | skills transferable to other classes                                     |
| SUB_CLASS        | sub-class certification skills                                           |
| CLAN             | clan skills                                                              |
| CLAN_SUBUNIT     | clan sub-unit (squad) skills                                             |
| NOBLESSE         | noblesse skills                                                          |
| HERO             | hero skills                                                              |
| TRANSFORMATION   | transformation skills                                                    |
| COLLECTION       | collection skills                                                        |
| FISHING          | fishing skills                                                           |
| GAME_MASTER      | GM skills                                                                |
| GAME_MASTER_AURA | GM aura skills                                                           |
| ALCHEMY          | alchemy skills                                                           |
| CUSTOM           | server-defined learnable tree; `SkillLearn.treeName` tells several apart |
| RACE             | race skills                                                              |
| REVELATION       | revelation (chaos) skills                                                |
| ABILITY          | ability-point skills; position in `SkillLearn.ability*`                  |
| DUAL_CLASS       | dual-class skills                                                        |
| AWAKENING_SAVE   | skills kept through awakening                                            |
| CERTIFICATION    | certification skills                                                     |

`RACE`, `REVELATION`, `ABILITY`, `DUAL_CLASS`, `AWAKENING_SAVE`, `CERTIFICATION` are included although not every
datapack ships rows for them: the API is ready before a datapack fills them. Engine-internal pre-merge copies of a
tree are never emitted.

### 3.15 `SkillRewardKind`

Package `domain.skill`. 4 constants.

- `ITEM`: id > 0, a real item template
- `PC_CAFE_POINTS`: pseudo id -100
- `CLAN_REPUTATION`: pseudo id -200
- `FAME`: pseudo id -300

Other negative ids (server currencies, multisell-only pseudo-items) are not skill reward kinds and stay raw in
`itemTemplateId`.

### 3.16 `StatOperation`

Package `domain.stat`. 10 constants.

| constant      | meaning                                                |
| ------------- | ------------------------------------------------------ |
| ADD           | value += x                                             |
| SUBTRACT      | value -= x                                             |
| MULTIPLY      | value *= x (chained)                                   |
| DIVIDE        | value /= x                                             |
| SET           | value = x                                              |
| PERCENT       | adds x percent; percents of one stat sum, applied once |
| BASE_MULTIPLY | value += base value × x                                |
| SHARE         | summon receives x of the owner's stat                  |
| ENCHANT       | item enchant bonus by enchant level                    |
| ENCHANT_HP    | max-HP bonus of enchanted armor                        |

Operations with no data (enchant add / enchant multiply) stay raw (`operationRaw`).

### 3.17 Small enums

| enum                | package                     | constants (ordered)                                                                                                                 | meaning                                                                        |
| ------------------- | --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `EscapeDestination` | `domain.skill.effect`       | `TOWN, CASTLE, FORTRESS, CLAN_HALL, SIEGE_HEADQUARTERS`                                                                             | where an escape teleports (engine `TeleportWhereType` / escape type)           |
| `ActorKind`         | `domain.world`              | `PLAYER, PLAYABLE, SUMMON, SERVITOR, PET, NPC, ATTACKABLE, MONSTER, RAID_BOSS, EPIC_BOSS, CHEST, TRAP, DOOR`                        | kind of world actor; `SUMMON` = servitor or pet; `PLAYABLE` = player or summon |
| `ZoneKind`          | `domain.world`              | `PEACE, LANDING, WATER`                                                                                                             | zone kinds tested by skills                                                    |
| `MountType`         | `domain.character.state`    | `STRIDER, WYVERN, WOLF`                                                                                                             | rideable mount kind                                                            |
| `MovementState`     | `domain.character.state`    | `SITTING, STANDING, WALKING, RUNNING`                                                                                               | posture / motion                                                               |
| `RelativePosition`  | `domain.character.state`    | `FRONT, SIDE, BEHIND`                                                                                                               | position relative to the other creature's facing (engine `BACK` → `BEHIND`)    |
| `SoulType`          | `domain.character.resource` | `LIGHT, SHADOW`                                                                                                                     | typed souls; untyped souls have the `soulType` param absent                    |
| `CharacterResource` | `domain.character.resource` | `DEATH_POINTS, BEAST_POINTS, ASSASSINATION_POINTS, LIGHT_POINTS, WOLF_POINTS, CRAFT_POINTS, MAGIC_LAMP_POINTS`                      | class / character point pools                                                  |
| `ResidenceType`     | `domain.clan`               | `CASTLE, FORTRESS, CLAN_HALL`                                                                                                       | clan residence kind                                                            |
| `SiegeSide`         | `domain.clan`               | `ATTACKER, DEFENDER, NEUTRAL`                                                                                                       | side in a siege                                                                |
| `SocialClass`       | `domain.clan`               | `VAGABOND, VASSAL, APPRENTICE, HEIR, KNIGHT, ELDER, BARON, VISCOUNT, COUNT, MARQUIS, DUKE, GRAND_DUKE, DISTINGUISHED_KING, EMPEROR` | clan member social class (pledge class), ascending; engine ordinal = rank      |
| `ItemGrade`         | `domain.item`               | `NONE, D, C, B, A, S, S80, S84, R, R95, R99, EVENT`                                                                                 | item / expertise grade (crystal type); `EVENT` = event items                   |

Existing enum extended: `WeaponType` (moved to `domain.item`) gets `PISTOLS` appended. The engine "no weapon" kind
maps to the existing `WeaponType.NONE` (not `FIST`).

Existing enums used unchanged: `Attribute`, `ArmorType` (already has `SIGIL`), `ItemEquipSlot`, `CharacterRace`,
`CharacterSex`, `CharacterClass`, `NpcRace`; `SkillEffectCategory` and `SkillOperation` keep their content and move
to `domain.skill.effect` and `domain.skill.kind` (§1a).

## 4. Host guidance

How a host fills the record. The rules are build-agnostic; the engine-specific token tables belong to the host.

- **Canonical vs raw.** The host fills `<name>Raw` from the engine token and `<name>` only when an exact canonical
  constant exists (N1, N2). Never guess: an unmapped token stays `null` in the canonical field and travels in
  `<name>Raw` / `typeRaw`.
- **Target shape.** `targetType` is canonical; the affected set is read from `affectScope` + `affectObject`, which
  every host fills. Hosts whose engine has single-axis target tokens (shape embedded, e.g. a front-cone area) derive
  `affectScope` / `affectObject` from the token and leave `affectScopeRaw` / `affectObjectRaw` `null` (no engine
  token); hosts whose engine has separate scope / object tokens map them directly.
- **One engine object to many records (N3).** One engine effect, func or condition object may produce several wire
  records; each keeps the same raw token and its own params.
- **`_RES` inversion (N7).** Vulnerability-oriented engine stats map to `_RES` only through the exact inversion
  (`ADD`/`SUBTRACT` swap, `PERCENT x` to `PERCENT -x`); any other operation leaves `stat = null`.
- **Derived effects.** When a skill's behaviour is encoded in the engine as a skill type or a skill-level parameter
  rather than an effect object (e.g. a damage skill type, a lethal chance), the host may synthesize the effect. A
  synthesized effect has `derived = true`, `typeRaw` = the source raw token, carries the level's power / element /
  trait in `value` and `params`, and is emitted only when the skill has no engine effect of the same type already.
- **Passive scope.** Effects of an operate-type `P` skill get scope `PASSIVE` in every host, whatever the engine's own
  scope token says.
- **Stat modifiers live in one place.** `SkillLevel.statModifiers` holds modifiers applied by the level without an
  effect wrapper; the host never copies them into `STAT_MODIFIER` effects (no double counting).
- **Stat modifier notes.** Handlers that write one stat per magic type map to the `P_SKILL_` / `M_SKILL_` /
  `STATIC_SKILL_` / `SONG_DANCE_` variant, other magic types give `stat = null`. UI breakdown buckets of the same
  value (e.g. active / passive experience bonus) are not emitted. Positional critical bonuses become the base stat
  plus a modifier `conditionTree` of `CASTER_POSITION_TO_TARGET`; level / stat / MP / movement / night bonuses become
  `CASTER_LEVEL`, `CASTER_BASE_STAT`, `CASTER_MAX_MP`, `CASTER_MOVEMENT_STATE`, `WORLD_IS_NIGHT` conditions on the
  modifier.
- **Enchant route.** `SkillEnchantRoute.node` is the full `SkillLevel` payload of the enchanted variant
  (`node.level` = `baseLevel`); route-level fields keep only identity, enchant costs and the chance map. Chances are
  keyed by character level as the engine defines it; the host converts its own indexing.
- **Header vs level.** Target, affect, magic type, basic property and trait are read from the skill's canonical level.
  If levels of one skill differ in these values, the host emits the canonical level's values.
- **Learn entries.** `Skill.learns` lists every skill-tree entry that teaches the skill, all tree kinds; `null` means
  no tree teaches it. A class tree is `treeKind = CLASS` with a non-null `clazz`.

## 5. Compatibility

- 0.101.0 is breaking (§2.11). Wire JSON is not compatible with 0.100: a consumer deserializing `skill` must be
  upgraded together with the producer. Consumers that map by field name must change mappings for the retyped fields
  listed in §2.11.
- Consumers should enable unknown-enum-as-null deserialization (e.g. Jackson `READ_UNKNOWN_ENUM_VALUES_AS_NULL`) so
  that future vocabulary additions are additive: the canonical field then reads `null` while the matching `<name>Raw`
  field still carries the engine token.
- Existing enum constants are never renamed or removed; new constants are appended after the frozen ones.
