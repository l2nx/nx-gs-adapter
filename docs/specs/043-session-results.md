# Session results

> Owner: @n1rmata
> Status: adapter side implemented 2026-11-04, not yet released.
> Counterparts: `nx-gameservers/docs/specs/043-sessions/results.md` (storage and reads); the host counterpart holds the
> recorder and its write points.

## Problem

The platform knows when a game session started and ended, not what happened in it: what the character gained, spent
and lost, whom it killed, who killed it. The player wants the "current session" view of the game client after the
session, for a session played in the client as well as for one the app ran. Separate statistics streams would multiply
events on the hottest paths of a server, and their sum would never match the inventory.

The session end already reaches the platform once per session, so the end carries the result. Statistics are derived
from that data on the platform.

## Requirements

> Siblings: [`013-character-core-extension`](013-character-core-extension.md) (the presence event),
> [`038-app-offline-sessions`](038-app-offline-sessions.md) (engine, table, republish),
> [`042-item-snapshot`](042-item-snapshot.md) (`ItemSnapshot`),
> [`037-schema-migrations`](037-schema-migrations.md) (the column).

**Must:**

- [done] R1. `CharacterPresenceEvent` MUST gain a nullable `result` (`SessionResult`), allowed only with
  `online = false`, on `CLIENT` and `APP` sessions alike.
- [done] R2. `CharacterPresenceEvent.charId` MUST become `characterId` in the same release, partition key included.
- [done] R3. The block MUST carry the sections `items`, `uniques`, `experience`, `kills`, `deaths`, `untracked` and the
  fields `partial`, `overflow` ([Shapes](#shapes)).
- [done] R4. The api MUST ship the closed enums `domain.item.ItemSource` and `domain.character.ExperienceSource`.
- [done] R5. A host change that no source maps to MUST reach `untracked` with the raw token in `process`.
- [done] R6. Serialized, `result` MUST fit `SessionResult.MAX_JSON_BYTES` (800,000 UTF-8 bytes). Past it a host folds
  `uniques` into `items` and counts them in `overflow`. A `result` still over it MUST be dropped, not the end: the end of
  a session never depends on its result ([Byte budget](#byte-budget)).
- [done] R7. `OfflineSessionHost` MUST gain `takeSessionResult`, called by the engine once per close of a session that
  was placed in this JVM and once per session left open by a graceful stop ([SPI](#spi)).
- [done] R8. An `APP` end MUST be identical on its first send and on every republish, `result` included, so the
  result is stored in the JSON column `result` of `l2nx_offline_sessions`, written by the close.
- [done] R9. `result` MUST survive a graceful restart of an `APP` session. The graceful stop takes the running block of every
  session left open and writes it into the same column `result`. A restore reads and clears the column and hands the
  block to the host as `OfflineSession.getCarriedResult()`; the host seeds the session's recorder with it, so the end
  carries the whole session. Nothing is written while a session runs. A restore that finds the column empty means the
  stop was not graceful: what ran before it is lost and the block carries `partial = true`, which later restores keep.
- [done] R10. A close of a session whose character was not placed in this JVM MUST have `result = null`: a stop or a
  login before the restore, an `APP_TIME_UP` without placement, a refused placement. The reason does not decide it: a
  restore that placed the character and failed in `startAction` ends with `APP_RESTORE_FAILED` and has a block.
- [done] R11. The `CharacterPresenceEvent` constructor MUST allow `activity` with `source = CLIENT`, for the native
  offline store ([Native offline store](#native-offline-store)). `activity` stays required with `APP`.
- [done] R12. `LogoutReason` MUST gain `SOLD_OUT`, the end of a native offline store that sold out. `APP_SOLD_OUT`
  stays with `APP`.

**Non-goals:**

- Statistics events or counters on the wire: the platform derives statistics from results.
- A result after an abnormal end (the server crashed): `result` is absent; the platform closes the session itself.
- Changes outside a session (a platform command on an offline character): their command results report them.
- A periodic snapshot of the running result.
- The recorder itself (hooks, aggregation, reconciliation, row ceiling): a host concern, see the host counterpart.
  This spec is what any host must fill.
- Storage: `nx-gameservers/docs/specs/043-sessions/results.md`.

### Edge cases

- **Host without a recorder:** `takeSessionResult` answers `null` and its client ends carry no `result`.
- **The same item both gained and lost under one key:** `delta` is the net, `events` the operations. A net of `0` is a
  valid row: it records that the operations happened.
- **`takeSessionResult` throws:** logged, `result = null`, the end continues.
- **A stored `result` a later version cannot parse:** one ERROR, the end goes out with `result = null`, on every
  republish alike.
- **A close the table refused:** the retry queue keeps the same `result` text, so the retry writes what the event
  carried.
- **The JVM stops before such a retry lands:** the row stays open and is restored
  ([`038`](038-app-offline-sessions.md) Engine); the end already published keeps its `result`.

## Technical design

### Overview

A host records what a session changes and hands the finished block over at the end. On a client session the host's
presence publisher puts it on the end event. On an app session the engine fetches it through the SPI, writes it into the
closing row and builds the end from that row, so a republish reproduces the same event.

### Structure

- `nx-gs-adapter-api` `kafka.events.character.result`: `SessionResult`, `SessionResultItem`, `SessionResultUnique`,
  `SessionResultExperience`, `SessionResultKill`, `SessionResultDeath`, `SessionResultUntracked`.
- `nx-gs-adapter-api` `domain.item.ItemSource`, `domain.character.ExperienceSource`.
- `nx-gs-adapter-api` `spi.provider.OfflineSessionHost.takeSessionResult`.
- `nx-gs-adapter-core` `core.appsessions`: the close, the store and `PresencePublisher`; `core.events`: the
  size backstop.
- `nx-gs-db-migrations`: changeset `0014-offline-sessions-result`.

### Shapes

Wire POJOs in the repository's form: final class, final fields, a public constructor with the checks below, builder,
`toBuilder`, value `equals` / `hashCode` / `toString`. List getters never return `null`, and a section absent on the
wire reads as empty. Gson bypasses the constructor, so a consumer re-checks what it depends on.

```java
public final class SessionResult {
    public static final int MAX_JSON_BYTES = 800_000;

    private final boolean partial;
    private final long overflow;                          // >= 0
    private final List<SessionResultItem> items;
    private final List<SessionResultUnique> uniques;
    private final List<SessionResultExperience> experience;
    private final List<SessionResultKill> kills;
    private final List<SessionResultDeath> deaths;
    private final List<SessionResultUntracked> untracked;
}

public final class SessionResultItem {
    private final long itemTemplateId;                    // positive
    private final ItemSource source;                      // required
    private final @Nullable Long sourceNpcId;
    private final @Nullable Long sourceCharacterId;
    private final @Nullable String sourceRefId;           // blank becomes null
    private final long delta;                             // signed net
    private final long events;                            // positive
}

public final class SessionResultUnique {
    private final ItemSnapshot item;                      // required
    private final ItemSource source;                      // required
    private final long delta;                             // moved count with its sign, never 0
    private final @Nullable Long sourceNpcId;
    private final @Nullable Long sourceCharacterId;
    private final @Nullable String sourceRefId;           // blank becomes null
}

public final class SessionResultExperience {
    private final ExperienceSource source;                // required
    private final @Nullable Long sourceNpcId;
    private final long exp;                               // signed
    private final long sp;                                // signed
}

public final class SessionResultKill {
    private final @Nullable Long sourceNpcId;             // exactly one of the two
    private final @Nullable Long sourceCharacterId;
    private final long count;                             // positive
}

public final class SessionResultDeath {
    private final @Nullable Long sourceNpcId;             // both null: no known killer
    private final @Nullable Long sourceCharacterId;
    private final long count;                             // positive
}

public final class SessionResultUntracked {
    private final long itemTemplateId;                    // positive
    private final long delta;                             // signed net
    private final @Nullable String process;               // raw token; blank becomes null
}
```

| Field               | Meaning                                                                                                                                       |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `items`             | Stackable items by `(itemTemplateId, source, sourceNpcId, sourceCharacterId, sourceRefId)`, `sourceRefId` is part of the key                  |
| `uniques`           | Non-stackable items, one row per item, with their state in the snapshot                                                                       |
| `experience`        | Experience and SP by `(source, sourceNpcId)`                                                                                                  |
| `kills`             | Kills by victim: an NPC template or a character                                                                                               |
| `deaths`            | Deaths by killer                                                                                                                              |
| `untracked`         | What the host saw but no source explains: a change whose `process` token maps to no source, or a difference the reconciliation found          |
| `partial`           | `true` when the block covers only part of the session: it went through a crash of the host (R9). Set by the engine; always `false` on `CLIENT` |
| `overflow`          | Rows folded away: past the host's row ceiling, or past the byte budget. `0` means nothing was folded                                          |
| `process`           | The engine's own token, verbatim, for the platform to see what to map next; `null` on a row the reconciliation found, which has no operation  |
| `sourceNpcId`       | NPC template id                                                                                                                               |
| `sourceCharacterId` | The other character of the operation                                                                                                          |

There is no `kind` or `type` field: `source`, the sign of `delta` and `sourceCharacterId` give the meaning. Location
never appears here: only inventory and equipment count as the character's items, so a change is a change of those.

A folded row loses `sourceRefId` and `sourceCharacterId` and joins the row of the remaining key.

### `sourceRefId`

An opaque string that links a row to the event already describing the operation, so the operation is not repeated
here. A consumer joins on it and never parses it. `null` when no such event exists; never empty.

| `source`        | `sourceRefId`                                                                                                  |
| --------------- | -------------------------------------------------------------------------------------------------------------- |
| `TRADE`         | `PrivateTradeFinishedEvent.tradeId`                                                                            |
| `PRIVATE_STORE` | `PrivateStorePurchaseEvent.eventId`                                                                            |
| `MAIL`          | `mailId` of the mail lifecycle event; a host may reuse mail ids, so a join also needs the session's time range |
| any other       | `null`, unless a build has a matching event to name                                                            |

### `ItemSource`

A closed enum, constant name on the wire, read leniently with `fromToken`. Sign of `delta` as the character sees it:
`+` gained, `-` lost.

| Value              | Meaning                                                                               | `delta` | Context fields                                                   |
| ------------------ | ------------------------------------------------------------------------------------- | ------- | ---------------------------------------------------------------- |
| `DROP`             | Picked up from the ground or looted from a kill; thrown on the ground                 | `+` `-` | `sourceNpcId` on loot                                            |
| `SWEEP`            | Sweeper spoil                                                                         | `+`     | `sourceNpcId`                                                    |
| `FISHING`          | Catch                                                                                 | `+`     |                                                                  |
| `QUEST`            | Given or taken by a quest                                                             | `+` `-` | `sourceNpcId` when known                                         |
| `REWARD`           | Reward of a game system with no other source: an event, an online or a daily reward   | `+`     |                                                                  |
| `CRAFT`            | Product made; materials used                                                          | `+` `-` |                                                                  |
| `CONSUME`          | Used up: potions, shots, arrows, bait                                                 | `-`     |                                                                  |
| `ENCHANT`          | Scrolls and stones spent, an item lost to a failed enchant; what the attempt returned | `+` `-` |                                                                  |
| `CRYSTALLIZE`      | Item crystallized; crystals received                                                  | `+` `-` |                                                                  |
| `DEATH_DROP`       | Dropped on death                                                                      | `-`     | killer's `sourceNpcId` or `sourceCharacterId`                    |
| `FEE`              | A fee the game charged: teleport, service, tax                                        | `+` `-` |                                                                  |
| `NPC_BUY`          | The character bought from an NPC: goods gained, payment lost                          | `+` `-` | `sourceNpcId`                                                    |
| `NPC_SELL`         | The character sold to an NPC: goods lost, payment gained                              | `+` `-` | `sourceNpcId`                                                    |
| `MULTISELL`        | Exchange by an NPC list: product gained, ingredients lost                             | `+` `-` | `sourceNpcId` when known                                         |
| `TRADE`            | Direct trade with a character                                                         | `+` `-` | `sourceCharacterId`, `sourceRefId`                               |
| `PRIVATE_STORE`    | Deal in a private store, on either side                                               | `+` `-` | `sourceCharacterId`, `sourceRefId`                               |
| `MAIL`             | Attachment received, sent, returned or paid for                                       | `+` `-` | `sourceCharacterId`, `sourceRefId`                               |
| `PARTY_DISTRIBUTE` | Loot handed out by the party rule                                                     | `+`     | `sourceCharacterId` when another member's kill or pickup gave it |
| `WAREHOUSE`        | Put into or taken from a warehouse                                                    | `+` `-` |                                                                  |
| `PET`              | Moved between the character and its pet                                               | `+` `-` |                                                                  |
| `FREIGHT`          | Freight sent or received                                                              | `+` `-` |                                                                  |
| `ADMIN`            | Changed by staff in the game                                                          | `+` `-` |                                                                  |
| `DONATE`           | Granted by a paid purchase                                                            | `+`     |                                                                  |
| `PLATFORM`         | Changed by a platform command while the session ran                                   | `+` `-` |                                                                  |

### `ExperienceSource`

A closed enum in the same form. `exp` and `sp` are signed; the sign below applies to both.

| Value           | Meaning                                                              | Sign    | Context fields |
| --------------- | -------------------------------------------------------------------- | ------- | -------------- |
| `KILL`          | Kill of an NPC                                                       | `+`     | `sourceNpcId`  |
| `QUEST`         | Quest reward                                                         | `+`     |                |
| `REWARD`        | Reward of a game system with no other source                         | `+`     |                |
| `DEATH_PENALTY` | Lost on death                                                        | `-`     |                |
| `SPEND`         | Spent: skill learning or enchanting, a clan spend, a multisell price | `-`     |                |
| `RESURRECT`     | Returned by a resurrection                                           | `+`     |                |
| `ADMIN`         | Changed by staff in the game                                         | `+` `-` |                |
| `PLATFORM`      | Changed by a platform command during the session                     | `+` `-` |                |

Both enums are closed vocabularies: a host cannot invent a token, a new value is an api release first, then the platform,
then the host. Their consumers still tolerate an unknown token: `fromToken` gives `null`, the platform logs it and keeps
the event. Neither `source` is ever `null` on the wire: what no source explains travels in `untracked`.

### Example

```json
{
  "eventId": "01973b6e-8c4a-7a10-9d3e-2f6a1c0b7e11",
  "characterId": 268481234,
  "online": false,
  "source": "APP",
  "activity": "OFFLINE_FISHING",
  "resumed": false,
  "logoutReason": "APP_OVERWEIGHT",
  "sessionId": "01973a10-5e21-7c44-8f02-3b9d4e6a2c55",
  "accountName": "player1",
  "ip": "203.0.113.7",
  "result": {
    "partial": true,
    "overflow": 0,
    "items": [
      {
        "itemTemplateId": 6411,
        "source": "FISHING",
        "delta": 37,
        "events": 37
      },
      {
        "itemTemplateId": 6520,
        "source": "CONSUME",
        "delta": -41,
        "events": 41
      },
      {
        "itemTemplateId": 57,
        "source": "MAIL",
        "sourceCharacterId": 268470001,
        "sourceRefId": "90817",
        "delta": 150000,
        "events": 1
      }
    ],
    "uniques": [
      {
        "item": {
          "itemId": 1073812,
          "itemTemplateId": 6579,
          "count": 1,
          "enchantLevel": 4,
          "attributes": { "FIRE": 120 },
          "augmentation": { "option1Id": 14561, "option2Id": 16112 }
        },
        "source": "TRADE",
        "delta": 1,
        "sourceCharacterId": 268470002,
        "sourceRefId": "01973b1f-6d02-7b8e-a4c1-77e0d2f4a903"
      }
    ],
    "experience": [
      { "source": "KILL", "sourceNpcId": 20432, "exp": 18230, "sp": 910 }
    ],
    "kills": [{ "sourceNpcId": 20432, "count": 12 }],
    "deaths": [{ "sourceCharacterId": 268470003, "count": 1 }],
    "untracked": [
      { "itemTemplateId": 1864, "delta": -5, "process": "SomeEngineProcess" }
    ]
  }
}
```

### Byte budget

The producer keeps the Kafka default `max.request.size` of 1 MiB, and the adapter sets no override. A larger record
fails with an error a later send cannot fix, and an `APP` end would loop through the republish. `MAX_JSON_BYTES` leaves
room for the rest of the event and the headers.

- **A host folds first.** Past the budget `uniques` become `items` rows and `overflow` counts them, so the numbers
  survive and only the instances are lost.
- **The adapter is the backstop.** A `result` still over the budget is replaced by `null` with one ERROR, so the end
  goes out. On `APP` the check runs before the row closes, so the stored `result` and every send agree. On `CLIENT` it runs
  on the events publisher thread, not in the wire normalizer: that one runs on the caller's game thread.
- The size counts the JSON the producer sends, before compression, which is what the producer's limit checks.

### SPI

```java
/**
 * The result of a session, or null when there is none. Called after removeFromWorld of a closing session placed in this
 * JVM, and on a graceful stop for a session left open. The answer is the host's to give up: the engine owns it from here.
 */
default @Nullable SessionResult takeSessionResult(OfflineSession session) {
    return null;
}
```

- `default` so a host without a recorder writes nothing; a host with one overrides it.
- It reads what `removeFromWorld` left: the character is out of the world by then, so the host takes the recorder of the
  session when it settles the character and keeps it by `sessionId` for this call. No world state is needed here, and the
  method neither hops to the game thread nor waits.
- The engine does not call it when `placeInWorld` did not place the character in this JVM (R10); the phase cannot tell,
  because a restore that fails after its placement closes from the same path, so the slot keeps a "placed" mark. A
  start that rolls back has no end to carry it.
- A throw is logged and gives `result = null`; the end continues.

### Close and republish

Order of the close ([`038`](038-app-offline-sessions.md) Lifecycle):

1. `stopAction`, `removeFromWorld(returnToOrigin)`.
2. `takeSessionResult`; the engine sets `partial = true` on a session whose restore found no carried block, since only it
   knows.
3. The engine serializes the block once and applies the budget.
4. `store.close(id, activeMs, endedAt, reason, resultJson)` writes the text into `result` with the rest of the close.
5. The end event is built from that text.

The first send and every republish build `result` from the same stored text, with the same `eventId` as before, so
identical means identical (R8). The retry queue carries the text too.

Only the republish and a restore read the column. `JdbcOfflineSessionStore.COLUMNS` stays without it, so reads of open
rows and the R21 reads never load a `result`, and `OfflineSession` carries only `getCarriedResult()`, set on the views a
restore hands over: the host has no use for a closed session's block. A restore reads the column before the placement
and clears it once the character is placed, so a stop between two attempts loses nothing. A republish page reads each `result` when its end is sent, not with the page, so a page of 500 ends holds one
block at a time. Queues stay bounded by event count, not bytes: a typical block is a few KB.

### Data model

`l2nx_offline_sessions` gains one column, in changeset `0014-offline-sessions-result` of `nx-gs-db-migrations`:

| Column   | Type        | Purpose                                                                                                                                   |
| -------- | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `result` | `clob` NULL | The `SessionResult` of a closed session as JSON, written by the close; `NULL` when there is none. On an open row: the running block, from a graceful stop until a restore placed the character (R9). Never written while the session runs |

Nullable, no backfill: an older row has none and its end was published long ago. An older adapter names its columns and
ignores this one. The row leaves with the retention, as every closed row does.

### Native offline store

The client command that leaves a character as an offline trader is two sessions, both with `source = CLIENT`, neither a
row of `l2nx_offline_sessions`. Source says who started a session, activity what it does, so no new `SessionSource`
exists.

1. The game session ends: `online = false`, `logoutReason = OFFLINE_PRIVATE_STORE`, with its `result`.
2. A new session opens at once, with its own `sessionId`: `online = true`, `source = CLIENT`,
   `activity = OFFLINE_PRIVATE_STORE`.
3. That session ends when the trader leaves the world, with `activity = OFFLINE_PRIVATE_STORE` and a `result` carrying
   the sales.

| The trader leaves because | `logoutReason` |

| The player logs in with that character        | `LOGOUT`      |
| --------------------------------------------- | ------------- |
| Everything is sold or bought                  | `SOLD_OUT`    |
| The paid time ran out, or a stop from the app | `KICKED`      |
| Banned                                        | `BANNED`      |
| The server stops                              | `SERVER_STOP` |

- Every exit goes through one host method that publishes the end, so no path leaves a trader with an open session.
- A restart ends the session: the server stop reports `SERVER_STOP` with its result and the host restores the trader
  under a new `sessionId`. Those starts go out once the host has sent its `ServerStartedEvent`, so they belong to the new
  generation. After that event has gone (a runtime reload of traders) a start publishes at once. `resumed` stays
  `APP`-only.
- A crash leaves no end: `result` is absent and the platform's generation close remains the fallback.
- The offline fee a restore charges is a `FEE` row of the new session.
- The fee of a sale is not a `FEE` row: a consumer reads it from `PrivateStorePurchaseEvent`.
- The end of a sold-out store can reach the platform before the `PrivateStorePurchaseEvent` with the same `sourceRefId`:
  the two travel on different paths. A consumer tolerates a `sourceRefId` it cannot join yet.

### Integration points

- **The host's presence publisher** sets `result` on a client end; the adapter's `core.events` path carries it.
- **`EventTypeRegistry`** takes the partition key from `getCharacterId()`; the key bytes do not change.
  `normalizePresence` rebuilds the event when it normalizes `ip` or `hwid`: the rebuild MUST keep `result`, and `activity`
  on `CLIENT`.
- **Platform and nx-social** read `characterId` and `charId`, stop dropping `activity` on a non-`APP` event, and treat a
  `CLIENT` row with an `activity` as a store row, not as "the player is in game"
  (`nx-gameservers/docs/specs/043-sessions/results.md`).

### Decisions

- **On the end event, not a new family.** Presence is the session event ([`038`](038-app-offline-sessions.md) R16). A
  second event would need its own ordering against the end, and a lost one would leave an end with no result.
- **Stored in the row.** R24 of `038` republishes an end from the row until Kafka acknowledges it, with a fixed
  `eventId`. A `result` held only in memory would make the republished end differ from the first send, or lose it.
- **One write at the graceful stop, none while running.** A periodic write of a hot structure would cost every session for
  the rare crash. The stop is the only moment the block must leave memory; a crash loses it, as it does for `CLIENT`, and
  `partial` says so.
- **The same column.** An open row holds the carried block, a closed row the final one; a restore clears it, so a crash
  after a restore reads as a lost block.
- **The host seeds, the engine does not merge.** The recorder already folds lines, keeps the budget and the caps; a merge
  in the engine would repeat it.
- **`partial` is set by the engine.** The engine knows what a restore found; a host would have to track the same fact.
- **Closed enums, not `WellKnown*`.** The set is a contract of the platform's storage. The raw token stays available
  in `untracked.process`, so a build-specific operation is not lost, per the rule that the wire carries the canonical
  vocabulary with the raw token alongside.
- **The size is checked in the adapter as well.** A host's folding is one implementation among many; the end of a session
  cannot depend on each of them being right.

## Rollout

Ships in the single adapter release of [`042`](042-item-snapshot.md), with `db-migrations` and `core`.

1. **Adapter:** the event field, the enums, the SPI method, changeset `0014`, `SOLD_OUT`, the constructor change.
2. **Platform and nx-social:** the `close_reason` CHECKs and `SessionCloseReason` learn `SOLD_OUT` and `APP_OVERWEIGHT`,
   the result tables, the reader for both `charId` and `characterId`, the store-row conditions on `activity`. This step
   lands before the host: a new value and the `CLIENT` + `activity` pair must be accepted before they are sent.
3. **Host:** the recorder, `takeSessionResult`, the native store sessions.
4. **Gate:** every game server of the tenant restarted on the new host; the platform and nx-social drop `charId`.

`result` itself is additive. An old host sends none and the platform reads that as no result.

## Links

- Presence event: [`013-character-core-extension`](013-character-core-extension.md)
- Engine: [`038-app-offline-sessions`](038-app-offline-sessions.md)
- Snapshot: [`042-item-snapshot`](042-item-snapshot.md)
- Platform: `nx-gameservers/docs/specs/043-sessions/results.md`, `nx-gameservers/docs/specs/093-item-snapshot-migration.md`
- Host counterpart: not linked here, this repository does not name tenant hosts.
- Epic: [l2nx/nx-platform#23](https://github.com/l2nx/nx-platform/issues/23)
