# gd-sync augmentation contract (nx-gs-adapter-api 0.116.0; option skill type: next breaking api release)

This file is the living, build-agnostic contract of the augmentation catalog on the gd-sync stream: the entities
`itemoption`, `lifestone` and `augmentationtargetgroup`. It is kept in sync with the DTOs in `kafka.sync.gd.itemoption`,
`kafka.sync.gd.augmentation.*` and `kafka.sync.gd.StatModifier`, and with the enums `domain.item.ItemSkillType`
(shared with item-template skills, defined in [`spec.md`](spec.md) §8.1), `domain.item.LifeStoneGrade` and
`domain.item.ItemOptionQuality`, in the same commit: any change to one updates the other. The module mechanics (burst
protocol, readiness, `null` vs empty snapshot) are in [`spec.md`](spec.md); the naming rules N1-N10 of
[`skill-contract.md`](skill-contract.md) apply here unchanged.

The host computes every probability with its own roll code and checks applicability with the same check its refine
packet uses. The platform stores the result and never repeats the engine's algorithm: roll algorithms differ between
builds, and a platform-side copy would break silently.

## 1. Entities

| `entityName`              | pk                           | payload                   | provider                          |
| ------------------------- | ---------------------------- | ------------------------- | --------------------------------- |
| `itemoption`              | `ItemOption.id`              | `ItemOption`              | `ItemOptionProvider`              |
| `lifestone`               | `LifeStone.itemTemplateId`   | `LifeStone`               | `LifeStoneProvider`               |
| `augmentationtargetgroup` | `AugmentationTargetGroup.id` | `AugmentationTargetGroup` | `AugmentationTargetGroupProvider` |

Three entities, not one, because `SNAPSHOT_COMPLETE` + `count` and the stale-row delete by `syncId` work per entity.
Each is a full snapshot, one record per aggregate; nested lists are the complete set of that aggregate, so the platform
replaces its rows with them. A `LifeStone` is one record with all its outcomes, so the record size is bounded by the
outcomes of one stone (worst known build: about 60 KB); no per-record size guard applies. All pks are numeric, as `GameDataSyncEvent.pk` is a `Long`.

## 2. Packages

Prefix `app.l2nx.gs.adapter.api.`:

| package                                  | types                                                                                       |
| ---------------------------------------- | ------------------------------------------------------------------------------------------- |
| `kafka.sync.gd`                          | `StatModifier` (shared with the `skill` entity)                                             |
| `kafka.sync.gd.itemoption`               | `ItemOption`, `ItemOptionSkill`                                                             |
| `kafka.sync.gd.augmentation.lifestone`   | `LifeStone`, `AugmentationOutcome`                                                          |
| `kafka.sync.gd.augmentation.targetgroup` | `AugmentationTargetGroup`                                                                   |
| `domain.item`                            | `ItemSkillType` ([planned], was `ItemOptionTrigger`), `LifeStoneGrade`, `ItemOptionQuality` |
| `spi.provider`                           | the three providers of §1                                                                   |

The option catalog sits outside `augmentation` because enchanting references the same option ids.

## 3. DTOs

### 3.1 `ItemOption`

| field           | type                    | null     | semantics                                                    |
| --------------- | ----------------------- | -------- | ------------------------------------------------------------ |
| `id`            | `int`                   | non-null | option id; one id space for augmentation and enchant options |
| `statModifiers` | `List<StatModifier>`    | non-null | stat bonuses of the option; empty when it has none           |
| `skills`        | `List<ItemOptionSkill>` | non-null | skills the option brings; empty when it has none             |

An option the engine defines with no bonus at all is still sent: other data references its id.

### 3.2 `ItemOptionSkill`

[planned] Shape of the next breaking api release ([`spec.md`](spec.md) §8); until then the code carries
`trigger` (`ItemOptionTrigger`: `ATTACK`, `MAGIC`, `CRITICAL`, `null` for a granted skill) and no `typeRaw`.

| field           | type            | null     | semantics                                                                                     |
| --------------- | --------------- | -------- | --------------------------------------------------------------------------------------------- |
| `skillId`       | `int`           | non-null |                                                                                               |
| `skillLevel`    | `int`           | non-null |                                                                                               |
| `type`          | `ItemSkillType` | nullable | when the skill is in effect or cast (§4.1); `null` when the engine token has no exact meaning |
| `typeRaw`       | `String`        | non-null | engine token or construct the type comes from ([`spec.md`](spec.md) §8.5); never empty        |
| `chancePercent` | `Double`        | nullable | cast chance on the event, `0 < p <= 100`; present exactly for the chance events of §4.1       |

`EQUIPPED` is a granted skill: it is learned while the item is equipped. Whether it is active or passive is the skill's
own `operateType`; the option does not repeat it, because the engine grants both kinds the same way. With an event type
the skill is cast with `chancePercent` on that event; the event is the option's data, since the cast skill does not know
which event fires it.

The builder checks the pairing (`chancePercent` present exactly for a chance event) and rejects a type an option cannot
express (`EQUIPPED_ENCHANT` needs a threshold the option has no field for). The constructor checks only `typeRaw` and the
range (the same `0 < p <= 100` as `AugmentationOutcome`, so a zero chance is rejected: an event that never fires is not
sent), so a type unknown to the consumer reads as `null` without rejecting the option; `typeRaw` still tells what it
was, and a chance without a known type is an event the consumer cannot place, not a granted skill.

### 3.3 `StatModifier`

The stat modifier of skills, moved to the gd root and shared by both entities; its fields and rules are in
[`skill-contract.md`](skill-contract.md) §2.6 and N7. An option whose bonus the engine expresses as effects rather than
stat functions maps them into `StatModifier` by the `STAT_MODIFIER` membership rule of the skill contract; a stat with
no exact canonical meaning is `stat = null` with `statRaw` kept.

### 3.4 `LifeStone`

| field                        | type                        | null     | semantics                                                                                       |
| ---------------------------- | --------------------------- | -------- | ----------------------------------------------------------------------------------------------- |
| `itemTemplateId`             | `int`                       | non-null | the stone's item template; the stone has no id of its own                                       |
| `grade`                      | `LifeStoneGrade`            | nullable | canonical grade (§4.2); `null` when the engine grade has no exact meaning or the stone has none |
| `gradeRaw`                   | `String`                    | nullable | engine grade token; `null` only when the engine has no grade, so non-null whenever `grade` is   |
| `level`                      | `Integer`                   | nullable | level of the stone itself; `null` when the build has none                                       |
| `minCharacterLevel`          | `Integer`                   | nullable | lowest character level that may use the stone; `null` when the build has no such requirement    |
| `augmentationTargetGroupIds` | `List<Integer>`             | non-null | every group whose items the engine accepts with this stone; no repeats                          |
| `outcomes`                   | `List<AugmentationOutcome>` | non-null | every outcome with a non-zero probability                                                       |

Invariants, checked by the constructor (a consumer deserializing with these classes rejects a violating stone):

- `grade != null` implies `gradeRaw != null`; `gradeRaw` without `grade` is valid (a token with no canonical meaning);
- every outcome references a group listed in `augmentationTargetGroupIds`;
- a `(augmentationTargetGroupId, slot, itemOptionId)` triple appears at most once;
- the chances of one `(augmentationTargetGroupId, slot)` add up to at most 100, within
  `LifeStone.CHANCE_SUM_TOLERANCE_PERCENT` (`1e-6`) for floating-point rounding. A consumer that re-checks the sum uses
  the same tolerance.

A listed group without outcomes is valid: the engine accepts the stone and applies an empty augmentation, and the data
says so.

### 3.5 `AugmentationOutcome`

| field                       | type     | null     | semantics                                                                          |
| --------------------------- | -------- | -------- | ---------------------------------------------------------------------------------- |
| `augmentationTargetGroupId` | `int`    | non-null | the target group the outcome applies to                                            |
| `slot`                      | `int`    | non-null | option slot of the augmentation: `1` = low 16 bits, `2` = high 16 bits of its word |
| `itemOptionId`              | `int`    | non-null | the option that lands in the slot                                                  |
| `chancePercent`             | `double` | non-null | final probability of this option in this slot, `0 < p <= 100`                      |

`chancePercent` is the marginal of one slot. Marginals of different slots are **not** independent (a roll may decide
both slots together), so "chance to get the option in any slot" cannot be derived from them.

### 3.6 `AugmentationTargetGroup`

| field             | type            | null     | semantics                                      |
| ----------------- | --------------- | -------- | ---------------------------------------------- |
| `id`              | `int`           | non-null | host key of the group, stable across snapshots |
| `itemTemplateIds` | `List<Integer>` | non-null | member templates                               |

A group is the set of item templates that enter the augmentation roll the same way, so a stone yields the same
outcomes for every member. The wire carries no kind of the group: the consumer derives it from the member templates
through its item catalog (item class, magic-weapon flag, equip slot), so the wire does not duplicate the catalog.

## 4. Enums

A consumer reads a constant it does not know as `null` (Jackson `READ_UNKNOWN_ENUM_VALUES_AS_NULL`). New constants are
appended; a constant or an enum is renamed or removed only in a breaking api release (as `ItemOptionTrigger`, §6).

### 4.1 `ItemSkillType` (`domain.item`)

[planned] The vocabulary and its pairing rules are defined once in [`spec.md`](spec.md) §8.1, shared with item-template
skills. An engine option uses four of its constants:

| constant                        | event                                                                                             | was        |
| ------------------------------- | ------------------------------------------------------------------------------------------------- | ---------- |
| `EQUIPPED`                      | none: the skill is granted while the item is equipped                                             | `null`     |
| `ON_PHYSICAL_HIT`               | a normal-attack hit that is not critical, and every physical skill cast, once per affected target | `ATTACK`   |
| `ON_MAGIC_SKILL_CAST`           | every magic skill cast, once per affected target                                                  | `MAGIC`    |
| `ON_NORMAL_ATTACK_CRITICAL_HIT` | a critical normal-attack hit; a critical hit of a physical skill does not fire it                 | `CRITICAL` |

A build whose options use another constant of §8.1 (an Essence option cast on equip, for example) sends it as is.

### 4.2 `LifeStoneGrade` (`domain.item`)

| constant  | meaning                                     |
| --------- | ------------------------------------------- |
| `NONE`    | the plain stone of its level                |
| `MID`     | mid-grade stone                             |
| `HIGH`    | high-grade stone                            |
| `TOP`     | top-grade stone                             |
| `JEWELRY` | stone for jewelry (neck, ear, finger slots) |
| `GREAT`   | Great Life Stone                            |

`grade = null` is not `NONE`: `NONE` is a real grade (the plain stone), `null` means no canonical grade. Lower stone
tiers of other lines are added with the first provider that sends them.

### 4.3 `ItemOptionQuality` (`domain.item`)

Quality tier of an option as the game client shows it: the colour of the option lines in the item tooltip, lowest
tier first.

| constant | tooltip colour |
| -------- | -------------- |
| `YELLOW` | yellow         |
| `BLUE`   | blue           |
| `PURPLE` | purple         |
| `RED`    | red            |

No DTO carries it yet: the platform takes the tier from the client patch and keeps the vocabulary here, so a host
that later sends option quality uses the same tokens.

## 5. Host guidance

- **Probabilities come from the engine's own roll.** Compute each `(stone, group, slot)` marginal exactly from the roll
  code, by walking its random draws, not by sampling. Outcomes with probability zero are not sent.
- **Slot sums.** When a weighted scan gives its remainder to the last entry of a list, the slot is empty only when the
  list is empty, and the sum of that slot is 100. A sum below 100 means the engine can leave the slot empty.
- **Applicability is explicit.** `augmentationTargetGroupIds` holds the groups the engine accepts with the stone: the
  stone's own flags (weapon / armor augmentation), or the stone and item-group pairs of lines that define them. A target
  type (weapon, armor, accessory) the server has switched off in its configuration is neither listed nor sent as a group.
- **Group members** are the templates that pass the engine's template-level applicability check, the same predicate the
  refine packet calls; per-instance conditions (already augmented, equipped, and so on) are not part of the catalog.
- **Group ids** are stable host numbers; a line that names its item groups uses their ids.
- **Grades.** `gradeRaw` is the engine token as a string. A grade maps to a `LifeStoneGrade` only when its meaning is
  exact (N2); a stone of a line without grades sends `grade = null` and `gradeRaw = null`.
- **Option catalog.** `itemoption` sends every option the engine loaded, enchant options included, not only those a
  stone can roll.
- **Not ready.** Each provider returns `null` until the host has loaded the data (§2 of [`spec.md`](spec.md)).
- **Isolate per-aggregate failures.** The constructors reject a violating aggregate with `IllegalArgumentException`.
  A provider builds each aggregate on its own, catches that failure, logs the aggregate key with the reason and skips
  it; the rest of the snapshot is sent. A `snapshot()` that throws aborts the whole burst of the entity, so one bad
  stone or option must never reach that path.

## 6. Compatibility

- 0.116.0 adds the three entities; nothing on the wire changes for existing ones.
- [planned] Next breaking api release: `ItemOptionSkill.trigger` becomes `type` + `typeRaw`, `ItemOptionTrigger` is
  removed (`ItemSkillType`, [`spec.md`](spec.md) §8). No straddle on the platform: an old host's options are rejected
  until it restarts on the new release, and the platform keeps the last good snapshot meanwhile (§8.6 there).
- `SkillStatModifier` (`kafka.sync.gd.skill.effect`) became `StatModifier` (`kafka.sync.gd`) in the same release. The
  JSON shape is unchanged; only Java imports of hosts and consumers break.
- A consumer must route the three entity names before a host sends them; an unknown `entityName` is skipped by the
  platform consumer.
