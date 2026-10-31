# gd-sync fishing gear contract

[done] Released in api 0.120.0 / gd-sync 0.16.0. This file is the living, build-agnostic contract of the fishing gear catalog on the gd-sync stream: the
entities `fishingbait`, `fishingrod` and `fishingshot`. It is kept in sync with the DTOs in `kafka.sync.gd.fishing` in
the same commit: any change to one updates the other. The module mechanics (burst protocol, readiness, `null` vs empty
snapshot) are in [`spec.md`](spec.md); the naming rules N1-N10 of [`skill-contract.md`](skill-contract.md) apply here
unchanged.

## Problem

An app that lets a player fish without the game client has to show which of the player's items are a rod, a bait or a
fishing shot that this server accepts, which rod is the best one, and how long a bait stack lasts. The item template
alone does not say it: which items a build accepts as fishing gear, and the per-item fishing parameters, are server
data (a build's fishing tables, a server's own configuration), not properties of the item. Without a catalog the
frontend keeps template id lists and timing tables in code.

The catalog describes the gear, never the mechanics: how a shot changes a catch, the catch formula, daily penalties
and limits stay host rules. Penalties already travel as activity usage ([`017`](../017-character-runtime-activity.md)),
the session rules as app settings ([`041`](../041-settings-sync.md)).

## 1. Entities

| `entityName`  | pk                           | payload       | provider              |
| ------------- | ---------------------------- | ------------- | --------------------- |
| `fishingbait` | `FishingBait.itemTemplateId` | `FishingBait` | `FishingBaitProvider` |
| `fishingrod`  | `FishingRod.itemTemplateId`  | `FishingRod`  | `FishingRodProvider`  |
| `fishingshot` | `FishingShot.itemTemplateId` | `FishingShot` | `FishingShotProvider` |

Three entities, so `SNAPSHOT_COMPLETE` + `count` and the stale-row delete work per kind of gear. A record exists for
every item the server accepts in that role and only for those: membership is the rule "this item is a bait / rod / shot
here", whatever the item template's type says. A server without fishing registers no provider; a provider whose
server accepts no item of a kind returns an empty collection.

## 2. Packages

Prefix `app.l2nx.gs.adapter.api.`:

| package                 | types                                      |
| ----------------------- | ------------------------------------------ |
| `kafka.sync.gd.fishing` | `FishingBait`, `FishingRod`, `FishingShot` |
| `domain.item`           | `ItemGrade` (existing)                     |
| `spi.provider`          | the three providers of §1                  |

## 3. DTOs

The model takes the richest public fishing data (the later lines' bait and rod tables) as its base and adds the
fields other builds have as nullable extensions. A field a build does not have is `null`; consumers degrade.

### 3.1 `FishingBait`

| field               | type      | null     | validation                                               | semantics                                                                                 |
| ------------------- | --------- | -------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `itemTemplateId`    | `int`     | non-null | `> 0`                                                    | the bait's item template                                                                  |
| `chancePercent`     | `double`  | non-null | `0..100`, finite                                         | base catch chance of a cast with this bait, before rod, shot and penalties                |
| `minCharacterLevel` | `Integer` | nullable | `> 0`; `min <= max` when both are set                    | lowest character level that may use the bait; `null` = no bound                           |
| `maxCharacterLevel` | `Integer` | nullable | `> 0`; `min <= max` when both are set                    | highest character level that may use the bait; `null` = no bound                          |
| `fishingTimeMinMs`  | `Long`    | nullable | only as a pair with `fishingTimeMaxMs`; `0 < min <= max` | shortest fishing time with this bait; `null` when the build times fishing by the rod only |
| `fishingTimeMaxMs`  | `Long`    | nullable | only as a pair with `fishingTimeMinMs`                   | longest fishing time with this bait                                                       |

A DTO that breaks a rule is not built (`IllegalArgumentException`), so a provider never emits it.

### 3.2 `FishingRod`

| field                    | type        | null     | validation          | semantics                                                                                                    |
| ------------------------ | ----------- | -------- | ------------------- | ------------------------------------------------------------------------------------------------------------ |
| `itemTemplateId`         | `int`       | non-null | `> 0`               | the rod's item template                                                                                      |
| `fishingTimeMs`          | `Long`      | nullable | `> 0`               | fishing time with this rod whatever the bait; `null` when the build times fishing by the bait                |
| `fishingTimeReductionMs` | `Long`      | nullable | `>= 0`              | time this rod takes off the bait's fishing time; `null` = none                                               |
| `chanceBonusPercent`     | `Double`    | nullable | `-100..100`, finite | percentage points this rod adds to the bait's catch chance; `null` = none                                    |
| `shotGrade`              | `ItemGrade` | nullable | never `EVENT`       | grade a fishing shot must have to be used with this rod, collapsed as the host matches it; `null` = no check |

### 3.3 `FishingShot`

| field            | type        | null     | validation    | semantics                                                                                           |
| ---------------- | ----------- | -------- | ------------- | --------------------------------------------------------------------------------------------------- |
| `itemTemplateId` | `int`       | non-null | `> 0`         | the shot's item template                                                                            |
| `grade`          | `ItemGrade` | nullable | never `EVENT` | the shot's grade, collapsed as the host matches it against `FishingRod.shotGrade`; `null` = any rod |

**Collapsed grade.** Builds that split a grade into sub-grades (for example `S80` / `S84` under `S`) match a shot
against a rod by the main grade. `shotGrade` and `grade` carry that main grade, so a consumer compares the two with
`equals`; `EVENT` is never sent. A shot fits a rod when either value is `null` or both are equal.

## 4. Fishing time

The fishing time is the time of one fishing attempt (one cast) with a rod and a bait. The frontend uses it for "this
stack lasts about N hours" and to rank rods:

```
fishing time = rod.fishingTimeMs
               ?? (avg(bait.fishingTimeMinMs, bait.fishingTimeMaxMs) − coalesce(rod.fishingTimeReductionMs, 0))
fishing time = max(fishing time, 1 s)
```

The fishing time is unknown (`null`) when neither the rod nor the bait carries a time. It is an estimate for display:
the host may add waits or random spread a catalog does not model.

## 5. Host provider

A host fills the catalog from what its engine actually accepts:

- baits: the items its fishing accepts in the bait slot, with the chance and timing its fishing uses for them (its
  default when it has none per bait);
- rods: the items it accepts as a fishing rod, with the timing and bonuses its fishing uses for them;
- shots: the items it charges as a fishing shot, with their grade.

The list a host accepts is the rule; an item template typed as a lure that the server does not accept is not a bait
here.

## 6. Rollout

Additive: new entities, DTOs and providers in one api release, then a gd-sync release registering the three
descriptors. The platform (`nx-gamedata`) ships its tables and consumer before a host registers a provider; until then
the platform map has no topic route for the new entity names and drops nothing it already consumes.
