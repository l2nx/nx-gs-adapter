# Commands - item modification

> Owner: @n1rmata
>
> Counterparts: `nx-gameservers/docs/specs/088-item-modification.md` (platform commands, server settings read, item
> filters), `nx-gameservers/docs/specs/089-owner-verification.md` (owner verification of an app-phantom session),
> `nx-gamedata/docs/specs/026-enchant-attribute-via-adapter.md` (catalog storage in schema `gd`), and the reference
> host's own mapping doc.

## Problem

A player wants to improve the items of their character from the app while not in the game client: enchant an item with
a scroll, add attribute power with an attribute item, insert a life stone, re-roll or remove the augmentation. The
game mechanics and limits are the engine's: the app repeats them, it adds no rules of its own. A player clicks through
many single operations in a row (re-rolling a life stone until a wanted option lands), so the character is placed in the
world once and stays there between commands instead of being loaded per command.

The platform also has to show only what can be done: which items can be improved at all, which materials apply to a
chosen item, and the server's limits. That needs the engine's data as a catalog and the engine's server configuration
as settings, for any build (HighFive and Essence lines). Where the answer depends on an engine rule (grade collapse,
the element an armor gets, a configured blacklist), the host publishes the resolved value: the platform compares
fields, it never re-implements a rule.

Terms follow the L2NX glossary: `item-modification` (concept), `enchant-scroll` (kinds `REGULAR`, `BLESSED`,
`BLESSED_DOWN`, `SAFE`, `CURSED`, `GIANT`), `enchant-support-item`, `enchant-rate-group`, `enchant-scroll-group`,
`enchant-failure-reward`, `enchant-level`, `attribute`, `attribute-item`, `life-stone`, `gemstone`, `augmentation`,
`augmentation-target-group`, `item-option`, `app-session`, `app-phantom`, `spot-pool`; `fake-player` is renamed
`server-phantom` in the same change.

**Scope of this spec.** It fixes what the five operations share: the app-phantom session, the owner-verification
contract the host applies, the app settings keys, the catalog and the `gamesettings` settings, the runtime-sync rule,
the common command fields, the receipt protocol and the anti-duplication invariants. The five commands themselves
(R3-R5, the operation reasons and edge flows) are an outline: each command is designed in full in the scope of its
own session (its payload and result, the mapping of every engine check to a reason, its edge flows) and its section
here is rewritten then. Until that design, an outline row is not a contract; the invariants of "Anti-duplication and
abuse invariants" are.

**Core behaviour is not changed.** The operation services the host extracts from its packet handlers keep the client
path byte for byte: the same order of checks, spends and attempts, the same punishments. The app path calls the same
engine logic; what differs is only what has no client in the app path (a client-only punishment is not applied to an
app command, the app handler refuses before the engine where the client packet would have been refused by the client
UI). Where the engine's own order spends a material before a later check or clamps after the attempt, this spec records
the fact and the platform UI warns the player from the published catalog and settings; it does not reorder the engine.

## Requirements

> Siblings: [`spec.md`](spec.md) (rail: `OwnerVerified`, `COMMAND_EXPIRED`), [`catalog.md`](catalog.md) (wire entries),
> [`offline-fishing.md`](offline-fishing.md) (reference for an app-session command family),
> [`../038-app-offline-sessions.md`](../038-app-offline-sessions.md) (session engine),
> [`../030-gamedata-sync/spec.md`](../030-gamedata-sync/spec.md) (catalog entities),
> [`../040-command-receipts.md`](../040-command-receipts.md) (operation idempotency).

### Commands

- [planned] R1. Five `NxCommand`s in package `kafka.commands.item.modification`, routed by their simple names, each with
  its own result type: `EnchantItemCommand`, `AddItemAttributeCommand`, `AugmentItemCommand`,
  `SelectAugmentationCommand`, `RemoveAugmentationCommand`. They share the interface `ItemModificationCommand` and
  implement `OwnerVerified`.
- [planned] R2. Common payload of every item-modification command (the canonical fields of a world-changing command, as
  in [`offline-fishing.md`](offline-fishing.md) R2):

  | Field             | Type      | Required | Notes                                                                                                                                                            |
  | ----------------- | --------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `characterId`     | `int`     | yes      | Character whose item is modified                                                                                                                                 |
  | `itemId`          | `int`     | yes      | Inventory instance id of the item being modified                                                                                                                 |
  | `itemTemplateId`  | `int`     | yes      | Template the app showed for `itemId`; a mismatch with the live instance is `ITEM_CHANGED` (R6: instance ids are reused by the engine, the replica lags)          |
  | `masterAccountId` | `UUID`    | yes      | Master account of the initiator; stored in the session row when this command opens it                                                                            |
  | `initiatorIp`     | `String?` | no       | IP of the app request; stored in the session when this command opens it                                                                                          |
  | `ownerVerified`   | `boolean` | no       | The platform's owner attestation for this command, see "Owner verification"; absent reads as `false`                                                             |
  | `deadline`        | `Instant` | yes      | Checked first and again after every wait (lock, session start) and right before the engine call; `COMMAND_EXPIRED` once passed, before anything is touched (R12) |
  | `idempotencyKey`  | `UUID`    | yes      | Business identity of this one operation, stable across retries (R12)                                                                                             |

  Every material a command names carries both its instance id and the template id the app showed for it
  (`<material>ItemId`, `<material>ItemTemplateId`), checked the same way as the target. A command naming the same
  instance twice (`supportItemId == scrollItemId`) is refused `VALIDATION_FAILED` before anything else; the platform
  rejects it with `400` before dispatch.

- [planned] R3. Operation fields and results (outline, see "Scope of this spec"):

  | Command                     | Own fields                                                                                                                                               | Result                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `EnchantItemCommand`        | `scrollItemId`, `scrollItemTemplateId` (`int`), `supportItemId`, `supportItemTemplateId` (`Integer?`), `expectedEnchantLevel` (`int`)                    | `EnchantItemResult {sessionId, success, reason, previousEnchantLevel, enchantLevel, itemDestroyed, scrollSpent, supportSpent, received}`: `enchantLevel` after the attempt (`null` when destroyed); `reason` (`ItemModificationRefusalReason?`) set when the engine refused after a material was spent (a stored OK result, never a `FAILED` receipt); `received` the items given on failure (crystals, failure rewards), empty if none |
  | `AddItemAttributeCommand`   | `attributeItemId`, `attributeItemTemplateId` (`int`), `count` (`int`, `>= 1`: the series the engine runs; the platform caps it at the stone stack limit) | `AddItemAttributeResult {sessionId, attribute, previousPower, power, succeeded, failed, attributeItemsSpent, limitReached}`: `power` after the series, `attributeItemsSpent` = stones the engine used (`min(count, stack, steps to the limit)`)                                                                                                                                                                                         |
  | `AugmentItemCommand`        | `lifeStoneItemId`, `lifeStoneItemTemplateId` (`int`), `replace` (`boolean`)                                                                              | `AugmentItemResult {sessionId, itemOptionIds, pendingItemOptionIds, feeItemTemplateId, feeCount, adenaSpent}`: insert — `itemOptionIds` the new augmentation by slot, `pendingItemOptionIds` empty, `adenaSpent = 0`; re-roll (`replace`) — `itemOptionIds` the current augmentation (still applied), `pendingItemOptionIds` the attempt waiting for `SelectAugmentationCommand`, `adenaSpent` the attempt price (R5)                         |
  | `SelectAugmentationCommand` | `choice` (`AugmentationChoice`: `KEEP`, `NEW`), `expectedItemOptionIds` (`int[]`, the current augmentation the app shows)                                | `SelectAugmentationResult {sessionId, choice, itemOptionIds, removedItemOptionIds}`: `itemOptionIds` the augmentation the item carries after the choice; `removedItemOptionIds` the one `NEW` replaced, empty for `KEEP`                                                                                                                                                                                                                |
  | `RemoveAugmentationCommand` | `expectedItemOptionIds` (`int[]`)                                                                                                                        | `RemoveAugmentationResult {sessionId, removedItemOptionIds, adenaSpent}`                                                                                                                                                                                                                                                                                                                                                                |

  `received` lines are `{itemTemplateId, count}` in the api's existing item-line model.

- [planned] R4. The gemstone (or any fee item) is not a command field: the host takes the fee the engine requires from the
  catalog (R20) out of the character's inventory. A client packet that carries the gemstone is the engine's detail, not
  the player's choice.
- [planned] R5. Re-rolling an augmentation keeps the engine's native two-step flow. `AugmentItemCommand.replace = true` on
  an augmented item performs the engine's attempt: it pays (adena, life stone, gemstones) and returns the pending result
  (`pendingItemOptionIds`); the item still carries its current augmentation. `SelectAugmentationCommand` then applies
  the player's choice: `KEEP` discards the attempt, `NEW` replaces the current augmentation by the rolled one. On an
  augmented item `replace = false` is refused `ALREADY_AUGMENTED`; on an item without augmentation `replace` is
  ignored and the command inserts. Shared invariants of the pending attempt, fixed now:
  - **Holder.** The pending attempt lives where the engine keeps it for the client: in the engine's per-player pending
    request on the app-phantom (in memory, keyed by the item instance); the host adds no second holder. Its durable
    trace is the receipt of the `replace` command: the claim's pre-state (current augmentation) and the stored result
    (`pendingItemOptionIds`, the price). A retry of the `replace` command answers from that receipt and attempts nothing
    again.
  - **Session end drops the attempt, never silently.** The engine drops a pending attempt with the player object on any
    logout; the client path loses a paid attempt on logout, restart or closing the window. The app path loses it in the
    same cases and in no other: idle end (`APP_IDLE`), supersede, client login, kick or ban, and a restart (`SERVER_STOP`
    at boot) drop the pending attempt and the item keeps its current augmentation — the engine's `KEEP`. A pending attempt
    does not extend the session by itself: the player has `app.phantom.idle-seconds` from the last command to choose,
    and the platform shows that window from the published app settings. The drop is a logged host fact (the engine's
    item log, as for a client re-roll), and the next `SelectAugmentationCommand` answers `NO_PENDING_ATTEMPT` so the UI
    tells the player the attempt expired. An attempt never applies itself at session end: choosing for the player would be a
    new rule.
  - **One pending attempt at a time.** A second `replace` while an attempt is pending is refused `ATTEMPT_PENDING` before the
    receipt is claimed; the engine would silently discard the paid pending attempt and charge a new one, which has no
    client in the app path (the client UI cannot send it without closing the window first).
  - **Select is idempotent.** `SelectAugmentationCommand` claims its own receipt with pre-state (current augmentation,
    pending augmentation, `choice`); `NEW` journals the outcome before the first mutation and performs remove + set
    under the inventory write lock in one step; a retry answers the stored result. `expectedItemOptionIds` differing
    from the live augmentation is `ITEM_CHANGED`; a select whose pending attempt does not match the item (the engine
    checks the pending request's item and previous augmentation id) is `NO_PENDING_ATTEMPT`.
  - **Select without a pending attempt** (none, expired with the session, already selected) is `NO_PENDING_ATTEMPT`
    (`INVALID_STATE`), refused before `claim` when no receipt exists for the key; a retry of an already applied select
    is answered from its receipt (`REPLAY_OK`), not refused.
- [planned] R6. Every check, consumption, chance and failure consequence is the engine's own, reached the same way the
  client packets reach it. The host adds no rule and drops none. Before the engine, the handler runs **data pre-checks**
  that only refuse (never mutate): the target and every material are in the character's inventory or paperdoll with the
  template id the command names (`ITEM_NOT_FOUND`, `MATERIAL_NOT_FOUND`, `ITEM_CHANGED`), the expected state matches
  (`expectedEnchantLevel`, `expectedItemOptionIds` → `ITEM_CHANGED`), the counts suffice (life stone, gemstones, adena
  → `NOT_ENOUGH_FEE_ITEMS`, `NOT_ENOUGH_ADENA`), `ALREADY_AUGMENTED` / `NOT_AUGMENTED` / `ATTEMPT_PENDING`, and the catalog
  applicability the client UI would have enforced (scroll target and grade, life stone target group →
  `SCROLL_NOT_APPLICABLE`, `LIFE_STONE_NOT_APPLICABLE`). These make the engine's refusal branches that sit after a spend
  unreachable for honest input; when one is reached anyway the result is stored honestly (`EnchantItemResult.reason`,
  `scrollSpent`). A build that lacks an operation (it loads no attribute data) answers `UNSUPPORTED_COMMAND` from the
  rail: the host registers no handler for it.
- [planned] R7. A success path calls `ctx.sync().requestNow(item, pk)` for every item instance the operation touched (the
  modified item, the spent material and fee stacks, the received items, a destroyed item) and `character` for its
  owner ([`catalog.md`](catalog.md) "Sync triggers"). A destroyed item needs no tombstone by hand: the triggered entity
  cycle sees the missing row and publishes `DELETED`.

### App-phantom session

- [planned] R8. `ActivityType.OFFLINE_PHANTOM`: the activity of an `app-phantom` (a character placed in the world by an
  app session; it stands without a client and carries out the app's actions). Session `source` is always `APP`. The
  current action is the activity usage's `subtype`: `ITEM_MODIFICATION` (later `SKILL_MODIFICATION`). A new value in
  `ServerOnlineBucket` (`OFFLINE_PHANTOM`) per [`../011-events-online-snapshot.md`](../011-events-online-snapshot.md).
  An activity row is published only while the character has an open session: a tick between the engine forgetting the
  session and the character leaving the world publishes nothing.
- [planned] R9. There is no start or stop command. The first item-modification command of a character opens the session;
  every next command of any app-phantom action extends it. The handler never holds the character mutation lock across
  the session start or across any engine wait; the order is:
  1. without the lock: `deadline` → payload validation → state refusals (`app.item-modification.enabled`, a session of
     another type → `IN_PRIVATE_STORE` / `IN_OFFLINE_FISHING`, a session that is ending → `CHARACTER_BUSY`) → data
     pre-checks (R6: on the live phantom when one stands, otherwise on the character loaded under a short offline lock
     and discarded again, as the fishing start checks the rod) → receipt `claim` (R12) → session: an open
     `OFFLINE_PHANTOM` session is extended with `NxAppSessions.touch(sessionId)`; no session: the shared start rules
     (recent logout, restart window, sanctions, state, the phantom limits below) and `engine().start(request)` awaited
     within `deadline`, placing the character on a free spot of the shared pool (R16);
  2. the character lock is taken only around the operation itself (the command lock timeout → `CHARACTER_BUSY`): under
     it the handler re-checks that the touched session is still the character's active one and not ending, hops to
     the game thread for the engine call, and releases;
  3. result into the receipt, sync request.

  Two first commands of one character at once are serialized by the engine (one `start` wins, the other gets
  `CHARACTER_BUSY` with the session and `touch`es it: that is "the second extends the first"). `touch` returning `false`
  or a session with `endedAt` set is `CHARACTER_BUSY` (receipt `FAILED`, R12): the command never waits for a session to
  end, the platform retries with the same `requestId` and the retry opens a new session. A refusal that does not reach
  the engine operation neither opens nor extends a session: `touch` comes after the pre-checks. **Phantom limits** (host
  start rules, keys in R15): one `OFFLINE_PHANTOM` session per master account, per game account and per hwid by default
  (`ALREADY_ACTIVE` + `scope` from `AlreadyActiveScope`: `CHARACTER`, `MASTER_ACCOUNT`, `ACCOUNT`, `HWID`, the first
  limit hit, the same vocabulary as [`offline-fishing.md`](offline-fishing.md)); a `null` hwid snapshot limits nothing.

- [planned] R10. Idle end. `NxAppSessions.touch(sessionId)` moves the idle deadline to `now + app.phantom.idle-seconds`
  (read on every use, so a hot reload applies from the next `touch`) and returns whether it extended the session; it
  returns immediately from any thread and never queues behind a slot task (`false` once the session is ending). With no
  command until the deadline the engine ends the session with `LogoutReason.APP_IDLE`. An `OFFLINE_PHANTOM` session
  always returns the character to its origin: the return threshold does not apply to the type. The session has no
  planned duration. The idle deadline lives in memory only (R13).
- [planned] R11. The app-phantom yields. A start of another app session type (offline fishing, app private store) for a
  character with an open `OFFLINE_PHANTOM` session first calls `NxAppSessions.supersede(characterId)` — before it takes
  any character lock of its own — and waits, within its `deadline`, until the engine has ended that session with
  `LogoutReason.APP_SUPERSEDED` and removed the character; only then it runs its own start checks, which see an offline
  character. The recent-logout rule does not apply, it is about a client logout. The yield is a property of the session
  type (`OFFLINE_PHANTOM` is preemptible), not a host rule. An item-modification command in flight when the supersede
  lands is serialized by the operation lock: the operation re-checks the session under the inventory write lock and
  refuses `CHARACTER_BUSY` instead of mutating a character that is leaving. A standing phantom seen by another type's
  start is `CHARACTER_BUSY` (retryable), never `IN_PRIVATE_STORE`.
- [planned] R12. Operation idempotency and receipts. A repeated command with the same `idempotencyKey` returns the stored
  result and spends nothing again: the handler claims a receipt ([`../040-command-receipts.md`](../040-command-receipts.md))
  before the session step and the engine call. `REPLAY_OK` neither opens nor extends a session. The platform derives the
  key as `IdempotencyKeys.derive(requestId, "<ENCHANT_ITEM | ADD_ITEM_ATTRIBUTE | AUGMENT_ITEM | SELECT_AUGMENTATION |
REMOVE_AUGMENTATION>", serverId, characterId, itemId, <material instance ids>, <count | replace ? 1 : 0 | choice>)`
  from a fresh UUIDv7 `requestId` (`RequestIds.requireFreshUuidV7`): a different intent is a different key and an honest
  second operation, never another operation's result; the per-command prefix makes a `requestId` reused across commands
  two independent operations. The protocol is "Receipt protocol" below: pre-state in the claim, the outcome journaled
  before the first mutation, every refusal after `claim` and before the first mutation closes the receipt as `FAILED`,
  `deadline` passed after `claim` is `FAILED` + `COMMAND_EXPIRED`, a boot resolver settles open rows. The session's own
  key is fresh, never the key of a command: a retry of a command whose session has ended meanwhile is still answered
  from the receipt, and a new command opens a new session.
- [planned] R13. A restart does not restore `OFFLINE_PHANTOM` sessions: when the engine boots, before it accepts any
  command, it ends each open one with `LogoutReason.SERVER_STOP` and returns the character to its origin, without a
  loaded world (stored rows only). The row's end is `startedAt + activeMs` as of the last flush, not the boot time. A
  standing app-phantom holds nothing worth restoring (a pending augmentation attempt is dropped as on a client restart, R5).
- [planned] R14. Commands that act on online or offline characters (mail send, accept and read, private store buy and
  sell, chat) treat an app-phantom as an online character, as they treat an app fisher. A mail returned or expired to a
  phantom sender takes the online branch under the sender's mail lock, serialized with the item-modification operation
  (verified in the reference host).

### Owner verification

- [planned] R15. `ownerVerified` travels on every command; for an app-phantom session the host also keeps it in the
  session row next to `masterAccountId`: the opening command sets it, a later command carrying `true` raises it, `false`
  or absent never lowers it, and every command of the session is judged by the stored value. So a failed platform
  refresh on one command does not doom the session: the next command that verifies raises it. The meaning is the one of
  `OwnerVerified` in [`spec.md`](spec.md) R28: `true` bypasses the item lock of the player's device (hwid item lock)
  for **every** item-modification operation (enchant, attribute, augment, select, remove), exactly as for buy-now;
  `false` with a locked item is `ITEM_LOCKED`. Making the attestation cheap across a series is the platform's business
  (one master-account refresh per series, reused while the series lives); the mechanism, the refresh budget and the
  edge cases live in `nx-gameservers/docs/specs/089-owner-verification.md`. Nothing new travels on the wire.

### App settings

- [planned] R16. New keys of `l2nx-app.properties` (catalog, reference file and `configuration.md` en / ru in the same
  change):

  | Key                                  | Default  | Allowed                | Meaning                                                                                                         |
  | ------------------------------------ | -------- | ---------------------- | --------------------------------------------------------------------------------------------------------------- |
  | `app.phantom.idle-seconds`           | `180`    | 30-1800                | Idle time after which an app-phantom session ends (R10)                                                         |
  | `app.phantom.max-per-master-account` | `1`      | int `>= 1`             | Open `OFFLINE_PHANTOM` sessions one master account may hold on this server (`ALREADY_ACTIVE`, `MASTER_ACCOUNT`) |
  | `app.phantom.max-per-account`        | `1`      | int `>= 1`             | The same per game account (`ACCOUNT`)                                                                           |
  | `app.phantom.max-per-hwid`           | `1`      | int `>= 1`             | The same per hwid snapshot (`HWID`); a `null` snapshot is never counted                                         |
  | `app.item-modification.enabled`      | `false`  | boolean                | Item modification on this server; `false`: every item-modification command is refused                           |
  | `app.session.spot-pool`              | empty    | list of pool names     | Spot pools of every session type without a pool of its own (`OFFLINE_PHANTOM` today)                            |
  | `app.session.spot-order`             | `RANDOM` | `RANDOM`, `FIRST_FREE` | How a free spot is picked across the listed pools                                                               |

  The three `max-per-*` keys count open `OFFLINE_PHANTOM` sessions; the host enforces them in its start rules (a game
  rule), the adapter only owns the keys and their validation. `OFFLINE_PHANTOM` joins `AppRules.SESSION_TYPES` without
  an `enabled` key of its own: its `AppSessionTypeSettings.enabled` is published as "any of its actions is enabled".
  `AppSettings` gains `itemModificationEnabled`, `phantomIdleSeconds` and the three limits. `app.item-modification.enabled
= true` with an empty `app.session.spot-pool` is a file validation error, as for fishing. A kill switch while a phantom
  stands (`enabled` flipped to `false`) refuses every command with `FEATURE_DISABLED` and lets the phantom idle out; no
  forced end.

- [planned] R17. Spot pools. A pool-name key takes a comma-separated list (each name 1-32 characters of `a-z`, `0-9`,
  `_`; a repeated name is a validation error). `app.fishing.spot-pool` takes a list too: `AppRules.fishingSpotPool`
  (`String`) becomes `fishingSpotPools` (`List<String>`), an SPI break listed in the rollout. A pool listed in
  `app.session.spot-pool` may neither be the private-store pool nor appear in `app.fishing.spot-pool`: the spot key
  `<pool>:<spotId>` is unique across types only because pool names differ, so the overlap is a file validation error.
  `RANDOM` picks any free spot of the union of the listed pools; `FIRST_FREE` walks the pools in list order and, inside a
  pool, the lowest spot id. A listed pool missing from the host's data is logged at `ERROR` and skipped; no pool left:
  the type is refused with `FEATURE_DISABLED`. A type with a zone of its own (fishing) never falls back to the shared
  pool. The set of spots already tried during one start is keyed by the full spot key. The session row stores the pool
  of the spot it got. A full pool is `NO_FREE_SPOT` with a host log line naming the pool; the platform counts it.

### Catalog (gd-sync)

The catalog is the engine's loaded data, published per server; `null` means the build has no such field. Where the
engine decides applicability with a rule, the host publishes the resolved value (R19, R20, R21), so platform filters
compare fields only. Entities and fields below; the living wire contract is `030-gamedata-sync/enchant-contract.md`
and `attribute-contract.md` (new), and `augmentation-contract.md` (changed), written with the code. Every entity has a
numeric `pk` (`GameDataSyncEvent.pk` is a `long`; the platform's receipts and `count` check need one number per
record): an entity the engine keys by a name or a tuple is published under a stable host integer id with the name as a
field, or as an aggregate on the id its rows share. A build without the feature registers no provider; a registered
provider with an empty catalog returns an **empty** collection (`count = 0`, which the platform does not reconcile);
`null` only while the host is not ready (030 §3.7).

- [planned] R18. Enchant entities:

  | Entity                 | Key (`pk`)        | Fields                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
  | ---------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
  | `enchantscroll`        | `itemTemplateId`  | `kind` / `kindRaw` (`REGULAR`, `BLESSED`, `BLESSED_DOWN`, `SAFE`, `CURSED`, `GIANT`); `target` (`WEAPON`, `ARMOR`: accessories are enchanted by armor scrolls in both lines); `grade` (`ItemGrade`, required; `NONE` = items without a grade), compared with the item's `enchantGrade`; `minEnchantLevel` (inclusive; `null` when the engine's default sentinel `0`), `maxEnchantLevel` (exclusive: the scroll applies while the level is below it; `null` = no limit of its own, including the engine's default sentinel); `safeEnchantLevel`; `bonusRatePercent`; `stepMin`, `stepMax`, `stepChancePercent`; `onFailure` (`DESTROY`, `RESET`, `DECREASE`, `KEEP`) with `failureLevelLoss` (`DECREASE`) or `failureResetLevel` (`RESET`); `onSuccessLevelChange` (`CURSED` lowers on success); `scrollGroupId`; `items`: `{itemTemplateId, scrollGroupId}` (explicit list, each row with its own group; empty = by target and grade); `excludedItemTemplateIds`: items another scroll's explicit list took from this one's target-and-grade set (`null` where the build has no such rule) |
  | `enchantsupportitem`   | `itemTemplateId`  | `target`, `grade`, `minEnchantLevel`, `maxEnchantLevel` (as for the scroll); `chanceBonusPercent` with `chanceBonusMode` / `chanceBonusModeRaw` (canonical `ADD`; a build's own mode stays raw with `null` mode); `compatibleKinds` (scroll kinds it pairs with; `null` = the build has no pairing rule, a non-empty list is exact); `stepMin`, `stepMax`, `stepChancePercent` (filled only by a build whose support changes the step); `itemTemplateIds`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | `enchantrategroup`     | `id` (host `int`) | `name` (the engine's group name, a field, not the key); `rates`: `{enchantLevelFrom, enchantLevelTo, chancePercent}` (`0 <= p <= 100`: `0` is a real "does not advance" value); a level past the last range takes the last range's chance, as the engine does                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
  | `enchantscrollgroup`   | `id` (host `int`) | `name`; `rules`: ordered `{equipSlots[]?, magicWeapon?, itemTemplateIds[]?, enchantRateGroupId}`; the first matching rule picks the rate group of an item                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | `enchantitemoption`    | `itemTemplateId`  | `levels`: `{enchantLevel, itemOptionIds}` (into the `itemoption` catalog)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | `enchanthpbonus`       | `0` (singleton)   | `grades`: `{grade (ItemGrade, the collapsed grade the engine looks up), bonuses[] (index = enchant level - 1; a level past the list takes the last value, as the engine does), fullBodyMultiplier}`; grades the engine never looks up (sub-grades it collapses) are not published                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
  | `enchantfailurereward` | `itemTemplateId`  | `levels`: `{enchantLevel, items: {itemTemplateId, count, chancePercent}}`, given when the item is destroyed at that level                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

  A scroll's `kind` and failure outcome are given as the engine resolves them from one and the same branch of its own
  data (the platform never infers one from the other, and the host never derives `kind` from a field the engine does
  not use for the outcome). Duplicates in the engine's data are resolved per entity as the engine loads them (first or
  last wins), stated in the contract doc per entity. Rates carry no personal bonus: the final chance is `rate +
bonusRatePercent + support bonus + character enchant stat`, capped at 100.

- [planned] R19. Item template changes: `ItemSkillRef` gains `enchantLevel` (`Integer?`), the enchant level from which an
  item skill of type `ENCHANT` is active (part of the skill ref's identity: the same skill at two levels is two refs).
  `ItemTemplate.upgrade` gains:
  - `maxEnchantLevel` (`Integer?`): the item's own enchant limit where the build has one;
  - `enchantGrade` (`ItemGrade?`): the grade the engine compares a scroll's grade with (sub-grades collapsed, for
    example `S80` to `S`); the raw `grade` stays as it is;
  - `augmentable` (`Boolean?`): the template belongs to at least one augmentation target group after every
    configured restriction.
- [planned] R20. `attributeitem`, key `itemTemplateId`: `attribute` (`Attribute`, FIRE..DARK); `weaponAttribute`,
  `armorAttribute` (the element it gives a weapon and an armor, as the engine resolves it; `null` where it cannot be
  used); `type` / `typeRaw` (`STONE`, `STONE_SUPER`, `ROUGH_ORE`, `CRYSTAL`, `CRYSTAL_SUPER`, `JEWEL`, `ENERGY`);
  `maxLevel` (attribute level the type reaches); `powerPerUse`: `{weaponFirst, weaponNext, armor}`. The entity carries
  no power limit: the limit depends on the item's grade, the type and the server's configuration, and its single
  source is `gamesettings.attribute.limits` (R22). A build that loads no attribute data registers no provider for the
  entity, so the platform simply has no rows; the same holds for every entity of R18-R21 a build lacks.
- [planned] R21. Augmentation contract changes:
  - `AugmentationOutcome.slot` is the ordinal option slot `1..3` (the Essence line has three option slots; the former
    "low / high 16 bits" wording is dropped); `ItemAugmentationDbDto` gains `option3Id` (`Integer?`) and
    `lifeStoneItemTemplateId` (`Integer?`, the stone that produced the augmentation where the build keeps it) in the
    same api release.
  - The members of an augmentation target group are published after every configured restriction (a blacklisted item
    is not a member).
  - New entity `augmentationfee`, `pk = lifeStoneItemTemplateId`, an aggregate per life stone: `fees`:
    `{augmentationTargetGroupId, itemGrade?, feeItemTemplateId?, feeCount, feeAdena}`. `itemGrade` is the **raw**
    template grade the engine prices by (sub-grades included where the engine distinguishes them); a `null` `itemGrade`
    matches any grade; the most specific row wins. Rows the engine itself would refuse (a zero adena fee where the
    engine requires one) are not published.
  - New entity `augmentationcancelfee`, `pk = itemTemplateId`, an aggregate per template: `prices`:
    `{enchantLevelFrom, enchantLevelTo, lifeStoneItemTemplateId?, cancelAdena}`, the price of removing the augmentation
    of that item at that enchant level (per life stone where the build prices by stone). The host resolves its own
    price rules (grade, crystal count, multipliers) into this table.

### Server settings (settings-sync)

- [planned] R22. New settings entity `gamesettings` (`GameSettings`) on `<tenant>.gs.settings.sync`, one full snapshot
  per server, newest wins, next to `appsettings`. A host provider maps its engine configuration; `null` means the build
  has no such setting. `updatedAt` is the host's start time (the snapshot is built once per start); the snapshot is
  published on connect and after every reconnect, as `appsettings` is, and the platform deduplicates by content.

  | Group          | Fields                                                                                                                                                                                                                                                                                                                                                                                                                            |
  | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `enchant`      | `maxWeapon`, `maxArmor`, `maxAccessory`: the server-wide caps **as the engine applies them**, resolved (a build that enchants accessories by the armor cap publishes `maxAccessory` = that cap); `null` = no server-wide cap (scroll and item limits apply). Whether the engine refuses at the cap or clamps the attempt to it while spending the scroll is the engine's; the platform warns the player at the cap from these values |
  | `attribute`    | `maxWeaponAttributes`, `maxArmorAttributes`; `oppositePairs`: `{attribute, opposite}` (an armor never holds both); `chances`: `{grade?, type, target?, chancePercent}`; `limits`: `{grade, type, target, maxPower}` — the **only** power-limit table: the host expands grade × type × target with its configuration applied (`0` = the type cannot be used on that grade)                                                         |
  | `mail`         | `feeAdena`, `feeAdenaPerAttachment`                                                                                                                                                                                                                                                                                                                                                                                               |
  | `offlineStore` | `feeItemTemplateId`, `feeCount`, `feePeriodHours`                                                                                                                                                                                                                                                                                                                                                                                 |

### Runtime sync

- [planned] R23. The `stateStamp` of every runtime row keyed by a character (character, activity usage, effects) MUST
  change on every entry of the character into the world: a client login, an app-session placement, a restore. The
  entry counter is incremented before the character becomes visible to the runtime snapshot (before spawn and the online
  flag), so one entry republishes once, and a check-load that never enters the world does not touch it. An activity
  usage pack that is equal byte for byte before and after a quick leave and return is otherwise never republished, while
  the character's offline tombstone has already stopped the platform's rows. Documented in
  [`../006-runtime-sync.md`](../006-runtime-sync.md) and [`../017-character-runtime-activity.md`](../017-character-runtime-activity.md).

### Reason codes

> Outline: the shared session reasons are final; the operation reasons are a first list, completed by each command's
> own design.

Refusals carry a stable `reason` in `CommandProblem.extensions`; the host never sends player-facing text. The only
extensions item-modification uses are `reason`, `retryAt` and `scope`.

| Reason                                                                                                                           | Status           | When                                                                                                                                |
| -------------------------------------------------------------------------------------------------------------------------------- | ---------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `FEATURE_DISABLED`                                                                                                               | `INVALID_STATE`  | `app.item-modification.enabled = false`, or no spot pool is usable                                                                  |
| `CHARACTER_ONLINE`, `RECENTLY_LOGGED_OUT` (+ `retryAt`), `RESTART_SOON`, `CHARACTER_BANNED`, `CHARACTER_JAILED`, `DEAD`, `KARMA` | `INVALID_STATE`  | shared start rules, only when the command opens the session                                                                         |
| `ALREADY_ACTIVE` (+ `scope`)                                                                                                     | `INVALID_STATE`  | a phantom limit of R9 / R16 is hit when the command opens the session                                                               |
| `IN_PRIVATE_STORE`, `IN_OFFLINE_FISHING`                                                                                         | `INVALID_STATE`  | the character runs another app session                                                                                              |
| `NO_FREE_SPOT`                                                                                                                   | `INVALID_STATE`  | every spot of the shared pool is taken                                                                                              |
| `CHARACTER_BUSY`                                                                                                                 | `INVALID_STATE`  | the session is ending or just ended, the operation lock timed out, or the session changed under the operation; retryable            |
| `COMMAND_EXPIRED`                                                                                                                | `INVALID_STATE`  | `deadline` passed, at the first check or after a wait (rail)                                                                        |
| `ITEM_NOT_FOUND`, `MATERIAL_NOT_FOUND`                                                                                           | `NOT_FOUND`      | the item or the material is not in the character's inventory or paperdoll                                                           |
| `ITEM_CHANGED`                                                                                                                   | `INVALID_STATE`  | the live instance differs from what the app showed (`itemTemplateId`, `expectedEnchantLevel`, `expectedItemOptionIds`)              |
| `ITEM_IN_STORE`, `ITEM_IN_USE`, `ITEM_LOCKED`                                                                                    | `INVALID_STATE`  | as for `DeleteItemCommand`; `ITEM_LOCKED` only with `ownerVerified = false` (R15)                                                   |
| `ITEM_NOT_ENCHANTABLE`, `SCROLL_NOT_APPLICABLE`, `SUPPORT_NOT_APPLICABLE`, `ENCHANT_LIMIT`                                       | `INVALID_STATE`  | enchant checks of the engine (`ENCHANT_LIMIT`: the scroll's own `maxEnchantLevel` or the item's)                                    |
| `ITEM_NOT_ATTRIBUTABLE`, `ATTRIBUTE_ITEM_NOT_APPLICABLE`, `ATTRIBUTE_CONFLICT`, `ATTRIBUTE_LIMIT`, `MAX_ARMOR_ATTRIBUTES`        | `INVALID_STATE`  | attribute checks of the engine                                                                                                      |
| `ITEM_NOT_AUGMENTABLE`, `LIFE_STONE_NOT_APPLICABLE`, `LIFE_STONE_LEVEL`, `ALREADY_AUGMENTED`, `NOT_AUGMENTED`                    | `INVALID_STATE`  | augmentation checks of the engine                                                                                                   |
| `ATTEMPT_PENDING`, `NO_PENDING_ATTEMPT`                                                                                                | `INVALID_STATE`  | a re-roll while one waits for a choice; a select without a pending attempt (R5)                                                        |
| `NOT_ENOUGH_FEE_ITEMS`, `NOT_ENOUGH_ADENA`                                                                                       | `INVALID_STATE`  | the fee, the attempt price or the cancel price cannot be paid                                                                          |
| `OUTCOME_UNKNOWN`                                                                                                                | `INTERNAL_ERROR` | the operation may have mutated and its receipt is still open (a timeout, a write the engine reported as failed); retry the same key |

`VALIDATION_FAILED` carries no reason of this table (payload shape, the same instance named twice). New values join
`AppSessionRefusalReason` (shared) or a new `ItemModificationRefusalReason` (operation checks). The full mapping from
engine checks to reasons is the host's, written with each command's design.

### Edge-flow table

| Situation                                                                             | Result                                                                                                                                                                                           |
| ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Repeated command, same `idempotencyKey`                                               | the stored result, nothing spent again, the session untouched (R12)                                                                                                                              |
| Same `requestId`, different item, material, `count` or `choice`                       | a different key: an honest second operation (R12)                                                                                                                                                |
| Two POSTs with one `requestId` at once (two devices)                                  | one `OK`, the other `OUTCOME_UNKNOWN` (`IN_FLIGHT`), a later retry the stored result                                                                                                             |
| Two first commands of one character at once                                           | the engine serializes the start; the loser `touch`es the session the winner opened and runs after it (R9)                                                                                        |
| Command arrives while the session is ending by idle                                   | `touch` returns `false`; the command is refused `CHARACTER_BUSY`, its receipt is `FAILED`, the retry opens a new session (R9)                                                                    |
| Command refused before the engine (`ITEM_NOT_FOUND`, `NOT_AUGMENTED`, `ATTEMPT_PENDING`) | no session is opened or extended; the receipt, if claimed, is `FAILED` (R6, R12)                                                                                                                 |
| Second phantom of the same master account, game account or hwid                       | `ALREADY_ACTIVE` with the `scope` of the first limit hit (R9)                                                                                                                                    |
| Start of offline fishing or an app store during an app-phantom                        | `supersede` before any lock ends the app-phantom with `APP_SUPERSEDED`, the new session starts (R11)                                                                                             |
| Item-modification command during offline fishing or an app store                      | refused `IN_OFFLINE_FISHING` / `IN_PRIVATE_STORE`                                                                                                                                                |
| Session ends (idle, supersede, login, kick) while an operation is on the game pool    | the operation sees the ending session under the inventory write lock before any mutation → `CHARACTER_BUSY`; or the exit waits for the lock the operation holds — no item row is lost or doubled |
| The player logs in with the client, no command in flight                              | the session ends with `APP_LOGIN`, as every app session                                                                                                                                          |
| The player logs in during an in-flight command                                        | the client is refused once ("try again"), the next attempt closes the session with `APP_LOGIN`                                                                                                   |
| Session ends with a pending augmentation attempt                                         | the attempt is dropped (the engine's `KEEP`), the item keeps its augmentation, the drop is logged; the next select answers `NO_PENDING_ATTEMPT` (R5)                                                   |
| `app.item-modification.enabled = false` while a phantom stands                        | every command `FEATURE_DISABLED`; the phantom idles out, no forced end (R16)                                                                                                                     |
| The server restarts with an open app-phantom                                          | the engine ends it with `SERVER_STOP` at boot, the character goes back to its origin, a pending attempt is dropped (R13)                                                                            |
| Timeout or crash while a material is being spent                                      | `OUTCOME_UNKNOWN`; a retry with the same key answers `IN_FLIGHT` until the receipt settles (the task finishes or the boot resolver closes it), then the stored result ("Receipt protocol")       |
| The engine reports a failed write (augmentation, element rows)                        | `OUTCOME_UNKNOWN`, the receipt stays open with an alert; never a false `OK`                                                                                                                      |

## Technical design

### Wire shape

```
kafka.commands.item.modification
  ItemModificationCommand            (interface: characterId, itemId, itemTemplateId, OwnerVerified, deadline, idempotencyKey)
  EnchantItemCommand / EnchantItemResult
  AddItemAttributeCommand / AddItemAttributeResult
  AugmentItemCommand / AugmentItemResult
  SelectAugmentationCommand / SelectAugmentationResult
  RemoveAugmentationCommand / RemoveAugmentationResult
  model/ItemModificationRefusalReason, model/AugmentationChoice
kafka.sync.gd.enchant.{scroll, supportitem, rategroup, scrollgroup, itemoption, hpbonus, failurereward}
kafka.sync.gd.attributeitem
kafka.sync.gd.augmentation.{fee, cancelfee}
kafka.sync.settings.gamesettings    GameSettings (+ groups)
spi/provider                        one provider per new gd entity, GameSettingsProvider
spi/capability/NxAppSessions        + boolean touch(UUID sessionId), + Optional<OfflineSession> supersede(int characterId)
```

### Engine changes (038)

- `AppSessionStartRequest` accepts `OFFLINE_PHANTOM`; it carries no `plannedMs` and no idle value: the idle length is
  `rules.current().phantomIdleSeconds()`, read on every arming and every `touch`.
- No schema change: the idle deadline is kept in memory, since a restart never restores the type (R13). A slot keeps
  `volatile long idleDeadlineMs` and an `AtomicBoolean ending`; `close()` sets `ending` first. The idle timer compares
  `now` with the deadline and re-arms itself when the deadline moved.
- `touch` is a CAS on the deadline guarded by `!ending && phase == RUNNING`; it returns `true` when it moved the
  deadline, `false` otherwise, immediately and from any thread. It never runs as a slot task: a slot task would queue
  behind a `close` that waits for the character lock, and the slots share one bounded executor, so one hanging close
  would stall every session of the server.
- `supersede(characterId)` ends the character's preemptible session, if any, with `APP_SUPERSEDED` through an ordinary
  close (`removeFromWorld(session, true)`), queued on the session's slot so that it also catches a start still in flight
  (`STARTING` → it closes the session once `RUNNING`), and returns the closed session once it has landed, empty with no
  such session. The host calls it before it takes any character lock of its own (R11).
- **Invariant:** the host never holds a character mutation lock while waiting on an engine future; the only exception
  is the parked login holder (the login thread that waits for the session's exit while holding the lock for it).
- A preemptible type always returns to the origin on its end; the return threshold applies to the other types only.
- At boot, before the engine accepts a command, open sessions of a non-restorable type end with `SERVER_STOP`; the
  row's `endedAt` is `startedAt + activeMs`, not the boot time (a downtime is not activity).

### Host semantics (summary)

The handler of every command, in this order: `deadline`; payload validation; state refusals (enabled key, another
session type, an ending session); data pre-checks on the live phantom or on a character loaded under a short offline
lock and discarded (R6); receipt `claim` with the pre-state; session (`touch` or start, no character lock held across
it); `deadline` again; the operation under the character lock and, inside the engine, under the inventory write lock in
the packet's lock order, where it first re-reads the character's active session (`null` or ending → `CHARACTER_BUSY`
without a mutation) and then runs check → attempt → journal → spend → apply as one synchronous step on the game pool; the
engine's refusal mapped to a reason; `markOk`; sync request. Operation refusals of the engine never trigger the engine's
client-only punishment path ("illegal action" counters that kick or ban after repeated client cheats): an app refusal
is a refusal, not a cheat, and the counter stays on the packet path for what a client cannot physically send. The
host's mapping doc names the engine entry points.

### Receipt protocol

Receipts are the only idempotency guard of the host: the host's in-memory dedup by correlation id is inert here, since
the platform mints a fresh correlation id per dispatch. Per command:

1. `claim(key, command, claimJson = pre-state)`: `itemId`, `itemTemplateId`, `enchantLevel`, augmentation id /
   `itemOptionIds`, element values of the target; `objectId` and `count` of every material and fee stack; adena. The
   exact composition is fixed in each command's session.
2. State refusals come before `claim`; after `claim` only engine checks remain, and any refusal before the first mutation
   is `failClean` (`FAILED`) so that a retry with the same `requestId` reopens the receipt instead of meeting `IN_FLIGHT`.
   A `deadline` that passed after `claim` is `FAILED` + `COMMAND_EXPIRED`.
3. The outcome (the attempt, the stones used, the new augmentation) is computed before the first mutation and journaled with
   `markChargedOwnTx(key, chargedAdena, claimJson + outcome)` — `chargedAdena = 0` for operations without a price, the
   attempt or cancel price for augmentation — **before** the first spend; `markOk(con, key, charged, result)` after the last
   apply. A spend that fails after the journal (adena debited, a material gone) is rolled back where the engine itself
   attempts back (the re-roll refunds the adena when a material cannot be destroyed) and recorded `markRefundedOwnTx`.
4. `ItemModificationReceiptRecovery` is registered in the host's boot resolvers for all five commands: a `STARTED` row
   spent nothing → `failStale` (retry allowed); a `CHARGED` row → compare the item rows with the pre-state and the
   journaled outcome (the engine writes levels, counts, augmentation and element rows synchronously, so the database is
   authoritative at boot), apply the missing steps as an offline mutation and `markOk` with the reconstructed result;
   ambiguous → `OUTCOME_UNKNOWN` final with an alert.
5. A write the engine reports as failed (augmentation or element rows) is `OUTCOME_UNKNOWN` with the receipt left open,
   never a false `OK`: the engine's own store does not rewrite those tables, so a swallowed error would be a free
   rollback on the next load.

### Anti-duplication and abuse invariants

Contract-level rows (host classes and lines are in the host's mapping doc). Format: vector → expected behaviour →
enforced where → test.

**EnchantItem**

| Vector                                                              | Expected behaviour                                                                                                                                                                   | Enforced where                                    | Test                                         |
| ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------- | -------------------------------------------- |
| Retry with the same `requestId` after `504` / `500 OUTCOME_UNKNOWN` | stored result, the scroll is not spent twice                                                                                                                                         | `claim` → `REPLAY_OK` (040), key R12              | host component: replay, fresh correlation id |
| Same `requestId`, other `itemId` / `scrollItemId` / `supportItemId` | other key → an honest second operation, not another's result                                                                                                                         | `IdempotencyKeys.derive(…)` with the instance ids | platform IT                                  |
| Two parallel POSTs, one `requestId`                                 | one `200`, the other `500 OUTCOME_UNKNOWN` (`IN_FLIGHT`), the third `200` with the same body                                                                                         | atomic `claim` INSERT                             | platform IT                                  |
| `supportItemId == scrollItemId` (stack of 2)                        | `400` on the platform, `VALIDATION_FAILED` on the host, nothing claimed or spent                                                                                                     | R2                                                | IT + component                               |
| `supportItemId` is not a support item                               | `SUPPORT_NOT_APPLICABLE` before `claim`                                                                                                                                              | pre-checks R6                                     | component                                    |
| Stale or reused `itemId`                                            | `ITEM_NOT_FOUND` / `ITEM_CHANGED` by `itemTemplateId` + `expectedEnchantLevel`, before `claim`                                                                                       | R2, R6                                            | component                                    |
| Item at the server cap                                              | the engine's behaviour as published (refuse, or spend the scroll and keep the level); the result reports `previousEnchantLevel == enchantLevel`, the platform warns before the click | engine; R22 `enchant` caps                        | component + platform UI check                |
| Owner / not enchantable / zero chance reached after the spend       | unreachable for honest input (pre-checks); if reached, stored OK with `success = false`, `reason`, `scrollSpent`                                                                     | R6                                                | component (refusal after spend)              |
| Scroll destroy returns nothing after a computed success             | the engine's own branch; the result reports it, no level change                                                                                                                      | engine                                            | component                                    |
| Crash between the spend and `markOk`                                | boot resolver: `CHARGED` + item rows → `markOk` with the reconstructed result; retry → `REPLAY_OK`                                                                                   | Receipt protocol 4                                | integration                                  |
| Crash before the first mutation (`STARTED`)                         | `failStale`, retry allowed                                                                                                                                                           | Receipt protocol 4                                | integration                                  |
| Destruction of an equipped item                                     | unequipped, crystals added, the item row deleted, `gs_items` gets `DELETED` through the triggered cycle                                                                              | engine + R7                                       | e2e                                          |
| Session end during the attempt on the game pool                        | the operation sees `ending` under the inventory write lock before any mutation → `CHARACTER_BUSY`; or the exit waits                                                                 | Host semantics                                    | component (hung operation + end)             |
| Mail or trade changes the scroll stack between check and spend      | impossible: the whole operation runs under the inventory write lock                                                                                                                  | Host semantics                                    | component                                    |
| Hwid item lock on the item                                          | `ownerVerified = true` of the session bypasses it (buy-now parity); `false` → `ITEM_LOCKED`                                                                                          | R15, `nx-gameservers` 089                         | component                                    |
| Five refusals in a row ("wrong scroll")                             | the session is not kicked, no illegal-action counter                                                                                                                                 | Host semantics                                    | component                                    |
| Enchant-bonus effect on a character without a client                | the same final chance through packet and command                                                                                                                                     | engine's chance; effect restore at placement      | component                                    |
| Destruction → "item depleted" push                                  | no false "ran out of X" for a weapon                                                                                                                                                 | platform push producer filter                     | platform IT                                  |

**AddItemAttribute**

| Vector                                          | Expected behaviour                                                                                              | Enforced where             | Test        |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | -------------------------- | ----------- |
| `count` above the stones in the stack           | series = `min(count, stack)`, `attributeItemsSpent` = used stones                                               | engine                     | component   |
| Limit reached before `count`                    | `limitReached`, the surplus stones are not spent                                                                | engine                     | component   |
| Engine guard "element changed under the series" | the engine's own order (it spends, then guards); unreachable: the operation runs under the inventory write lock | Host semantics             | component   |
| Opposite element on an armor                    | `ATTRIBUTE_CONFLICT`, no punishment                                                                             | pre-checks, Host semantics | component   |
| `maxArmorAttributes` reached                    | `MAX_ARMOR_ATTRIBUTES` before the spend                                                                         | engine (before its spend)  | component   |
| Huge `count`                                    | `400` (`@Max`) on the platform; the host cuts by stack and `maxPower`                                           | platform 088               | IT          |
| A stone as the target                           | impossible: a stone is not attributable → `ITEM_NOT_ATTRIBUTABLE`                                               | engine                     | component   |
| Crash between the spend and the element write   | resolver: element rows against the journaled series → apply → `markOk`                                          | Receipt protocol 4         | integration |
| Failed element write                            | `OUTCOME_UNKNOWN` + alert, no false `OK`                                                                        | Receipt protocol 5         | component   |
| Retry / two devices / other stone or `count`    | as for enchant; `count` is part of the key                                                                      | R12                        | IT          |

**AugmentItem (insert and re-roll)**

| Vector                                            | Expected behaviour                                                                                            | Enforced where     | Test          |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------ | ------------- |
| `replace = false` on an augmented item            | `ALREADY_AUGMENTED` before `claim`, the session is not extended                                               | R6                 | component     |
| `replace = true` while an attempt is pending          | `ATTEMPT_PENDING` before `claim`, nothing paid twice                                                             | R5                 | component     |
| Re-attempt paid, choice pending                      | the receipt stores the attempt; retry → the same `pendingItemOptionIds`; the engine's pending request holds it   | R5, R12            | component     |
| Life stone spent, gemstones short                 | pre-checks count both before `claim` → `NOT_ENOUGH_FEE_ITEMS`; the engine's own two-step spend is unreachable | R6                 | component     |
| No augmentation can be generated for the stone    | `LIFE_STONE_NOT_APPLICABLE` before `claim` (catalog target groups)                                            | R6                 | component     |
| Failure after the attempt price was debited          | `markChargedOwnTx` before the debit; the engine's refund + `markRefundedOwnTx`, refusal with a reason         | Receipt protocol 3 | component     |
| Gemstone not a command field                      | the host takes the engine's gemstone and count for the item grade from the inventory                          | R4                 | component     |
| A life stone as the target                        | impossible: not augmentable → `ITEM_NOT_AUGMENTABLE`                                                          | engine             | component     |
| Failed augmentation write                         | `OUTCOME_UNKNOWN` + alert                                                                                     | Receipt protocol 5 | component     |
| Session end during the operation                  | as for enchant                                                                                                | Host semantics     | component     |
| Fee by a sub-grade                                | raw grade rows (`augmentationfee.itemGrade`), not the collapsed one                                           | R21                | provider unit |
| Other `lifeStoneItemId` with the same `requestId` | other key                                                                                                     | R12                | IT            |

**SelectAugmentation**

| Vector                                                        | Expected behaviour                                                                                      | Enforced where     | Test        |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ------------------ | ----------- |
| No pending attempt (none, expired, already chosen)               | `NO_PENDING_ATTEMPT` before `claim`; a retry of an applied select → `REPLAY_OK`                            | R5                 | component   |
| `expectedItemOptionIds` differ from the item                  | `ITEM_CHANGED` before `claim`                                                                           | R6                 | component   |
| Pending attempt of another item or previous augmentation changed | `NO_PENDING_ATTEMPT` (the engine's own check of the pending request)                                       | engine             | component   |
| `KEEP`                                                        | the attempt is dropped, nothing changes, result = current augmentation                                     | engine             | component   |
| `NEW`, crash between remove and set                           | resolver: no augmentation row + journaled attempt → set → `markOk`                                         | Receipt protocol 4 | integration |
| Two devices choose at once                                    | one `OK`, the other `IN_FLIGHT` then the stored result (same key) or `NO_PENDING_ATTEMPT` (other `choice`) | R12                | IT          |
| Session ended between attempt and select                         | `NO_PENDING_ATTEMPT`; the item keeps its augmentation; the attempt price is not refunded (client parity)      | R5                 | component   |

**RemoveAugmentation**

| Vector                                  | Expected behaviour                                                                                                  | Enforced where       | Test        |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | -------------------- | ----------- |
| `NOT_AUGMENTED`                         | before `claim`, the session is neither opened nor extended                                                          | R6                   | component   |
| The picker showed an older augmentation | `expectedItemOptionIds` differ → `ITEM_CHANGED`                                                                     | R6                   | component   |
| Cancel price `<= 0`                     | refusal (`INVALID_STATE`) without a debit                                                                           | engine               | component   |
| `NOT_ENOUGH_ADENA`                      | before the debit                                                                                                    | engine               | component   |
| Crash between the debit and the removal | `CHARGED` (price in the receipt) + augmentation row present → finish the removal → `markOk`; no debit → `failStale` | Receipt protocol 3-4 | integration |
| Failed removal write                    | `OUTCOME_UNKNOWN` + alert, not a free rollback after a relog                                                        | Receipt protocol 5   | component   |
| Retry with the same key                 | `REPLAY_OK` with `adenaSpent` (the price depends on the crystal count, unrecoverable after the removal)             | 040                  | component   |

**App-phantom session**

| Vector                                                      | Expected behaviour                                                                                                                               | Enforced where       | Test         |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------- | ------------ |
| First command of a character                                | `engine.start` without the character lock; the placement takes its own offline lock                                                              | R9, Engine invariant | component    |
| Two first commands at once (two devices)                    | one `STARTED`, the other `CHARACTER_BUSY` with the session → `touch` → both run                                                                  | R9                   | component    |
| `touch == false` / `endedAt != null`                        | `CHARACTER_BUSY` at once, receipt `FAILED`, the retry opens a new session                                                                        | R9, R12              | component    |
| `touch` while another session of the server closes          | returns immediately, never waits for a slot                                                                                                      | Engine changes       | engine unit  |
| Fishing or store start while a phantom stands               | `supersede` before any lock, waits for the removal, then the start's own lock; a phantom in the world → `CHARACTER_BUSY`, not `IN_PRIVATE_STORE` | R11                  | component    |
| `supersede` meets a slot in `STARTING`                      | the queued close ends the session once `RUNNING`                                                                                                 | Engine changes       | engine unit  |
| Command refused before the operation                        | no session opened or extended                                                                                                                    | R6, R9               | component    |
| Second phantom of one master account / account / hwid       | `ALREADY_ACTIVE` + `scope`; limits from R16, host-enforced                                                                                       | R9                   | component    |
| `idle-seconds` without a command                            | `APP_IDLE`, return to origin, `active_ms` not published                                                                                          | R10                  | component    |
| Hot reload of `app.phantom.idle-seconds`                    | applies from the next `touch`                                                                                                                    | R10                  | unit         |
| Client login during an in-flight command                    | refused once, the next attempt → `APP_LOGIN`                                                                                                     | host login closer    | component    |
| Client login without a command                              | the session closes with `APP_LOGIN` before the live character loads                                                                              | 038                  | holds        |
| Kick / ban of the phantom                                   | `KICKED` / `BANNED`, the next command is refused by the start rules (sanction)                                                                   | 038                  | holds        |
| Exit without the lock after its timeout during an operation | the exit takes the inventory write lock the operation holds; the operation reads `ending` under it → no item row lost                            | Host semantics       | component    |
| Restart with an open phantom                                | `SERVER_STOP` at boot before commands, origin, `endedAt = startedAt + activeMs`, no loaded world                                                 | R13                  | integration  |
| `enabled = false` while a phantom stands                    | `FEATURE_DISABLED` per command, idle end                                                                                                         | R16                  | component    |
| Pool spot outside a peace zone                              | host pool validation error (`ERROR`, pool skipped): a stunned phantom could not act                                                              | host pool parser     | parser unit  |
| Pool full                                                   | `NO_FREE_SPOT` + host log + platform metric                                                                                                      | R17, 088             | component    |
| Pool name is the store pool or a fishing pool               | file validation error                                                                                                                            | R17                  | unit         |
| Tick between the engine forgetting the session and the exit | no activity row                                                                                                                                  | R8                   | mapping unit |
| Phantom as a target of trade, party, duel, summon           | refused, invulnerable, receives nothing                                                                                                          | 038 protections      | holds        |
| Mail returned or expired to a phantom sender                | online branch under the sender's mail lock                                                                                                       | R14                  | holds        |

**gd-sync catalog**

| Vector                                                     | Expected behaviour                                                              | Enforced where | Test                   |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------- | -------------- | ---------------------- |
| Entity with a non-numeric engine key                       | never on the wire: `pk` = host `int` id / singleton `0` / aggregate id          | Catalog intro  | consumer IT per entity |
| Registered provider, empty catalog                         | empty snapshot, `count = 0`, not reconciled, no error                           | Catalog intro  | consumer IT            |
| Host not ready                                             | `null` → burst deferred, WARN inside the 15-minute window                       | 030 §3.7       | holds                  |
| Duplicates in the engine's data                            | first or last wins as the engine loads it, documented per entity                | R18            | provider unit          |
| Default sentinels of `minEnchantLevel` / `maxEnchantLevel` | `null`                                                                          | R18            | provider unit          |
| Scroll `kind` and `onFailure`                              | consistent, from the engine's one branch                                        | R18            | provider unit          |
| `enchanthpbonus` grades                                    | only the grades the engine looks up; a level past the list takes the last value | R18            | unit                   |
| `augmentationfee`                                          | raw grade; rows the engine refuses are not published                            | R21            | unit                   |
| `slot = 3` of a life-stone outcome                         | ordinal slot; the platform alters its live CHECK                                | R21, 026       | CHECK IT               |
| Attribute power limit                                      | one table `gamesettings.attribute.limits`; `attributeitem` has no limit         | R20, R22       | filter IT              |
| Old enchant tables                                         | removed after the golden diff, vocabulary → ingester → migration                | 026            | release order          |

**settings-sync `gamesettings`**

| Vector                                               | Expected behaviour                                                                                                     | Enforced where    | Test                |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ----------------- | ------------------- |
| Tenant directory settings map without `gamesettings` | never after the rollout; the adapter logs the missing settings entity `ERROR` once (a configuration error, not a WARN) | Rollout 2         | tenant directory IT |
| Publication                                          | on connect and every reconnect, `updatedAt` = host start, platform deduplicates by content                             | R22               | provider unit       |
| Older snapshot after a newer one                     | the platform's upsert is gated by `updatedAt`                                                                          | 088               | ingest IT           |
| Platform older than the host                         | unknown entity → WARN + skip, the partition keeps going                                                                | platform consumer | holds               |
| Enchant cap                                          | the host publishes the cap it applies, `maxAccessory` resolved, `null` where none                                      | R22               | component + unit    |
| No snapshot yet                                      | the platform read returns `null`, not `404`                                                                            | 088               | IT                  |

**runtime-sync `stateStamp`**

| Vector                                       | Expected behaviour                                                              | Enforced where | Test                   |
| -------------------------------------------- | ------------------------------------------------------------------------------- | -------------- | ---------------------- |
| Leave and return within one tick, same bytes | new hash, the pack is republished after the tombstone                           | R23            | counter + mapping unit |
| Increment relative to visibility             | before spawn and the online flag: no extra republish                            | R23            | unit                   |
| Check-load without entering the world        | the counter is not touched                                                      | R23            | unit                   |
| Tombstone with stamp 0                       | allowed; tombstone → pack order on one topic by the character key               | engine         | holds                  |
| Growth of the per-character counter map      | no eviction (the value must outlive the character's leave); limitation recorded | R23            | —                      |
| Phantom without an active session            | no activity row                                                                 | R8             | unit                   |

### Review round 2026-10-25 (roles EO / ED / CR / PA / IN)

Five independent reviewers (exactly-once, engine duplication, crash and recovery, platform abuse, integrations)
reviewed the adapter, platform, host and catalog specs together; all five verdicts were "not ready". Rows below are
the adapter-level findings and decisions, including the rejected ones; platform rows live in the platform spec 088,
host rows in the host's mapping doc.

| #   | Role       | Risk    | Finding                                                                                                                                                                                                                   | Fix / decision                                                                                     |
| --- | ---------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| 1   | EO, ED, CR | blocker | The character lock was held across the session start: the placement needs the same lock, so the first command could never open a session; waiting for a session end under the lock made the exit leave the world unlocked | R9, Engine invariant: lock only around the operation, fishing-like order                           |
| 2   | EO, ED, CR | major   | Receipts without a resolver: an open row after a crash refused retries forever; an attempt leaves no durable evidence                                                                                                         | Receipt protocol: pre-state in `claim`, outcome journaled before the first mutation, boot resolver |
| 3   | IN         | blocker | `GameDataSyncEvent.pk` is numeric, five entities were keyed by strings or tuples                                                                                                                                          | Catalog intro, R18, R21: host `int` ids, singleton, aggregates                                     |
| 4   | PA         | blocker | Owner verification on every command would exhaust the platform's shared refresh budget and break neighbouring commands                                                                                                    | D2: a session property, R15, platform spec 089                                                     |
| 5   | PA         | blocker | No "one phantom per master account / account / device"; holding a spot was free                                                                                                                                           | D4: `ALREADY_ACTIVE` + `scope`, keys of R16, R9                                                    |
| 6   | ED, CR, EO | major   | The host's lock helpers release the character lock under a still-running operation; the phantom exit held no inventory lock                                                                                               | Host semantics: operation and exit under the inventory write lock, `ending` re-read under it       |
| 7   | CR         | major   | `touch` as a slot task waited behind `close`; a shared bounded executor stalled every session                                                                                                                             | Engine changes: `touch` is a CAS without a slot hop                                                |
| 8   | CR, EO     | major   | "Wait for the end" on `touch == false`: the end is written on a flush tick, up to a minute away                                                                                                                           | R9: `CHARACTER_BUSY` at once, retry from the platform                                              |
| 9   | ED, CR     | major   | `supersede` after the start's own lock → unlocked exit; a standing phantom read as a private store                                                                                                                        | R11, Engine changes                                                                                |
| 10  | EO, CR     | major   | Refusals after `claim` left the receipt open; `deadline` checked only first                                                                                                                                               | Receipt protocol 2, R2                                                                             |
| 11  | EO, ED     | major   | A stale or reused instance id operates on another item                                                                                                                                                                    | R2 `itemTemplateId`, expected state, `ITEM_CHANGED`                                                |
| 12  | EO, PA     | minor   | Idempotency key without the item and materials; UUIDv7 freshness not required                                                                                                                                             | R12                                                                                                |
| 13  | PA         | major   | The session opened before the operation checks: a free placement; a refusal extended the session                                                                                                                          | R6, R9                                                                                             |
| 17  | PA, ED     | major   | `ownerVerified` bypasses the device item lock of an irreversible operation                                                                                                                                                | D1: bypass kept for every operation, buy-now parity; session-stored flag raised by `true`, platform refresh once per series (R15, 089)      |
| 19  | ED, IN     | major   | At the server cap the scroll is spent and the level stays; `maxAccessory` was not published                                                                                                                               | D6: engine behaviour kept and recorded; R22 publishes the applied cap; the platform warns          |
| 20  | EO, ED     | major   | The native two-step re-roll loses a paid attempt on any session end                                                                                                                                                          | D5: native flow kept, `SelectAugmentationCommand`, R5 invariants                                   |
| 21  | EO         | major   | Adena debited before apply without a journal (removal and re-roll)                                                                                                                                                        | Receipt protocol 3                                                                                 |
| 22  | EO         | major   | One instance as scroll and support: two spends per attempt                                                                                                                                                                   | R2, platform `400`                                                                                 |
| 23  | IN         | major   | Tenant directory settings map without `gamesettings`: the platform would never see a snapshot                                                                                                                             | Rollout 2                                                                                          |
| 24  | IN         | major   | Two sources of the attribute power limit                                                                                                                                                                                  | R20, R22                                                                                           |
| 25  | IN         | major   | `ItemAugmentationDbDto` without `lifeStoneItemTemplateId`: the per-stone cancel row is unselectable                                                                                                                       | R21                                                                                                |
| 26  | IN         | major   | Scroll `kind` derived from a field the engine does not use for the outcome                                                                                                                                                | R18                                                                                                |
| 29  | IN         | major   | "Never sends an empty snapshot" contradicted 030 (`null` = not ready → permanent error)                                                                                                                                   | Catalog intro; `nx-gs-adapter/CLAUDE.md` corrected                                                 |
| 33  | EO         | minor   | Ownership / enchantable / zero-chance checks sit after the scroll spend in the engine                                                                                                                                     | R6: pre-checks make them unreachable; honest stored result otherwise                               |
| 34  | EO         | minor   | Attribute series guard after the spend, no refund                                                                                                                                                                         | unreachable under the inventory write lock (Host semantics)                                        |
| 35  | EO         | minor   | Augmentation generated after the stone is spent                                                                                                                                                                           | R6 pre-check                                                                                       |
| 36  | EO         | minor   | `deadline` equal to the reply timeout against a session start → `504` loop                                                                                                                                                | R2: re-checked after waits; a longer deadline for the opening command is measured first            |
| 38  | ED         | minor   | Life stone and gemstones spent in two steps                                                                                                                                                                               | R6 pre-check of both counts                                                                        |
| 39  | ED         | minor   | Swallowed write errors of augmentation and element rows                                                                                                                                                                   | Receipt protocol 5                                                                                 |
| 40  | ED         | minor   | Folding the host's paid auto-enchant into the shared service would change its odds                                                                                                                                        | D6: core behaviour unchanged; the shared service wraps the packet path verbatim                    |
| 41  | ED         | minor   | `requestNow` for a destroyed item leaves a ghost (hypothesis)                                                                                                                                                             | **false**: the triggered cycle publishes `DELETED` (R7)                                            |
| 43  | ED         | minor   | A mail returned to a phantom sender writes behind the live inventory (hypothesis)                                                                                                                                         | **holds**: online branch under the sender's lock (R14)                                             |
| 44  | CR         | minor   | Boot close stamped the boot time; the exit on an empty world was not written                                                                                                                                              | R13, Engine changes                                                                                |
| 45  | CR         | minor   | `idleMs` in the start request against the live key                                                                                                                                                                        | Engine changes, R10                                                                                |
| 46  | CR         | minor   | Kill switch while a phantom stands                                                                                                                                                                                        | R16                                                                                                |
| 52  | CR         | minor   | Client login during an in-flight command                                                                                                                                                                                  | Edge-flow table                                                                                    |
| 58  | PA         | minor   | Refusal `extensions` not fixed                                                                                                                                                                                            | Reason codes: `reason`, `retryAt`, `scope` only                                                    |
| 60  | IN         | minor   | Default sentinels of the enchant window published as numbers                                                                                                                                                              | R18                                                                                                |
| 61  | IN         | minor   | `stepChancePercent` and the support's step undefined across builds                                                                                                                                                        | command session (Enchant)                                                                          |
| 62  | IN         | minor   | `compatibleKinds`: empty vs `null`                                                                                                                                                                                        | R18                                                                                                |
| 63  | IN         | minor   | `enchanthpbonus`: raw grade, level index, fallback                                                                                                                                                                        | R18                                                                                                |
| 64  | IN         | minor   | `augmentationfee.itemGrade` raw vs collapsed; zero fees                                                                                                                                                                   | R21                                                                                                |
| 65  | IN         | minor   | `slot = 3` defined through bits                                                                                                                                                                                           | R21                                                                                                |
| 66  | IN         | minor   | `GameSettings` without `updatedAt` and a publication moment                                                                                                                                                               | R22                                                                                                |
| 69  | IN         | minor   | Names: `ItemGrade`, `scroll_group_id`, glossary, `excludedItemTemplateIds`                                                                                                                                                | R18, R19, Problem                                                                                  |
| 71  | IN         | minor   | Duplicate rule stated for one entity only                                                                                                                                                                                 | R18                                                                                                |
| 72  | IN         | minor   | A shared pool name could collide with the store or fishing pools                                                                                                                                                          | R17                                                                                                |
| —   | CR         | —       | `awaitEnd(sessionId)` on the engine as an alternative to `CHARACTER_BUSY`                                                                                                                                                 | **rejected**: idempotent by receipt, no new engine API                                             |
| —   | EO         | —       | Host compares the intent in `claimJson` on `REPLAY_OK`                                                                                                                                                                    | **replaced** by the key composition (R12)                                                          |
| —   | PA         | —       | A platform concurrent quota as a second line against phantoms                                                                                                                                                             | **not now**: per-account / per-device rules are the host's; revisit on metrics                     |
| —   | PA         | —       | A host-side cooldown key                                                                                                                                                                                                  | **rejected**: a rule of its own over the game's mechanics                                          |
| —   | ED, IN     | —       | Refuse at the server cap before the spend                                                                                                                                                                                 | **rejected** (D6): core behaviour is not changed; recorded as a fact, the platform warns           |

**Holds (verified in code).** The receipt key is `idempotencyKey`, not the correlation id; `claim` is an INSERT on a
unique key with `REPLAY_OK` for `OK`, `IN_FLIGHT` for an open row, `FAILED` reopened by CAS; a retry after `markOk`
executes nothing. The consumer's at-most-once (commit before dispatch) rules out a Kafka redelivery. Two commands of one
character with different `requestId`s are serialized by the host's character lock: the second is `CHARACTER_BUSY` or
runs after — two legitimate attempts, not a duplicate. A login under a phantom without a command closes the session with
`APP_LOGIN` before the live character loads. Kick or ban of a phantom ends the session `KICKED` / `BANNED`. A crash
mid-placement or mid-removal is closed by `SERVER_STOP` at boot with an idempotent position update; pending writes keep
the character busy until the end is written. `supersede` on a `STARTING` slot is not lost (serial slot queue). The
engine writes levels, counts, augmentation and element rows synchronously, so the database is authoritative at boot and
the resolver may rely on it. The item-source rules cover store, trade, active enchant, pet and cast; mail attachments
never load into the live inventory. A stone is not attributable and a life stone is not augmentable: "material = target"
is impossible except for scroll / support (R2). The packet's lock order (inventory write lock → item monitor) matches the
inventory container's own, so the service introduces no inversion. Destruction of an equipped item unequips it; crystals
into a full inventory behave as for the client. Multi-enchant and auto-enchant are unreachable without a client. The
phantom is invulnerable and not a target of trade, party, duel or summon. Rolling deploy: the enum parity test fails the
build without enum + CHECK; unknown `activity` → `NULL`, `logoutReason` → `OTHER`, bucket and usage row → skip with a
metric, unknown settings entity → WARN + skip; `SERVER_STOP` is already in the close-reason CHECK; the pool-key SPI break
keeps the file, no platform straddle; the presence consumer skips `source == APP`. Catalog: `maxEnchantLevel` exclusive
and `minEnchantLevel` inclusive in both lines; grade collapse and `NONE` for gradeless scrolls are right (the old
"`NULL` = any grade" was a dictionary error); a level past the last range takes its chance, `chance = 0` is a real
value; `attributable` is published resolved; a build without attribute data has no provider → `UNSUPPORTED_COMMAND`;
`enchantfailurereward` has a real filler in the Essence line; three option slots per enchant in both lines, option slot 3
of augmentation in Essence; `option3Id` is additive; `UNIQUE NULLS NOT DISTINCT` needs PostgreSQL ≥ 15; Java 8 / no-deps:
the new POJOs and enums fit the api's constraints. `stateStamp` (R23) closes a real race of two tick loops; `active_ms`
and retention do not concern the phantom.

## Rollout

1. API release: commands and results (five), refusal reasons, `AugmentationChoice`, `ActivityType.OFFLINE_PHANTOM`,
   `ServerOnlineBucket`, `LogoutReason.APP_IDLE` / `APP_SUPERSEDED`, gd entities with numeric `pk`, `gamesettings`
   with `updatedAt`, `ItemSkillRef.enchantLevel`, `ItemTemplate.upgrade.{maxEnchantLevel, enchantGrade, augmentable}`,
   `AugmentationOutcome.slot = 3` (ordinal), `ItemAugmentationDbDto.{option3Id, lifeStoneItemTemplateId}`, engine
   `touch` / `supersede`, idle end, boot end of non-restorable sessions, list-valued pool keys, the app settings keys of
   R16 (reference file, `configuration.md` en / ru). SPI break: `AppRules.fishingSpotPool` becomes `fishingSpotPools`; a
   host switches in the same release. Contract docs (`enchant-contract.md`, `attribute-contract.md`,
   `augmentation-contract.md`), 038, 006, 017 and the glossary land in the same change.
2. Platform consumers accept the new values first and are deployed to every replica before any host sends them
   (nx-gamedata, nx-gameservers, and every other reader of the activity and logout vocabularies): accept-then-emit. The
   tenant directory's `/connect` settings map gains `gamesettings` next to `appsettings` (without it the host publishes
   nothing and the platform's game-settings read stays `null` forever); the adapter logs a settings entity missing from
   the map at `ERROR` once.
3. Host release with providers, handlers, the receipt resolver, the app-phantom placement, spot pools and the
   world-entry stamp; the owner merges and restarts the game servers.

Platform rate limits for the five endpoints are the platform's (088): starting numbers in that spec, tuned later by
usage metrics; the host has no cooldown of its own.

## Open questions

None.

## Links

- Platform: `nx-gameservers/docs/specs/088-item-modification.md`, `nx-gameservers/docs/specs/089-owner-verification.md`,
  `nx-gamedata/docs/specs/026-enchant-attribute-via-adapter.md`.
- Glossary: `nx-l2/docs/public/en/glossary.md` (new terms `enchant-support-item`, `enchant-rate-group`,
  `enchant-scroll-group`, `enchant-failure-reward`, `attribute-item`, `gemstone`, `item-modification`, `app-phantom`;
  `enchant-scroll` kinds `BLESSED_DOWN`, `CURSED`, `GIANT`; `fake-player` renamed `server-phantom`; the `phantom`
  collision).
