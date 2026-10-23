# Commands — Wire Catalog

> Per-command business contract. For the runtime / handler-author concerns
> (lifecycle, threading, registration, error envelope) see
> [`guide.md`](./guide.md). Formal infra spec lives in [`spec.md`](./spec.md).

This document describes **what each command does** from the platform's
point of view — the inputs the caller supplies, the result fields they
receive on success, and the error statuses they can expect. It is the
contract every web-side caller of `NxCommandSender.sendSync(...)` is
coding against.

All commands ship under `app.l2nx.gs.adapter.api.kafka.commands.<group>.*`
where group is one of: `announcement`, `ban`, `captcha`, `character`, `chat`, `gd`,
`item`, `mail`, `offlinefishing`, `privatestore`, `sync`, `telegram`. The group is purely a
code-organization split; on the wire every command travels on the single
`<tenant>.gs.commands` topic, routed by the `Nx-Message-Type` header.

Naming convention: `{X}Command` → `{X}Result`. The reply envelope is
always `CommandResult<R>` where `R` is the dedicated result type
declared on the command's `NxCommand<R>` marker.

---

## Announcement commands

### `AnnounceNowCommand`

> **Removed in `api/v0.87.0`.** `SendChatMessageCommand` covers this exact call with
> `senderCharacterId: null`, `senderDisplayName: ""`, `channel: ANNOUNCEMENT`,
> `audience: ALL_ONLINE`, so the two never coexisted as choices — see
> [`send-chat-message.md`](send-chat-message.md). Hosts pinned to `api/v0.86.0` or earlier still
> compile against it; anything upgrading past that sends the chat command instead. `AnnounceResult`
> went with it.

### `DeleteAutoAnnouncementCommand`

**Purpose.** Delete one row from the game-server's native
`auto_announcements` table (or equivalent). Used for two platform flows:
an operator deleting a `GAME`-origin row directly ("delete in game"), and
the `GAME`→`L2NX` transfer flow, where the platform first creates its own
copy of the announcement and then issues this command to remove the
now-redundant source row so it is not re-ingested by the next db-sync
cycle.

**Inputs**

| Field    | Type   | Required | Notes                                                                                                                              |
| -------- | ------ | -------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `gameId` | `long` | yes      | The host's native `auto_announcements` row id — the same value surfaced as `AutoAnnouncementDbDto.id` on the db-sync mirror stream |

**Result** — none (`Void`). Reply is `CommandResult<Void>` — `ok()` on
success with no typed payload; the caller already knows the `gameId` it
asked to delete.

**Errors**

| Status           | When                                  |
| ---------------- | ------------------------------------- |
| `NOT_FOUND`      | No row with the given `gameId` exists |
| `INTERNAL_ERROR` | Delete failed host-side               |

---

## Ban commands

### `BanCommand`

**Purpose.** Apply a ban. Build-agnostic: the contract names the target
dimension, the ban kind, and the expiry; the host maps those onto its own
ban engine and decides how the ban is enforced. Applying a ban is
naturally convergent — the host SHOULD treat a re-issued command for an
already-active ban as a no-op success.

**Inputs**

| Field         | Type       | Required    | Notes                                                                                                                                                                                                                                                                 |
| ------------- | ---------- | ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `targetType`  | `String`   | yes         | `WellKnownBanTargetTypes` value: `CHARACTER`, `ACCOUNT`, `IP`, `HWID`, or `HARD` (fan-out marker — the host expands one `HARD` command into the full concrete set — character + account + IP + HWID — for the same subject; a persisted ban row never carries `HARD`) |
| `targetValue` | `String`   | yes         | Keyed datum for `targetType` — char id (as a string), account login, plaintext IP, or HWID hash. For `HARD`, the subject's char id                                                                                                                                    |
| `banType`     | `String`   | yes         | `WellKnownBanTypes` value: `GAME_LOGIN`, `CHAT`, `CHAT_SHADOW`, `PARTY`, `JAIL`                                                                                                                                                                                       |
| `permanent`   | `boolean`  | yes         | Drives expiry: `true` → `expiresAt` MUST be `null`; `false` → `expiresAt` required. Constructor enforces `permanent == (expiresAt == null)`                                                                                                                           |
| `expiresAt`   | `Instant?` | conditional | Instant the ban lapses; `null` iff `permanent`                                                                                                                                                                                                                        |
| `reason`      | `String?`  | no          | Human-readable ban reason, surfaced to the player and stored on the ban row                                                                                                                                                                                           |
| `issuedBy`    | `String?`  | no          | Admin display name or service identifier, stored on the ban row for audit                                                                                                                                                                                             |

**Result** (`BanResult`)

| Field    | Type         | Notes                                                                                                                                                                                                                     |
| -------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `banIds` | `List<Long>` | Ids of the ban rows created or matched. Never `null`; empty when the ban kind is not persisted as an id-bearing row (e.g. a char-variable-backed shadow chat ban). A `HARD` fan-out returns one id per concrete dimension |

**Errors**

| Status              | When                                                                                           |
| ------------------- | ---------------------------------------------------------------------------------------------- |
| `NOT_FOUND`         | The targeted character / account does not exist                                                |
| `VALIDATION_FAILED` | Wire payload missing `targetType`, `targetValue`, or `banType`, or an unrecognized combination |
| `FORBIDDEN`         | Operation rejected on host policy grounds                                                      |

---

### `UnbanCommand`

**Purpose.** Lift a previously applied ban — the inverse of `BanCommand`:
names the same target dimension and ban kind and asks the host to clear
the matching ban(s). Clearing a ban that is not present is a no-op
success (`removed = false`), not an error — the post-condition (no such
ban) already holds.

**Inputs**

| Field         | Type     | Required | Notes                                                                                            |
| ------------- | -------- | -------- | ------------------------------------------------------------------------------------------------ |
| `targetType`  | `String` | yes      | `WellKnownBanTargetTypes` value. A `HARD` target clears every concrete dimension for the subject |
| `targetValue` | `String` | yes      | Keyed datum for `targetType`                                                                     |
| `banType`     | `String` | yes      | `WellKnownBanTypes` value naming which ban kind to clear                                         |

**Result** (`UnbanResult`)

| Field           | Type         | Notes                                                                                                          |
| --------------- | ------------ | -------------------------------------------------------------------------------------------------------------- |
| `removed`       | `boolean`    | `false` when no matching ban existed — a no-op success, not an error                                           |
| `removedBanIds` | `List<Long>` | Ids of the ban rows cleared. Never `null`; empty when nothing matched or the ban kind is not an id-bearing row |

**Errors**

| Status              | When                                                           |
| ------------------- | -------------------------------------------------------------- |
| `VALIDATION_FAILED` | Wire payload missing `targetType`, `targetValue`, or `banType` |
| `FORBIDDEN`         | Operation rejected on host policy grounds                      |

---

## Chat commands

### `SendChatMessageCommand`

**Purpose.** Put a line of text into game chat from outside the game
client — the generic outbound counterpart of `ChatMessageEvent`. Covers
the mini app posting into clan chat as the player's own character (the
character may be offline), and the platform speaking under an arbitrary
display name. Supersedes `AnnounceNowCommand`; see spec
[`send-chat-message.md`](send-chat-message.md) for the cutover phases.

Idempotent by `messageId` provided the host keeps a bounded window of
seen ids — re-issuing after a reply timeout then converges on one message
rather than posting twice.

**Inputs**

| Field                 | Type      | Required | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| --------------------- | --------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `messageId`           | `UUID`    | yes      | UUIDv7 minted by the platform. The host echoes it as the `eventId` of the resulting `ChatMessageEvent`, and dedupes re-deliveries on it                                                                                                                                                                                                                                                                                                                                    |
| `channel`             | `String`  | yes      | `WellKnownChatChannels` code. Accepted values are a whitelist that grows per slice — planned `CLAN`, `ALLIANCE`, `PARTY`, `WHISPER`, `ANNOUNCEMENT` (shipped: `CLAN`, `ANNOUNCEMENT`); anything else is `VALIDATION_FAILED`. `CRITICAL_ANNOUNCEMENT` is deliberately not part of the contract                                                                                                                                                                              |
| `audience`            | `String`  | yes      | `CHARACTER`, `CLAN`, `ALLIANCE`, `PARTY` or `ALL_ONLINE` — the recipient list, orthogonal to `channel` (`ALLIANCE` / `PARTY` planned, spec R22)                                                                                                                                                                                                                                                                                                                            |
| `audienceId`          | `Long?`   | cond.    | Character id for `CHARACTER` (or `targetCharacterName` instead), clan id for `CLAN`, alliance id for `ALLIANCE`; `null` for `PARTY` and `ALL_ONLINE`                                                                                                                                                                                                                                                                                                                       |
| `senderCharacterId`   | `Long?`   | no       | Who speaks legally — drives the host's gates, the packet's `objectId` and platform attribution. `null` means the platform itself speaks                                                                                                                                                                                                                                                                                                                                    |
| `senderDisplayName`   | `String?` | cond.    | What the client renders for nameless senders (`"System"`, `"Дед Мороз"`; empty string reproduces the nameless announcement line). Planned (spec R23): nullable when `senderCharacterId` is set — if present an updated host ignores it and builds the name itself, incl. the offline `*` postfix (older hosts use it verbatim); required when `senderCharacterId` is `null`, except channel `ANNOUNCEMENT`, where `null` renders the nameless announcement line (spec R30) |
| `source`              | `String`  | yes      | Where the message originates (`APP` for the player app, `AUTO_ANNOUNCEMENT`, …). Echoed into the event metadata under `ChatMetadataKeys.SOURCE`; the host cannot infer the surface, and without it analysis cannot tell platform traffic from what players typed in-game                                                                                                                                                                                                   |
| `text`                | `String`  | yes      | Body in the neutral chat micro-format, at most 300 characters (spec R29): plain text, LF hard line breaks, bare `http(s)://` URLs for auto-linking; URL translation is a host concern. Planned (spec R25): item links as game-native `\b\tType=1 \tID=<objectId> ...\b` tokens built by the platform and ownership-verified by the host (foreign / missing item is `VALIDATION_FAILED`)                                                                                    |
| `targetCharacterName` | `String?` | cond.    | Whisper addressee by name, used with audience `CHARACTER` when `audienceId` is `null`; trailing `*` stripped. Exactly one of the two for `CHARACTER`; both or neither is `VALIDATION_FAILED` (planned, spec R24)                                                                                                                                                                                                                                                           |
| `ownerVerified`       | `boolean` | no       | `true` only when the platform verified, against fresh master-account data, that the acting user owns the character; absent reads as `false`. See Cross-cutting › Owner verification                                                                                                                                                                                                                                                                                        |

**Result** (`SendChatMessageResult`)

| Field        | Type  | Notes                                                                                                          |
| ------------ | ----- | -------------------------------------------------------------------------------------------------------------- |
| `linesSent`  | `int` | Physical chat lines emitted — the count of non-empty lines after splitting `text` on LF                        |
| `recipients` | `int` | Online recipients the packet actually reached. Best-effort telemetry; hosts that don't track it MAY report `0` |

**Errors**

| Status              | When                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `INVALID_STATE`     | `PARTY` audience while the sender is offline or not in a party (planned)                                                  |
| `NOT_FOUND`         | `senderCharacterId`, `audienceId` or `targetCharacterName` resolves to nothing on this server                             |
| `FORBIDDEN`         | Host policy refuses — chat ban, shadow ban, block list, academy level floor                                               |
| `VALIDATION_FAILED` | Missing required field, `channel` outside the accepted whitelist, or `audienceId` absent where the `audience` requires it |
| `INTERNAL_ERROR`    | Broadcast mechanism failed host-side                                                                                      |

## Captcha commands

### `SendCaptchaCommand`

**Purpose.** Start the host's human-verification check on an online character and reply with what
the player did. **Deferred reply** ([`spec.md`](./spec.md) R27): the reply arrives when the check
ends — minutes after the command, not within the platform's synchronous window. Not idempotent and
not deduped: one open check per character, a second command is `INVALID_STATE`. Full contract:
[`captcha.md`](captcha.md).

**Inputs**

| Field         | Type      | Required | Notes                                              |
| ------------- | --------- | -------- | -------------------------------------------------- |
| `characterId` | `Long`    | yes      | Target character's primary key                     |
| `issuedBy`    | `String?` | no       | Staff login or service label; echoed in the result |
| `staffNotes`  | `String?` | no       | Internal staff note; not shown to the player       |

**Result** (`SendCaptchaResult`)

| Field         | Type                       | Notes                                                                                       |
| ------------- | -------------------------- | ------------------------------------------------------------------------------------------- |
| `characterId` | `Long`                     | Echo                                                                                        |
| `issuedBy`    | `String?`                  | Echo                                                                                        |
| `outcome`     | `String`                   | `WellKnownCaptchaOutcomes`: `PASSED`, `FAILED_WRONG`, `FAILED_TIMEOUT`, `LOGOUT`, `ABORTED` |
| `startedAt`   | `Instant`                  | Host clock, UTC                                                                             |
| `finishedAt`  | `Instant`                  | Host clock, UTC                                                                             |
| `durationMs`  | `long`                     | Whole check                                                                                 |
| `rounds`      | `List<CaptchaRoundResult>` | `index`, `questionType`, `pickedSlot?`, `correct`, `answerTimeMs?`                          |
| `metadata`    | `Map<String,String>`       | Host-defined consequences (`ban.type`, `ban.expiresAt`, `kick`, …); keys not stable         |

**Errors** (immediate, no deferred reply taken)

| Status              | When                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------- |
| `VALIDATION_FAILED` | `characterId` missing / out of range                                                  |
| `NOT_FOUND`         | No such character, or not in the world                                                |
| `INVALID_STATE`     | `reason=ALREADY_ACTIVE` (check open) / `reason=SERVER_PLAYS_CHARACTER` (auto-play on) |
| `RATE_LIMITED`      | Per-character cooldown between checks                                                 |
| `UNAVAILABLE`       | Check disabled, concurrent-check limit, no picture                                    |
| `INTERNAL_ERROR`    | Unexpected failure; also `deferred-reply-expired` when the host never completed       |

---

## Character commands

### `TransferCharToAccountCommand`

**Purpose.** Re-bind a character to a different account (account migration,
re-sale support). Forces logout if the source player is online so the
target account picks the character up cleanly on next login.

**Inputs**

| Field       | Type     | Required | Notes                          |
| ----------- | -------- | -------- | ------------------------------ |
| `charId`    | `Long`   | yes      | Character to re-bind           |
| `accountTo` | `String` | yes      | Destination login (must exist) |

**Result** (`TransferCharToAccountResult`)

| Field            | Type      | Notes                                          |
| ---------------- | --------- | ---------------------------------------------- |
| `charId`         | `Long`    | Echo                                           |
| `newAccountName` | `String`  | Echo of destination account                    |
| `wasLoggedOut`   | `boolean` | `true` if the player was online and got kicked |

**Errors**

| Status              | When                                                |
| ------------------- | --------------------------------------------------- |
| `NOT_FOUND`         | Character or destination account does not exist     |
| `INVALID_STATE`     | Character cannot be re-bound (clan-leader, jail, …) |
| `VALIDATION_FAILED` | Missing fields                                      |
| `INTERNAL_ERROR`    | Unexpected failure (DB write, etc.)                 |

**Side effects on success.** `requestNow("character", charId)`.

---

### `UpsertCharacterLockCommand`

**Purpose.** Set, replace, or clear a single character lock (IP / HWID /
item-trade). Mirrors the in-game voiced `Security` command, which sets a
lock via `player.setVar(lockVar, value)` and clears it via
`player.setVar(lockVar, 0)`. Exactly one `lockType` is named per call, so a
single command can never accidentally touch any lock other than the one it
targets. Set/clear is naturally idempotent (writes an absolute value, not a
delta) — Kafka redelivery on crash recovery re-applies the same final
state.

**Inputs**

| Field      | Type      | Required | Notes                                                                                                                                        |
| ---------- | --------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId`   | `Long`    | yes      | Target character's primary key                                                                                                               |
| `lockType` | `String`  | yes      | `WellKnownCharacterLockTypes` value: `IP`, `HWID`, or `ITEM`                                                                                 |
| `value`    | `String?` | no       | A non-blank value sets/replaces the lock to that value; `null` or blank clears it (host writes the `"0"` sentinel, matching core convention) |

**Result** (`UpsertCharacterLockResult`)

| Field    | Type                 | Notes                                                                                                                                                                                                                                     |
| -------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId` | `Long`               | Echo of the target character                                                                                                                                                                                                              |
| `lock`   | `CharacterLockState` | Post-upsert state of the affected lock: `lockType` (`String`), `active` (`boolean` — in effect after the upsert), `value` (`String?` — bound datum: plaintext IP for `IP`, HWID hash for `HWID`/`ITEM`; `null` when cleared or valueless) |

**Errors**

| Status              | When                                                                                     |
| ------------------- | ---------------------------------------------------------------------------------------- |
| `NOT_FOUND`         | Character does not exist                                                                 |
| `VALIDATION_FAILED` | Wire payload missing `charId` or `lockType`, or `lockType` is not a recognized lock kind |
| `FORBIDDEN`         | Operation rejected on host policy grounds                                                |

---

### `SetCharacterAccessLevelCommand`

**Purpose.** Set a character's access level to an absolute value, mirroring the in-game
`//changelvl` command. Applies to a live session on the game thread when the target is online,
or to the offline row otherwise. `staffNotes` is a staff-only note (see
[`guide.md`](./guide.md)); the host never shows it to the player.

**Inputs**

| Field         | Type      | Required | Notes                                                                                                                                                                         |
| ------------- | --------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId`      | `Long`    | yes      | Target character's primary key                                                                                                                                                |
| `accessLevel` | `String`  | yes      | Opaque, same vocabulary as `CharacterDbDto.accessLevel`: numeric text on int-based builds (e.g. `"7"`), a role name on string-role builds. The host converts to its own model |
| `staffNotes`  | `String?` | no       | Internal staff note; host MUST NOT show it to the player, SHOULD log it on the audit line                                                                                     |

**Result** (`SetCharacterAccessLevelResult`)

| Field                 | Type      | Notes                                                                                                      |
| --------------------- | --------- | ---------------------------------------------------------------------------------------------------------- |
| `charId`              | `Long`    | Echo                                                                                                       |
| `accessLevel`         | `String`  | The level as the host stored it, in the same vocabulary                                                    |
| `previousAccessLevel` | `String?` | The level before the write, `null` when the host could not read it                                         |
| `wasOnline`           | `boolean` | `true` when applied to a live session (full effect on next login); `false` when written to the offline row |

**Errors**

| Status              | When                                                                                                                             |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `NOT_FOUND`         | Character does not exist (offline path)                                                                                          |
| `INVALID_STATE`     | A login raced the offline write                                                                                                  |
| `VALIDATION_FAILED` | `charId` / `accessLevel` missing, `accessLevel` not an integer, negative (bans go through `BanCommand`), or level not registered |
| `FORBIDDEN`         | Level above the host's platform-grantable ceiling (e.g. `5`); the in-game command is not capped                                  |
| `UNAVAILABLE`       | DB error on the offline path                                                                                                     |
| `INTERNAL_ERROR`    | Unexpected host failure (offline dispatch threw)                                                                                 |

**Side effects on success.** `requestResync("character", [charId])`.

---

### `KickCharacterCommand`

**Purpose.** Disconnect an online character, either dropping it to the login screen or closing
the client outright. Not idempotent — a second delivery after the player relogged would kick
them again — hosts dedupe on the correlation id. `staffNotes` is a staff-only note (see
[`guide.md`](./guide.md)); the host never shows it to the player.

**Inputs**

| Field         | Type      | Required | Notes                                                            |
| ------------- | --------- | -------- | ---------------------------------------------------------------- |
| `charId`      | `Long`    | yes      | Target character's primary key                                   |
| `closeClient` | `boolean` | yes      | `true` closes the game client; `false` drops to the login screen |
| `staffNotes`  | `String?` | no       | Internal staff note; not shown to the player                     |

**Result** (`KickCharacterResult`)

| Field           | Type      | Notes                                                                                          |
| --------------- | --------- | ---------------------------------------------------------------------------------------------- |
| `charId`        | `Long`    | Echo                                                                                           |
| `offlineTrader` | `boolean` | `true` when the target was an offline trader: the store was ended, `closeClient` had no effect |

**Errors**

| Status              | When                                |
| ------------------- | ----------------------------------- |
| `NOT_FOUND`         | No such character exists            |
| `INVALID_STATE`     | Character exists but is not online  |
| `VALIDATION_FAILED` | `charId` missing / out of int range |
| `INTERNAL_ERROR`    | Unexpected failure                  |

---

## Gd commands

### `GdResyncCommand`

**Purpose.** Instruct the gd-sync module to re-publish a full snapshot of
every registered game-data entity (itemtemplate, npctemplate, skill,
recipe, armorset, soulcrystal, class, instance). Pure adapter operation —
no host game code involved; the snapshot is the same burst the module
fires on connect / host datapack-reload.

**Inputs** — none. Granularity is always full snapshot (no per-entity
selection).

**Result** (`GdResyncResult`)

| Field              | Type           | Notes                                                                                                                                                                                                  |
| ------------------ | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `acceptedEntities` | `List<String>` | Entity names scheduled for re-snapshot, taken from the live provider registry rather than hardcoded. Never empty on a real ack — an adapter with zero active gd entities replies `UNAVAILABLE` instead |

**Errors**

| Status        | When                                                                                                               |
| ------------- | ------------------------------------------------------------------------------------------------------------------ |
| `UNAVAILABLE` | The gd-sync module is not active (disabled / failed / no provider on the classpath / no gd topics from `/connect`) |

**Note.** The reply is an ack returned after the snapshot is scheduled —
it does NOT wait for the publish to finish. Completion is observable on
the platform via the per-entity `SNAPSHOT_COMPLETE` markers in
nx-gamedata, not a separate completion event.

---

## Item commands

### `CreateItemCommand`

**Purpose.** Grant a fresh item stack to a character — admin item-grant,
event reward, support compensation.

**Inputs**

| Field            | Type            | Required | Notes                                                                             |
| ---------------- | --------------- | -------- | --------------------------------------------------------------------------------- |
| `charId`         | `Long`          | yes      | Target character primary key                                                      |
| `itemTemplateId` | `Long`          | yes      | Catalog template id (e.g. `57` = adena). NOT a stack object-id                    |
| `count`          | `Long`          | yes      | Quantity to grant. Must be positive                                               |
| `enchantLevel`   | `Long?`         | no       | Default `0`. Applied to non-stackable templates only; ignored for stackable       |
| `location`       | `ItemLocation?` | no       | Default `INVENTORY`. Today supported: `INVENTORY`, `WH`. Others → `INVALID_STATE` |

**Result** (`CreateItemResult`)

| Field          | Type           | Notes                                                                                  |
| -------------- | -------------- | -------------------------------------------------------------------------------------- |
| `itemId`       | `Long`         | Object-id of the resulting stack. For stackable + existing stack → existing stack's id |
| `countCreated` | `Long`         | Echo of `count` (host may clamp on stack-size limits — typically equal to input)       |
| `enchantLevel` | `Long`         | Actually applied enchant (0 for stackable templates regardless of request)             |
| `location`     | `ItemLocation` | Echo of `location` for caller confirmation                                             |

**Errors**

| Status              | When                                                                                                                                                                                                                                                         |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `NOT_FOUND`         | Character does not exist (charId not in DB), or template id unknown                                                                                                                                                                                          |
| `INVALID_STATE`     | Inventory slots full (extension `limitsExact`, as in `TransferItemToCharacterCommand`); reason `STACK_OVERFLOW` when the target stack would pass its cap; unsupported `location` for v1. Weight is a player policy and is not checked for this staff command |
| `VALIDATION_FAILED` | Missing required field, non-positive `count`, out-of-int-range id                                                                                                                                                                                            |
| `INTERNAL_ERROR`    | Unexpected failure (IdFactory exhausted, DB write threw, etc.)                                                                                                                                                                                               |

**Side effects on success.** Host emits `NxSync.requestNow("item", itemId)` and
`requestNow("character", charId)` — sync engine ticks both entities on the
next cycle so platform consumers observe the new stack within ms instead
of waiting up to one CDC interval.

---

### `DeleteItemCommand`

**Purpose.** Remove items from a character — confiscation, refund, cleanup
after rollback. Partial-stack semantics: `count` is clamped to the live
stack size, never errors on excess.

**Inputs**

| Field    | Type   | Required | Notes                                                          |
| -------- | ------ | -------- | -------------------------------------------------------------- |
| `charId` | `Long` | yes      | Owner character primary key                                    |
| `itemId` | `Long` | yes      | Object-id of the specific stack (NOT template id)              |
| `count`  | `Long` | yes      | Quantity to remove. Must be positive. Clamped to current stack |

**Result** (`DeleteItemResult`)

| Field          | Type      | Notes                                                                                          |
| -------------- | --------- | ---------------------------------------------------------------------------------------------- |
| `itemId`       | `Long`    | Echo of the stack that was decremented / destroyed                                             |
| `countDeleted` | `Long`    | Actual count removed — MAY be less than requested when the live stack was smaller              |
| `fullyDeleted` | `boolean` | `true` if the stack is gone (object destroyed); `false` if it was decremented and still exists |

**Errors**

| Status              | When                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `NOT_FOUND`         | No stack with that object-id owned by this character                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `INVALID_STATE`     | Item in an unsupported location (auction, freight, etc.); destroy rejected; critical refusals `ITEM_IN_STORE` (the stack is listed in an open private store; close the store first) and `ITEM_IN_USE` (the stack is held by a live engine object, e.g. the control item of a summoned companion, an item in an active enchant, an item the character is casting with; release it first); reason `CHARACTER_BUSY` when the character mutation lock times out or the character logs in or out mid-command; reason `RESTORE_IN_PROGRESS` while the character is being restored as an offline trader |
| `VALIDATION_FAILED` | Missing required field, non-positive `count`, `charId` out of `int` range                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `UNAVAILABLE`       | DB error on the offline path before anything was written (nothing changed, safe to retry)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `INTERNAL_ERROR`    | Unexpected handler failure; reason `OUTCOME_UNKNOWN` when the offline commit or a rollback after the write failed, or the online hop timed out while running - the stack may already be decremented, so a retry is a new deletion                                                                                                                                                                                                                                                                                                                                                                |

**Side effects on success.** `requestNow("item", itemId)` and
`requestNow("character", charId)`.

---

### `TransferItemToCharacterCommand`

**Purpose.** Move a stack from one character to another. Transparently
handles all four online/offline source-target combinations.

**Inputs**

| Field        | Type   | Required | Notes                                            |
| ------------ | ------ | -------- | ------------------------------------------------ |
| `charIdFrom` | `Long` | yes      | Source character                                 |
| `charIdTo`   | `Long` | yes      | Target character (must differ from `charIdFrom`) |
| `itemId`     | `Long` | yes      | Object-id of the source stack                    |
| `count`      | `Long` | yes      | Quantity to move. Clamped to source stack size   |

**Result** (`TransferItemToCharacterResult`)

| Field              | Type   | Notes                                           |
| ------------------ | ------ | ----------------------------------------------- |
| `itemId`           | `Long` | Echo of the moved stack's id                    |
| `countTransferred` | `Long` | Actual count moved (may be less than requested) |
| `fromCharId`       | `Long` | Echo                                            |
| `toCharId`         | `Long` | Echo                                            |

**Errors**

| Status              | When                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `NOT_FOUND`         | Either character or the item not found                                                                                                                                                                                                                                                                                                                                                                                                           |
| `INVALID_STATE`     | Target inventory slot capacity exceeded (extension `limitsExact`: `false` when an out-of-world receiver was judged by a base limit, so the refusal may be false); reason `STACK_OVERFLOW` when the receiver's stack would pass its cap; item in an unsupported location; critical refusals `ITEM_IN_STORE` (the stack is listed in an open private store) and `ITEM_IN_USE` (the stack is held by a live engine object, see `DeleteItemCommand`) |
| `VALIDATION_FAILED` | Same-character transfer, missing field, non-positive count                                                                                                                                                                                                                                                                                                                                                                                       |
| `INTERNAL_ERROR`    | Unexpected handler failure                                                                                                                                                                                                                                                                                                                                                                                                                       |

**Critical refusals.** Staff item commands skip player-policy checks (weight, bans, item lock, tradeability)
but never data-integrity ones. A host MUST refuse `ITEM_IN_STORE` and `ITEM_IN_USE` (above) for delete and
transfer, and MUST refuse a receiver whose inventory has no free slot (the same slot refusal as
`CreateItemCommand`), online or offline; a host MUST NOT create a second stack row where a stack merge is
possible.

**Side effects on success.** `requestNow("item", itemId)` and
`requestNow("character", [fromId, toId])`.

---

## Mail commands

### `SendMailCommand`

**Purpose.** Compose and deliver a system mail to a character with
optional item attachments, exactly once per idempotency key (durable host-side
receipt). The wire allows partial success through `itemErrors`; the reference
host is all-or-nothing: over its per-mail attachment cap it splits the request
into several mails, delivered all together or not at all. Details:
[`send-mail.md`](send-mail.md).

**Inputs**

| Field            | Type             | Required | Notes                                                                                                                                                                 |
| ---------------- | ---------------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId`         | `Long`           | yes      | Recipient character                                                                                                                                                   |
| `author`         | `String?`        | no       | Display author. Defaults to system character on host                                                                                                                  |
| `title`          | `String`         | yes      | Mail subject; non-empty                                                                                                                                               |
| `body`           | `String?`        | no       | Mail body; `null` = empty                                                                                                                                             |
| `items`          | `List<MailItem>` | no       | Each `MailItem`: `itemTemplateId: Long`, `count: Long`                                                                                                                |
| `adena`          | `Long?`          | no       | Adena in the mail; positive; additive                                                                                                                                 |
| `idempotencyKey` | `UUID?`          | no       | Business identity of the send, stable across retries; host dedups and replays by it; `correlationId` is one attempt only; `null` = host falls back to `correlationId` |

**Result** (`SendMailResult`)

| Field            | Type                      | Notes                                                               |
| ---------------- | ------------------------- | ------------------------------------------------------------------- |
| `createdMailIds` | `List<Long>`              | Host mail-row ids, one per mail (several when the host splits)      |
| `itemErrors`     | `List<ItemDeliveryError>` | Lines dropped from a delivered mail; reference host always sends [] |

**Errors**

| Status              | When                                                                         |
| ------------------- | ---------------------------------------------------------------------------- |
| `VALIDATION_FAILED` | Missing `charId` / `title`, malformed or unknown-template item line          |
| `NOT_FOUND`         | Recipient character does not exist, or the host's mail manager rejects input |
| `INVALID_STATE`     | Offline traders (or the recipient) still being restored after a server start |
| `INTERNAL_ERROR`    | Delivery failed, or a repeat while the first attempt's receipt is still open |

A repeat of a completed correlation id replays the stored `createdMailIds`.

**Side effects on success.** Cascading `character` resync of the recipient
(its items included).

---

### `SendPlayerMailCommand`

**Purpose.** Send a mail from a character (the character-sender counterpart
of `SendMailCommand`): text, items taken from the sender's inventory, adena
and cash on delivery (COD). The sender may be online or offline. One recipient
per command. All-or-nothing: no partial mails. Idempotent per `idempotencyKey`
through the host's durable receipts (each attempt has its own `correlationId`); a replay returns the stored `mailId`
and `feeAdena`. There is no `offlineAllowed` field; the daily quota is
enforced on the platform, the host does not gate mail by quota. Full contract:
[`send-player-mail.md`](send-player-mail.md).

**Inputs**

| Field             | Type                   | Required | Notes                                                                                                                                                                      |
| ----------------- | ---------------------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `senderCharId`    | `Long`                 | yes      | Character the mail is sent from                                                                                                                                            |
| `recipientCharId` | `Long`                 | yes      | Single recipient, MUST differ from the sender; same account as the sender is allowed                                                                                       |
| `title`           | `String`               | yes      | Non-blank; at most 128 chars and 255 UTF-8 bytes; text allowlist                                                                                                           |
| `body`            | `String?`              | no       | `null` = empty; at most 512 chars; text allowlist                                                                                                                          |
| `items`           | `List<PlayerMailItem>` | no       | Lines `PlayerMailItem{Long itemId, Long count}`: sender inventory instance id, positive count, no duplicate id; an adena stack is refused, adena goes only through `adena` |
| `adena`           | `Long?`                | no       | Adena in the mail; positive; counts as one attachment line (limit and fee)                                                                                                 |
| `codPrice`        | `Long?`                | no       | COD, positive, requires at least one `items` line (`COD_REQUIRES_ITEMS`), adena alone does not satisfy it; paid to the sender                                              |
| `ownerVerified`   | `boolean`              | no       | See Cross-cutting › Owner verification; `true` bypasses the item lock. The command is produced only for the owner of the sender; the host still validates the wire         |
| `idempotencyKey`  | `UUID`                 | yes      | Business identity of the send, stable across retries; non-null; host dedups and replays by it, `correlationId` is one attempt only                                         |
| `deadline`        | `Instant`              | yes      | Non-null; checked first, host replies `COMMAND_EXPIRED` before touching anything once passed                                                                               |

**Result** (`SendPlayerMailResult`)

| Field      | Type   | Notes                                              |
| ---------- | ------ | -------------------------------------------------- |
| `mailId`   | `long` | Created mail                                       |
| `feeAdena` | `long` | Adena the host charged (host-defined fee; never 0) |

**Errors** — `extensions.reason` per status (full table in the command spec):

| Status              | Reasons                                                                                                                                                                                                                                                                                                                                          |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `NOT_FOUND`         | `RECIPIENT_NOT_FOUND` (also a recipient pending deletion)                                                                                                                                                                                                                                                                                        |
| `FORBIDDEN`         | `CHARACTER_BANNED`, `RECIPIENT_BANNED`, `CHARACTER_JAILED`, `TRANSACTIONS_FORBIDDEN`, `ITEM_LOCKED`, `RECIPIENT_BLOCKED_SENDER`, `RECIPIENT_GM_ONLY`                                                                                                                                                                                             |
| `VALIDATION_FAILED` | `SELF_SEND`, `TOO_MANY_ATTACHMENTS`, `TOO_MANY_LINES`, `ADENA_OVERFLOW`, `COD_REQUIRES_ITEMS`, `ITEM_NOT_TRANSFERABLE`; malformed command, carrying a `field` extension instead of a reason (null required field, id out of `int` range, bad count, duplicate `itemId`, adena stack in `items`, text outside the allowlist, title/body too long) |
| `INVALID_STATE`     | `NOT_ENOUGH_ADENA`, `RECIPIENT_INBOX_FULL`, `OUTBOX_FULL`, `CHARACTER_BUSY`, `NOT_IN_PEACE_ZONE` (attachments while the sender, online or by its last known position, is outside a peace zone), `MAIL_DISABLED`, `ATTACHMENTS_DISABLED`, `LEVEL_TOO_LOW`, `RESTORE_IN_PROGRESS`                                                                  |
| `COMMAND_EXPIRED`   | its own status; `deadline` passed, nothing changed                                                                                                                                                                                                                                                                                               |
| `INTERNAL_ERROR`    | `MAIL_DELIVERY_FAILED` (dirty rollback / persist failure); `OUTCOME_UNKNOWN` for a replay that finds the first attempt in flight (platform holds its guard, not retry-safe)                                                                                                                                                                      |

---

### `AcceptMailCommand`

**Purpose.** The recipient-side counterpart of `SendPlayerMailCommand`: accept the attachments of one
mail and pay its COD, for a receiver that may be online or offline. All-or-nothing: the mail is fully
accepted or nothing is charged and nothing moves. Idempotent per `idempotencyKey` through the host's
durable receipts, with the same unknown-outcome semantics as the send. The host MAY charge a receive
fee; it refuses with `RECEIVE_FEE_REQUIRED` (fee amount in the extension) until the caller retries with
`acceptedFeeAdena`. Refusing/returning and deleting a mail stay in the game client. Design only, not
released. Full contract: [`accept-mail.md`](accept-mail.md).

**Inputs**

| Field                  | Type      | Required | Notes                                                                                                   |
| ---------------------- | --------- | -------- | ------------------------------------------------------------------------------------------------------- |
| `receiverCharId`       | `Long`    | yes      | Character that accepts; a different recipient on the live mail is `MAIL_NOT_FOUND`                      |
| `mailId`               | `Long`    | yes      | Mail to accept                                                                                          |
| `expectedSenderCharId` | `Long`    | yes      | Sender the player saw; a different sender on the live mail is `MAIL_NOT_FOUND`                          |
| `expectedCodAdena`     | `Long`    | yes      | COD the player saw, `0` without COD; a mismatch with the live mail is `MAIL_NOT_FOUND`, nothing charged |
| `acceptedFeeAdena`     | `Long?`   | no       | Consent to the receive fee: the largest fee the player agreed to; `null` = no consent yet               |
| `ownerVerified`        | `boolean` | no       | See Cross-cutting › Owner verification; `true` bypasses the item lock                                   |
| `idempotencyKey`       | `UUID`    | yes      | Business identity of the accept, stable across retries; non-null; host dedups and replays by it         |
| `deadline`             | `Instant` | yes      | Non-null; checked first, host replies `COMMAND_EXPIRED` before touching anything once passed            |

**Result** (`AcceptMailResult`)

| Field             | Type                     | Notes                                                                     |
| ----------------- | ------------------------ | ------------------------------------------------------------------------- |
| `mailId`          | `long`                   | Accepted mail                                                             |
| `items`           | `List<MailItemMovement>` | Moved items, the same type the `MailAccepted` event carries; may be empty |
| `codPaidAdena`    | `long`                   | COD paid to the sender; `0` without COD                                   |
| `receiveFeeAdena` | `long`                   | Receive fee the host charged; `0` when none                               |

**Errors** — `extensions.reason` per status (`WellKnownAcceptMailReasons`; full table in the command spec):

| Status              | Reasons                                                                                                                                                                                                                                                                                                                     |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NOT_FOUND`         | `MAIL_NOT_FOUND` (also a different recipient, sender or COD)                                                                                                                                                                                                                                                                |
| `FORBIDDEN`         | `CHARACTER_BANNED`, `TRANSACTIONS_FORBIDDEN`, `ITEM_LOCKED`, `RECEIVE_FORBIDDEN`                                                                                                                                                                                                                                            |
| `INVALID_STATE`     | `NO_ATTACHMENTS`, `INVENTORY_FULL` (+ `requiredSlots`, `freeSlots`), `OVERWEIGHT` (+ `requiredWeight`, `freeWeight`), `NOT_ENOUGH_ADENA` (+ `requiredAdena`, `availableAdena`), `ADENA_OVERFLOW`, `RECEIVE_FEE_REQUIRED` (+ `feeAdena`), `RECEIVER_DATA_UNAVAILABLE`, `CHARACTER_BUSY`, `STORE_OPEN`, `RESTORE_IN_PROGRESS` |
| `VALIDATION_FAILED` | malformed command, carrying a `field` extension                                                                                                                                                                                                                                                                             |
| `COMMAND_EXPIRED`   | its own status; `deadline` passed, nothing changed                                                                                                                                                                                                                                                                          |
| `INTERNAL_ERROR`    | `OUTCOME_UNKNOWN` (replay finds the first attempt in flight, or the commit outcome is unknown; platform holds its guard, not retry-safe). A problem only, no payload: the caller treats an accept as done only on status `OK`                                                                                               |

An active jail sanction is `RECEIVE_FORBIDDEN` regardless of the receiver's location. A receiver with an open
store at the keyboard is `STORE_OPEN` (close the store first); an offline trader with an open store MAY accept.
After an accept the mail keeps its COD amount, only the attachments are removed.

**Events.** `MailAccepted` is the platform's only source for the mail's new state. The host MUST publish
it for every accept (this command and the native in-game accept) even when `attachments` is empty, and
SHOULD publish `MailReturned` / `MailDeleted` (side `RECEIVER`) when the host's expiry returns or deletes
a mail. After an accept the attachments are removed and the mail stays; a `MailAccepted` lost after the
commit is re-published by the host's recovery with the original completion time in its `eventId`, so consumers
MUST be idempotent and MUST NOT move the accepted time on a duplicate.

---

### `ReadMailCommand`

**Purpose.** Mark one mail as read by its receiver, whether the receiver is online or offline. Moves nothing and
charges nothing: no `idempotencyKey`, no `deadline`, not `OwnerVerified`. Idempotent: an already-read mail answers
`OK`. The platform learns the new state only from the `MailRead` event. Full contract:
[`read-mail.md`](read-mail.md).

**Inputs**

| Field    | Type   | Required | Notes                                                                               |
| -------- | ------ | -------- | ----------------------------------------------------------------------------------- |
| `charId` | `Long` | yes      | The receiver; a different character on the live mail is `MAIL_NOT_FOUND`            |
| `mailId` | `Long` | yes      | The host's mail id (an `int` in the core), the value `MailReadEvent.mailId` carries |

**Result** — none (`Void`): the command is `NxCommand<Void>` and the reply is `CommandResult<Void>`, `ok()` with no
typed payload. There is no `ReadMailResult` class; that name is only the routing name the reply travels under
(`{X}Command` → `{X}Result`).

**Errors** — `extensions.reason` per status (`WellKnownReadMailReasons`):

| Status              | Reasons                                                            |
| ------------------- | ------------------------------------------------------------------ |
| `NOT_FOUND`         | `MAIL_NOT_FOUND` (also a mail whose receiver is another character) |
| `VALIDATION_FAILED` | malformed command, carrying a `field` extension                    |
| `INTERNAL_ERROR`    | host-side failure                                                  |

---

## Offline fishing commands

Full contract, reason-code table and edge flows: [`offline-fishing.md`](./offline-fishing.md). Session mechanics:
[`038-app-offline-sessions`](../038-app-offline-sessions.md).

### `StartOfflineFishingCommand`

**Purpose.** Place a character in the world without a client and make it fish for a chosen time with a chosen rod and
bait. The host decides every rule; the reply carries a reason code from the shared enum on refusal. Idempotent by
`idempotencyKey` (the session's `idempotency_key`).

**Inputs**

| Field             | Type      | Required | Notes                                                                                                                                                                   |
| ----------------- | --------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `characterId`     | `int`     | yes      | Character that goes fishing                                                                                                                                             |
| `masterAccountId` | `UUID`    | yes      | Master account of the initiator, set by the platform; used for the one-fishing limit                                                                                    |
| `initiatorIp`     | `String?` | no       | IP of the app request, set by the platform; stored in the session and returned in the session event                                                                     |
| `durationMs`      | `long`    | yes      | Chosen duration, positive; outside the host's minimum..maximum it is refused with `INVALID_DURATION`; a longer-than-allowed-today value yields a shorter effective plan |
| `rodItemId`       | `int`     | yes      | Inventory instance id of the chosen rod                                                                                                                                 |
| `baitItemId`      | `int`     | yes      | Inventory instance id of the chosen bait stack                                                                                                                          |
| `ownerVerified`   | `boolean` | no       | See Cross-cutting › Owner verification; absent reads as `false`                                                                                                         |
| `deadline`        | `Instant` | yes      | Moment after which the host MUST refuse with `COMMAND_EXPIRED`; checked before anything is read or changed                                                              |
| `idempotencyKey`  | `UUID`    | yes      | Business identity of the start, stable across retries; the host dedups and replays by it; `correlationId` is one attempt only                                           |

The initiator is the envelope's `CommandInitiator`.

**Result** (`StartOfflineFishingResult`)

| Field                 | Type     | Notes                                                                    |
| --------------------- | -------- | ------------------------------------------------------------------------ |
| `sessionId`           | `UUID`   | The created (or, on a repeat, the existing) session                      |
| `spotPool`            | `String` | Pool of the spot the character stands on                                 |
| `spotId`              | `int`    | The spot inside the pool                                                 |
| `effectiveDurationMs` | `long`   | The duration the host will actually run, possibly shorter than requested |

**Errors.** A refusal carries `extensions.reason` from the shared enum: `RECENTLY_LOGGED_OUT` and `COOLDOWN` (with
`retryAt`), `CHARACTER_ONLINE`, `DEAD`, `KARMA`, `CHARACTER_JAILED`, `CHARACTER_BANNED`, `UNSAFE_LOGOUT_LOCATION`, `IN_PRIVATE_STORE`,
`NO_ROD`, `NO_BAIT`, `INVALID_ROD`, `INVALID_BAIT`, `INVALID_DURATION`, `FEATURE_DISABLED`, `DAILY_LIMIT`, `RESTART_SOON`,
`NO_FREE_SPOT`, `ALREADY_ACTIVE` (with `scope` `CHARACTER` / `MASTER_ACCOUNT` / `ACCOUNT` / `HWID`), `COMMAND_EXPIRED`. Status per
reason: `NO_ROD` / `NO_BAIT` and every other state refusal are `INVALID_STATE`; `INVALID_ROD` / `INVALID_BAIT` /
`INVALID_DURATION` are `VALIDATION_FAILED`; `CHARACTER_BANNED` / `CHARACTER_JAILED` are `FORBIDDEN`.

---

### `StopOfflineFishingCommand`

**Purpose.** End the character's running offline fishing session (end reason `APP_STOPPED`). Stopping a character with no
running session is an idempotent edge flow, see [`offline-fishing.md`](./offline-fishing.md).

**Inputs.** `characterId`, `masterAccountId`, `initiatorIp`, `ownerVerified`, `deadline`, `idempotencyKey` (same meaning
as on start; the session is addressed by `characterId`).

**Result** (`StopOfflineFishingResult`)

| Field       | Type      | Notes                                                                                                                      |
| ----------- | --------- | -------------------------------------------------------------------------------------------------------------------------- |
| `sessionId` | `UUID?`   | The session that was stopped; `null` when no session was running                                                           |
| `stopped`   | `boolean` | `true` when a running session was stopped; stop with no running session is the success `{sessionId: null, stopped: false}` |

---

## Privatestore commands

### `StartPrivateStoreSellCommand`

**Purpose.** Open a regular ("sell one by one") private store on behalf
of a character, listing the given inventory stacks at their asked prices.
Executed by the host's private-store subsystem on the character's game
thread. **Strict all-or-nothing start:** if ANY requested line cannot be listed,
the host rejects the whole command with `LINES_REJECTED` (`VALIDATION_FAILED`),
the store is not opened, nothing is charged and the character is not moved; the
per-line reasons travel in `dropped[]` in the problem extension. Partial stores
(open with the accepted lines, report the rest) no longer exist. The host MUST
judge each line with the same item availability checks as its native
private-store listing path, not a reduced subset (a stack the in-game store
window would not list is not listable here either).

**Offline-only start.** The command opens a store only for a character that is NOT in the game. A character online with
a client is refused with `CHARACTER_ONLINE`; a character that left the game less than N minutes ago (N from the host's
app settings file, default 2) is refused with `RECENTLY_LOGGED_OUT` plus `retryAt`, the same rule as for offline
fishing. There is no online start. Every store started by this command is an app `OFFLINE_PRIVATE_STORE` session
(see [`038`](../038-app-offline-sessions.md)), so the seller fee always lives in the session's `type_state`.

**Start cooldown.** A start of a character whose previous app private-store session (sell, package sell or buy, open or
closed) really started less than N minutes ago is refused with `COOLDOWN` (`INVALID_STATE`) plus `retryAt` (that start
plus N), against dumping prices by restarting the store. N is `app.private-store.start-cooldown-minutes` of the host's app
settings file (default 5, `0` turns the rule off). The scope is the character: another character of the same master
account is not held back, and a store opened in the game does not count. The host reads the previous start through
`NxAppSessions.findLastStartedByCharacter(characterId, OFFLINE_PRIVATE_STORE)` ([`038`](../038-app-offline-sessions.md) R21, R23);
a failed read refuses the start as temporarily unavailable, never as "no previous session". Every start command applies
it (sell, package sell, buy); `StopPrivateStoreCommand` never waits for it.

**Inputs**

| Field              | Type             | Required | Notes                                                                                                                                                                                                                                                                                                                                                                                                                |
| ------------------ | ---------------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId`           | `int`            | yes      | Character to open the store for                                                                                                                                                                                                                                                                                                                                                                                      |
| `title`            | `String?`        | no       | Store banner text shown above the seller, max 29 characters, host-sanitized. `null` falls back to the host's default                                                                                                                                                                                                                                                                                                 |
| `lines`            | `List<SellLine>` | yes      | Offered stacks, non-empty. Each `SellLine`: `itemId` (`int`, inventory instance object-id), `count` (`long`, positive), `priceAdena` (`long`, non-negative — `0` is a valid give-away price; engine charges `count * priceAdena` for the whole stack)                                                                                                                                                                |
| `sellerFeePercent` | `Integer?`       | no       | Seller fee, whole percent, not negative; `null` = no fee. The platform decides the rate; the host rejects a negative percent and one above `100` (proceeds cannot go negative). Declared on the sell branch (the abstract class shared by `StartPrivateStoreSellCommand` and `StartPrivateStorePackageSellCommand`), not on the base `StartPrivateStoreCommand`: a buy store has no seller. See **Seller fee** below |
| `offlineAllowed`   | `Boolean`        | no       | Platform's verdict on the offline-store quota, taken before dispatch. `false`: the host refuses with `OFFLINE_QUOTA_EXCEEDED` (`INVALID_STATE`) before loading anything. `null` (older platform) reads as allowed                                                                                                                                                                                                    |
| `ownerVerified`    | `boolean`        | no       | `true` only when the platform verified, against fresh master-account data, that the acting user owns the character; absent reads as `false`. See Cross-cutting › Owner verification                                                                                                                                                                                                                                  |
| `deadline`         | `Instant`        | yes      | Moment after which the host MUST refuse with `COMMAND_EXPIRED` instead of running the command; checked before anything is read or changed. `null` (older platform) reads as "no expiry"                                                                                                                                                                                                                              |

The start commands also carry `idempotencyKey` (`UUID`, business identity of the start, becomes the session's
`idempotency_key`) and `initiatorIp` (`String?`, IP of the app request, stored in the session), both declared on the
shared base `StartPrivateStoreCommand` (as are `charId`, `title`, `offlineAllowed`, `ownerVerified` and `deadline`); the platform sends them already while the host still ignores them (an older
host parses commands leniently and drops unknown fields).

**Result** (`StartPrivateStoreResult`)

| Field           | Type      | Notes                                                                                                                                                  |
| --------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `sessionId`     | `UUID`    | The app `OFFLINE_PRIVATE_STORE` session the store runs in; the platform writes it to its audit                                                         |
| `storeType`     | `String`  | Open-string store-type token the host opened (e.g. `"SELL"` / `"PACKAGE_SELL"` / `"BUY"`); host-defined vocabulary, not a closed adapter enum          |
| `acceptedCount` | `int`     | Number of requested lines the host actually listed                                                                                                     |
| `offline`       | `boolean` | Always `true` for a successful start: the store is opened offline (it occupies an offline seat); there is no online start. Kept for wire compatibility |

**Per-line rejections.** A `VALIDATION_FAILED` reply with reason `LINES_REJECTED` carries `dropped[]` in the problem extension; each
`DroppedLine` is `lineIndex` (`int`, required, `>= 0`, 0-based index into the command's `lines`), `itemId` (`Integer?`) and
`reason` (`String`, open `UPPER_SNAKE_CASE` token; consumers MUST tolerate unknown tokens). The host fills `lineIndex` for
every line, sell and buy alike. A sell line also carries its inventory `itemId`; a buy line names a template, not an
instance, so its `itemId` is `null`. Known reasons (buy lines use the subset in
[`StartPrivateStoreBuyCommand`](#startprivatestorebuycommand)):

| Reason                 | Meaning                                                                                  |
| ---------------------- | ---------------------------------------------------------------------------------------- |
| `NOT_FOUND`            | No such stack in the character's inventory (for example sold or moved since it was read) |
| `NOT_TRADEABLE`        | The item cannot be traded                                                                |
| `ITEM_BLOCKED`         | The item is blocked from private stores by host policy                                   |
| `EQUIPPED`             | The item is currently equipped                                                           |
| `BAD_COUNT`            | The count is not available in the stack                                                  |
| `PRICE_OVERFLOW`       | `count * priceAdena` exceeds the host's adena limit                                      |
| `ADENA`                | The item is the host's currency and cannot be listed                                     |
| `PET_CONTROL_ITEM`     | The item controls a summoned companion                                                   |
| `ENCHANTING`           | The item is in use by an active enchant operation                                        |
| `SKILL_CONSUMABLE`     | The item is being consumed by the skill the character is casting                         |
| `PET_COLLAR_NOT_EMPTY` | The item is a companion collar whose companion still carries items                       |
| `NOT_AVAILABLE`        | The host's availability checks refuse the line without a finer code                      |

**Errors**

| Status              | When                                                                                                                                                                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `NOT_FOUND`         | `charId` does not exist on this server                                                                                                                                                                                                                 |
| `INVALID_STATE`     | `CHARACTER_ONLINE` (the character is in the game with a client) or `RECENTLY_LOGGED_OUT` (with `retryAt`); see **Offline-only start**. `COOLDOWN` (with `retryAt`); see **Start cooldown**                                                             |
| `VALIDATION_FAILED` | `lines` missing/empty, or any `SellLine` entry is malformed. When any line cannot be listed the reply is `VALIDATION_FAILED` with reason `LINES_REJECTED`: the store is not opened and the problem extension carries `dropped[]` with per-line reasons |
| `INVALID_STATE`     | The character cannot open a store right now (in combat, already trading, dead, …)                                                                                                                                                                      |
| `COMMAND_EXPIRED`   | `deadline` already passed when the host picked up the command; nothing changed                                                                                                                                                                         |

---

### `StartPrivateStorePackageSellCommand`

**Purpose.** Open an all-or-nothing "package sell" private store — a
buyer must purchase every listed line in one transaction, rather than
picking lines individually as with `StartPrivateStoreSellCommand`. Reply,
required/optional fields, and partial-acceptance semantics are identical
to `StartPrivateStoreSellCommand` (source Javadoc states no further
detail beyond that equivalence).

**Inputs**

| Field              | Type             | Required | Notes                                                                                                                                                                                                   |
| ------------------ | ---------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId`           | `int`            | yes      | Character to open the store for                                                                                                                                                                         |
| `title`            | `String?`        | no       | Store banner text shown above the seller, max 29 characters, host-sanitized. `null` falls back to the host's default                                                                                    |
| `lines`            | `List<SellLine>` | yes      | Bundled stacks, non-empty, all-or-nothing at purchase time (same `SellLine` shape as `StartPrivateStoreSellCommand`)                                                                                    |
| `sellerFeePercent` | `Integer?`       | no       | Same as on `StartPrivateStoreSellCommand`; `null` = no fee                                                                                                                                              |
| `offlineAllowed`   | `Boolean`        | no       | Same semantics as on `StartPrivateStoreSellCommand`: `false` refuses with `OFFLINE_QUOTA_EXCEEDED`; the start is offline-only too (`CHARACTER_ONLINE` / `RECENTLY_LOGGED_OUT`); `null` reads as allowed |
| `ownerVerified`    | `boolean`        | no       | `true` only when the platform verified, against fresh master-account data, that the acting user owns the character; absent reads as `false`. See Cross-cutting › Owner verification                     |
| `deadline`         | `Instant`        | yes      | Moment after which the host MUST refuse with `COMMAND_EXPIRED` instead of running the command; checked before anything is read or changed. `null` (older platform) reads as "no expiry"                 |

**Reasons.** Refusals carry a stable `extensions.reason` code (the platform localizes; known values include `IN_COMBAT`, `SELLING_BUFFS`, `CANNOT_ACT`, `NOT_IN_TRADE_ZONE`, `NO_FREE_SEAT`, `ADENA_OVERFLOW`, `TOO_MANY_LINES`, `CHARACTER_BANNED`, `CHARACTER_JAILED`, `OFFLINE_FEE_UNPAID`, `OFFLINE_QUOTA_EXCEEDED` (`INVALID_STATE`; `offlineAllowed = false`), `CHARACTER_ONLINE` and `RECENTLY_LOGGED_OUT` (`INVALID_STATE`, the latter with `retryAt`; start is offline-only), `COOLDOWN` (`INVALID_STATE` with `retryAt`; see **Start cooldown** under `StartPrivateStoreSellCommand`), `RESTORE_IN_PROGRESS`, `COMMAND_EXPIRED`, `ITEM_LOCKED`, `TRANSACTIONS_FORBIDDEN`, `DUPLICATE_ITEM_ID`, `LINES_REJECTED`, `TITLE_TOO_LONG`, `STORE_NOT_OPEN`, and for the buy start `NOT_ENOUGH_ADENA` and `DUPLICATE_ITEM_TEMPLATE_ID`; the set is open; the start and stop tokens are the constants of the one enum `PrivateStoreReason`, read with its lenient `fromToken`). When any line cannot be listed the reply is `VALIDATION_FAILED` with reason `LINES_REJECTED` carries `dropped[]` with per-line reasons (table above); the store is not opened. The per-line `reason` is as precise as the host can make it and falls back to `NOT_AVAILABLE` for a line the host's native availability checks refuse without a finer code. A duplicate `itemId` in `lines` is `VALIDATION_FAILED` (`DUPLICATE_ITEM_ID`). One reason set applies to the start and stop commands alike (the stop uses `STORE_NOT_OPEN`).

**Result** (`StartPrivateStoreResult`) — same shape as
`StartPrivateStoreSellCommand`'s result, above.

**Errors** — same as `StartPrivateStoreSellCommand`, above.

**Seller fee (`sellerFeePercent`, both start commands).** When non-null and greater than `0`, the host
remembers the rate with the store and, on EVERY sale from that store (a platform buy and an in-game
purchase alike), withholds `ceilDiv(totalPrice * sellerFeePercent, 100)` from the seller's proceeds and
burns it. The buyer pays the lot price only (plus the buyer fee on a platform buy, see
`BuyFromPrivateStoreCommand`). Rounded up, so at a price of 1 adena the seller receives 0. The rate ends
with the store, whatever ends it (close, sold out, logout, not restored): a store opened later in game
carries no fee, and stores opened in game never have a seller fee. The rate is the platform's decision; a negative percent, or one above `100` (the
proceeds would go negative), is rejected with `VALIDATION_FAILED`, never clamped. Deserialization bypasses the constructor check, so the host re-validates it. Staff-started stores carry the same rate: every platform start command sets it.

---

### `StartPrivateStoreBuyCommand`

**Purpose.** Open a buy store ("wanted" list) on behalf of a character: the owner states which item templates it buys, how
many and at what price per unit; any seller, in the game or later from the app, fills the lines. Same rail as the sell
starts: executed by the host's private-store subsystem on the character's game thread, offline-only start (see
**Offline-only start** under `StartPrivateStoreSellCommand`), every store is an app `OFFLINE_PRIVATE_STORE` session
([`038`](../038-app-offline-sessions.md)). **Strict all-or-nothing start**: if ANY line cannot be listed the host rejects the
whole command with `LINES_REJECTED`, nothing is charged. The host judges each line with the same item rules as its native
buy-store listing path.

Adena is NOT reserved at start (same as the native in-game buy store): the start only checks that the owner can cover the
whole list plus the buyer fee (and the offline-mode price when it is paid in adena), and every later fill re-checks the owner's
adena (price plus fee); a fill the owner cannot cover is refused. A restore of the store does not re-check adena. A restore is
all-or-nothing like the sell stores: if the list cannot be rebuilt as stored, the store is not restored. The host does not
match a fill by anything but the template and the price.

**Inputs**

| Field             | Type               | Required | Notes                                                                                                                 |
| ----------------- | ------------------ | -------- | --------------------------------------------------------------------------------------------------------------------- |
| `charId`          | `int`              | yes      | Character to open the store for                                                                                       |
| `title`           | `String?`          | no       | Store banner text, max 29 characters, host-sanitized. `null` falls back to the host's default                         |
| `lines`           | `List<WantedLine>` | yes      | Wanted items, non-empty; the host's buy-store slot limit applies (`TOO_MANY_LINES`). See **WantedLine** below         |
| `buyerFeePercent` | `Integer?`         | no       | Buyer fee, whole percent, not negative; `null` / `0` = no fee. The platform decides the rate. See **Buyer fee** below |
| `offlineAllowed`  | `Boolean`          | no       | Same as on `StartPrivateStoreSellCommand`                                                                             |
| `ownerVerified`   | `boolean`          | no       | Same as on `StartPrivateStoreSellCommand`                                                                             |
| `deadline`        | `Instant`          | yes      | Same as on `StartPrivateStoreSellCommand`                                                                             |

`idempotencyKey` and `initiatorIp` come from the shared base, as on the sell starts.

**`WantedLine`** (package `kafka.commands.privatestore.model`)

| Field            | Type   | Notes                                                                                                                             |
| ---------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `itemTemplateId` | `long` | Item template the owner buys, `> 0`                                                                                               |
| `count`          | `long` | Units wanted, `> 0`                                                                                                               |
| `priceAdena`     | `long` | Per unit, `>= 0`; `count * priceAdena` MUST NOT overflow `long` (the host also bounds each line and the total by its adena limit) |

Two lines with the same `itemTemplateId` are `VALIDATION_FAILED` with reason `DUPLICATE_ITEM_TEMPLATE_ID`; a second line of
the same template would be unreachable, because the host's fill matches by template and price only. Deserialization bypasses the constructors, so the host re-validates the lines (non-empty, bounds, duplicates),
the fee and the total: `Σ(count * priceAdena)` and the buyer fee, computed with exact arithmetic, must stay within the
host's adena limit, otherwise `ADENA_OVERFLOW`.

**Matching is host-defined.** The host's fill matches a seller's item to a line by template and price only; the adapter
contract does not define more. `WantedLine` carries no enchant level, attributes or augmentation; adding them later is additive. A buy store has
no package variant.

**Buyer fee (`buyerFeePercent`).** The mirror of the seller fee: the fee is always borne by the owner of the app store. When
non-null and greater than `0`, the host remembers the rate with the store and, on EVERY fill of it (a seller in the game
or, later, one from the app), the owner pays `totalPrice + ceilDiv(totalPrice * buyerFeePercent, 100)`; the surcharge is
burned and the seller receives exactly the listed price. The rate ends with the store, whatever ends it; stores opened in
the game never carry a fee. A negative percent is `VALIDATION_FAILED`, never clamped; the rate is the platform's decision;
deserialization bypasses the constructor check, so the host re-validates it. The rate lives in the session's `type_state`
([`038`](../038-app-offline-sessions.md)); the purchase event reports the fee of each fill
([`019`](../019-death-status-ratings-events.md) R27).

**Result** (`StartPrivateStoreResult`) — same shape as `StartPrivateStoreSellCommand`'s result, with `storeType = "BUY"`;
`acceptedCount` is the number of wanted lines listed.

**Per-line reasons.** `LINES_REJECTED` carries `dropped[]` as for the sell starts, with `itemId = null` and `lineIndex` set.
Reasons a buy line can get (meanings in the table under `StartPrivateStoreSellCommand`): `NOT_FOUND` (unknown template,
or the owner holds none of it: the game client offers a buy line only for a template in the inventory, so the host
requires one held when the store opens; a restored store with such a line, or with adena, is closed whole), `NOT_TRADEABLE` (quest items included), `BAD_COUNT` (a non-stackable template
with `count > 1`), `PRICE_OVERFLOW`, `ADENA` (the currency item cannot be wanted), `NOT_AVAILABLE`. The stack-bound
reasons (`EQUIPPED`, `PET_CONTROL_ITEM`, ...) do not apply: a buy line holds no stack, only proof that one is held. The tokens are the constants of
the enum `DroppedLineReason` (lenient `fromToken`; the problem extension name is `DroppedLine.EXTENSION`).

**Errors**

| Status              | When                                                                                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NOT_FOUND`         | `charId` does not exist on this server                                                                                                                                |
| `INVALID_STATE`     | `CHARACTER_ONLINE`, `RECENTLY_LOGGED_OUT` or `COOLDOWN` (both with `retryAt`), as for the sell starts                                                                 |
| `INVALID_STATE`     | `NOT_ENOUGH_ADENA`: `Σ(count * priceAdena)` plus the buyer fee, plus the offline-mode price when that price is paid in adena, exceeds the owner's adena at start      |
| `VALIDATION_FAILED` | `lines` missing/empty, a `WantedLine` is malformed, `buyerFeePercent` negative, `DUPLICATE_ITEM_TEMPLATE_ID`, or `LINES_REJECTED` with `dropped[]`                    |
| `VALIDATION_FAILED` | The other start reasons, as for the sell starts: `TOO_MANY_LINES`, `ADENA_OVERFLOW`, `TITLE_TOO_LONG`                                                                 |
| `INVALID_STATE`     | The character cannot open a store right now (in combat, already trading, dead, ...), `OFFLINE_QUOTA_EXCEEDED` (`offlineAllowed = false`), and the other start reasons |
| `COMMAND_EXPIRED`   | `deadline` already passed when the host picked up the command; nothing changed                                                                                        |

---

### `StopPrivateStoreCommand`

**Purpose.** Close whatever private store a character currently has open
(sell, package-sell, or buy). Executed by the host's private-store
subsystem on the character's game thread. No separate stop exists for a buy store: the same command closes a buy store
started by `StartPrivateStoreBuyCommand` (`previousStoreType = "BUY"`).

The command closes ANY private store of the player's own character: an app `OFFLINE_PRIVATE_STORE` session, a native `.offline`
store or a live player's store. Only the start is offline-only and only the start waits for the start cooldown: a stop
is never refused with `COOLDOWN`. For an app session the stop closes the session (reason
`APP_STOPPED`) and applies the return rule (origin only if the session lasted less than the return threshold `app.session.return-threshold-minutes` of the app settings file, the same as for fishing; otherwise the character stays at the store spot); for a store not started from the app it just closes the store.

**Inputs**

| Field           | Type      | Required | Notes                                                                                                                                                                                   |
| --------------- | --------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `charId`        | `int`     | yes      | Character whose open store to close                                                                                                                                                     |
| `ownerVerified` | `boolean` | no       | `true` only when the platform verified, against fresh master-account data, that the acting user owns the character; absent reads as `false`. See Cross-cutting › Owner verification     |
| `deadline`      | `Instant` | yes      | Moment after which the host MUST refuse with `COMMAND_EXPIRED` instead of running the command; checked before anything is read or changed. `null` (older platform) reads as "no expiry" |

**Result** (`StopPrivateStoreResult`)

| Field               | Type     | Notes                                                                                                                                                                                               |
| ------------------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `previousStoreType` | `String` | Open-string store-type token that was open before this command closed it (e.g. `"SELL"` / `"SELL_MANAGE"` / `"PACKAGE_SELL"` / `"BUY"` / `"BUY_MANAGE"` / `"MANUFACTURE"`); host-defined vocabulary |

**Errors**

| Status            | When                                                              |
| ----------------- | ----------------------------------------------------------------- |
| `NOT_FOUND`       | `charId` does not exist / is not online on this server            |
| `INVALID_STATE`   | The character has no private store open (reason `STORE_NOT_OPEN`) |
| `COMMAND_EXPIRED` | `deadline` already passed; nothing changed                        |

---

### `BuyFromPrivateStoreCommand`

**Purpose.** Buy the given lots from another character's open sell-store
on behalf of `buyerCharId` — the remote ("buy now") counterpart of the
in-game store purchase packet. The buyer does NOT have to be online, in
range, or in the same world instance as the seller. All-or-nothing: either
every line is bought at exactly the requested count and price, or nothing
is charged and nothing moves — the host validates all lots against the
seller's live trade list before entering the engine, so a stale order book
fails the command instead of silently buying less than the caller saw.
Every non-OK reply also carries a stable machine-readable `reason` code in
`CommandProblem.extensions` (with the numeric context of that reason —
required vs available adena / slots / weight — in sibling extension keys);
the platform localizes the code, the host never sends player-facing text.
The delivery mail's text is the platform's too: `mailSender` / `mailSubject` /
`mailBody` arrive ready-made in the buyer's language and the host writes them
verbatim — it composes no text of its own.

**Inputs**

| Field             | Type            | Required | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ----------------- | --------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `buyerCharId`     | `int`           | yes      | Character paying for the goods. Need not be online — the host loads an offline character for the duration of the deal                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `sellerCharId`    | `int`           | yes      | Character whose open sell-store is being bought from. MUST be in the world (online or offline-trading) with a sell-store open. MUST differ from `buyerCharId`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `lines`           | `List<BuyLine>` | yes      | Lots to buy, non-empty, no duplicate `itemId`; more lines than the host's own delivery-mail attachment limit are refused with `TOO_MANY_LINES`. Each `BuyLine`: `itemId` (`int`, the instance object-id the buyer saw — primary identity key), `itemTemplateId` (`long`), `enchantLevel` (`Integer?`, `0..127`, `null` if the offer carried none), `attributes` (`Map<Attribute,Integer>`, empty if the offer carried none), `count` (`long`, positive — host buys exactly this many or fails, never silently shrinks), `unitPriceAdena` (`long`, non-negative). Fields beyond `itemId` are an optimistic lock, not a search filter — a mismatch against the live lot fails the whole command rather than buying something else |
| `buyerFeePercent` | `Integer`       | yes      | Buyer-side fee in whole percent (`5` = 5%) charged on top of the lot price and burned: `ceilDiv(itemsTotal * pct, 100)`. A negative value is rejected with `VALIDATION_FAILED`, never clamped; the platform decides the rate. Fractional rates unsupported; `0` means no fee. `null` is `VALIDATION_FAILED` (absence is never read as `0`)                                                                                                                                                                                                                                                                                                                                                                                      |
| `deadline`        | `Instant`       | yes      | Moment after which the host MUST refuse to execute this command instead of running it — checked before resolving the seller or touching the seller's trade list. Guards against a command sitting in the Kafka backlog while the game-server was down                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `mailSender`      | `String`        | yes      | Author shown on the delivery mail, non-blank. Platform-authored, player-facing, already localized; written verbatim                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `mailSubject`     | `String`        | yes      | Subject of the delivery mail, non-blank. Same rules as `mailSender`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `mailBody`        | `String`        | yes      | Body of the delivery mail, non-blank, final text (no placeholders). Same rules as `mailSender`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `ownerVerified`   | `boolean`       | no       | `true` only when the platform verified, against fresh master-account data, that the acting user owns the character; absent reads as `false`. See Cross-cutting › Owner verification                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |

**Result** (`BuyFromPrivateStoreResult`)

| Field             | Type               | Notes                                                                                                                                                                                                                                |
| ----------------- | ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `itemsTotalAdena` | `long`             | Lot price total the buyer pays for the items; the seller is credited this minus `sellerFeeAdena`                                                                                                                                     |
| `buyerFeeAdena`   | `long`             | Buyer fee burned: debited from the buyer on top of the lots, credited to nobody                                                                                                                                                      |
| `sellerFeeAdena`  | `long`             | Seller fee burned: withheld from `itemsTotalAdena` before the seller is credited; `0` when the store carries none. The seller receives `itemsTotalAdena - sellerFeeAdena`                                                            |
| `paidTotalAdena`  | `long`             | Total debited from the buyer; always `itemsTotalAdena + buyerFeeAdena` (the seller fee is not paid by the buyer)                                                                                                                     |
| `bought`          | `List<BoughtLine>` | The executed lots. Each `BoughtLine`: `itemId`, `itemTemplateId`, `enchantLevel` (`Integer?`), `count`, `unitPriceAdena` (seller price only — the burned surcharge is not included per-line, it is reported once on `buyerFeeAdena`) |
| `storeClosed`     | `boolean`          | `true` when this deal emptied the seller's store and the host closed it — the caller drops the whole store from its order book instead of decrementing the bought lots                                                               |
| `mailId`          | `long`             | Id of the delivery mail carrying the bought items; eventual-consistent — resolving it via the mail-read API immediately after this reply may still 404 until mail-ingest catches up                                                  |

**Errors**

| Status              | When                                                                                                                                                                                    |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VALIDATION_FAILED` | Malformed `lines`, `TOO_MANY_LINES` (more lines than the host's delivery-mail attachment limit), blank mail text, `buyerFeePercent` missing, or buyer and seller are the same character |
| `NOT_FOUND`         | The seller is not in the world or has no open sell-store                                                                                                                                |
| `INVALID_STATE`     | The lot no longer matches the request, the store type is not served, or the buyer cannot receive the goods (adena, weight, slots, regulated combat, …)                                  |
| `FORBIDDEN`         | The buyer is barred from trading at all (security lock, cursed weapon, restricted account)                                                                                              |
| `COMMAND_EXPIRED`   | `deadline` has already passed when the host picked up the command; nothing moved                                                                                                        |

---

### `SellToPrivateStoreCommand`

**Purpose.** Sell the given items of `sellerCharId` into another character's open buy-store (`BID`, opened in the game
or from the app, see `StartPrivateStoreBuyCommand`) — the mirror of `BuyFromPrivateStoreCommand`. The seller does NOT
have to be online, in range, or in the same world instance as the store owner; the owner is always in the world (online
or offline-trading) and receives the items directly. All-or-nothing: either every line is sold at exactly the requested
count and price, or nothing is charged and nothing moves. What the app player gives (the items) is taken directly; what
the app player gets (the proceeds in adena) is delivered by a durable system mail. Every non-OK reply carries a stable
machine-readable `reason` code in `CommandProblem.extensions` (with the context of that reason in sibling keys, e.g.
`changedLines`, `itemIds`); the platform localizes the code, the host never sends player-facing text. The mail text is
the platform's too: `mailSender` / `mailSubject` / `mailBody` arrive ready-made in the seller's language and the host
writes them verbatim.

**Inputs**

| Field              | Type             | Required | Notes                                                                                                                                                                                                                                                                                                                                                                                 |
| ------------------ | ---------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sellerCharId`     | `int`            | yes      | Character giving the items and receiving the proceeds. Need not be online — the host loads an offline character for the duration of the deal                                                                                                                                                                                                                                          |
| `buyerCharId`      | `int`            | yes      | Owner of the buy-store. MUST be in the world with a buy-store open. MUST differ from `sellerCharId`                                                                                                                                                                                                                                                                                   |
| `lines`            | `List<SellLine>` | yes      | Lots to sell, non-empty, no duplicate `itemId`; the platform caps the count. Each `SellLine`: `itemId` (`int`, the instance object-id in the seller's inventory), `count` (`long`, positive), `priceAdena` (`long`, the buy-store line's unit price the seller saw). The item template is taken from the instance |
| `sellerFeePercent` | `Integer`        | yes      | Seller-side fee in whole percent (`5` = 5%) withheld from the proceeds and burned: `ceilDiv(itemsTotal * pct, 100)`. Negative or above `100` (the proceeds would go negative) is rejected with `VALIDATION_FAILED`, never clamped; the platform decides the rate. `0` means no fee. `null` is `VALIDATION_FAILED` (absence is never read as `0`)                                      |
| `deadline`         | `Instant`        | yes      | Same as on `BuyFromPrivateStoreCommand`                                                                                                                                                                                                                                                                                                                                               |
| `mailSender`       | `String`         | yes      | Author shown on the proceeds mail, non-blank. Platform-authored, already localized; written verbatim                                                                                                                                                                                                                                                                                  |
| `mailSubject`      | `String`         | yes      | Subject of the proceeds mail, non-blank. Same rules as `mailSender`                                                                                                                                                                                                                                                                                                                   |
| `mailBody`         | `String`         | yes      | Body of the proceeds mail, non-blank, final text (no placeholders). Same rules as `mailSender`                                                                                                                                                                                                                                                                                        |
| `ownerVerified`    | `boolean`        | no       | `true` only when the platform verified, against fresh master-account data, that the acting user owns the character; absent reads as `false`. See Cross-cutting › Owner verification                                                                                                                                                                                                   |

Deserialization bypasses the constructor, so the host re-validates ids, lines, duplicates, the fee and the mail text.

**Matching.** For each line the host finds the buy-store line whose item template is the template of the instance
`itemId` and whose price equals `priceAdena` exactly; `count` must not exceed the line's remainder. Enchant level,
attributes and augmentation are not compared, as in the native sale into a buy-store. A mismatch (no such line, another
price, a smaller remainder, the store closed or of another kind) refuses the whole command with `OFFER_CHANGED` and
`changedLines`; nothing is charged. There is no partial execution. The seller's item is found by `itemId` only, with no
fall back to another item of the same template. It must pass the host's native item-manipulation check (ownership,
count, non-stackable = `1`, pet control item, item in an active enchant, augmented item during a cast) and be tradeable;
otherwise `SELLER_ITEM_UNAVAILABLE` with `itemIds`. The owner's adena (`itemsTotal` plus the owner's fee) is checked
before anything moves; weight and slots of the owner are not checked, as in the native sale.

**Fees.** Everyone who acts from the app pays. The seller fee is withheld from the proceeds: the mail carries
`receivedTotal = itemsTotal - sellerFee`, `sellerFee = ceilDiv(itemsTotal * sellerFeePercent, 100)`. The owner of a store
opened from the app also pays its buyer fee on top of the price, `ceilDiv(itemsTotal * buyerFeePercent, 100)` with the
rate of the store (see **Buyer fee** under `StartPrivateStoreBuyCommand`); a store opened in the game carries none. Both
fees are burned.

**Result** (`SellToPrivateStoreResult`)

| Field                | Type             | Notes                                                                                                                                                                             |
| -------------------- | ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `itemsTotalAdena`    | `long`           | Lot price total the store owner pays for the items                                                                                                                                |
| `sellerFeeAdena`     | `long`           | Seller fee burned: withheld from `itemsTotalAdena` before the seller is credited; `0` at rate `0`. In `0..itemsTotalAdena`                                                        |
| `receivedTotalAdena` | `long`           | Credited to the seller by mail; always `itemsTotalAdena - sellerFeeAdena`                                                                                                         |
| `buyerFeeAdena`      | `long`           | The store owner's fee burned: debited from the owner on top of the lots; `0` for a store opened in the game. The owner pays `itemsTotalAdena + buyerFeeAdena`                     |
| `sold`               | `List<SoldLine>` | The executed lots. Each `SoldLine`: `itemId` (the seller's instance), `itemTemplateId`, `enchantLevel` (`Integer?`), `count`, `unitPriceAdena` (the store price, no fee included) |
| `storeClosed`        | `boolean`        | `true` when this deal emptied the buy-store and the host closed it                                                                                                                |
| `mailId`             | `long`           | Id of the mail carrying the proceeds; eventual-consistent, as on `BuyFromPrivateStoreResult`                                                                                      |

**Errors**

| Status              | When                                                                                                                                                                                                                                                                                                                |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VALIDATION_FAILED` | Malformed `lines`, a duplicate `itemId`, `sellerFeePercent` missing or out of range, blank mail text, or seller and store owner are the same character                                                                                                                                            |
| `NOT_FOUND`         | The store owner is not in the world or has no open buy-store (`STORE_CLOSED`)                                                                                                                                                                                                                                       |
| `INVALID_STATE`     | `OFFER_CHANGED` (with `changedLines`), `SELLER_ITEM_UNAVAILABLE` (with `itemIds`), `STORE_OWNER_INSUFFICIENT_ADENA`, `SELLER_BUSY` (trade, craft, own store), `SELLER_LOGIN_IN_PROGRESS` (race with a login), `ADENA_OVERFLOW` |
| `FORBIDDEN`         | `SELLER_ACCESS_RESTRICTED`: the seller is barred from trading at all (ban, jail, restricted account); `SELLER_ITEM_LOCKED`: the character's item lock, unless `ownerVerified`                                                                                                                                                                                                                |
| `INTERNAL_ERROR`    | `MAIL_DELIVERY_FAILED`: the proceeds mail could not be persisted; the seller's items and the owner's adena are returned |
| `COMMAND_EXPIRED`   | `deadline` has already passed when the host picked up the command; nothing moved                                                                                                                                                                                                                                    |

**Event.** The deal is reported by the existing `PrivateStorePurchaseEvent` with `storeType = BID`, `source = APP`,
`seller_fee_*` metadata when the rate is above `0` and `buyer_fee_*` for a store opened from the app; the event api is
unchanged.

---

## Sync commands

### `ResyncEntitiesCommand`

**Purpose.** Force the db-sync engine to re-sync whole entities: every
snapshot hash of each targeted entity is invalidated so the next CDC cycle
re-publishes every live row (as `UPDATED`) and re-emits `DELETED` for
snapshot-known ghosts. Pure adapter operation — no host game code
involved. Redelivery re-enqueues the same `resyncId`; the engine merges
pending requests per entity and emits one completion event per drained
`resyncId`, so a duplicate delivery converges to the same platform-visible
outcome.

**Inputs**

| Field      | Type            | Required | Notes                                                                                                                                                                                             |
| ---------- | --------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `resyncId` | `UUID`          | yes      | Platform-generated UUIDv7 identifying the resync operation. Echoed on every `ResyncCompletedEvent` the forced cycles emit                                                                         |
| `entities` | `List<String>?` | no       | Entity names to resync. Null/empty = ALL db-sync entities declared by the adapter's schema provider. A non-empty list containing any unknown name fails the whole command — no partial acceptance |

**Result** (`ResyncEntitiesResult`)

| Field              | Type           | Notes                                                                                                                                                                 |
| ------------------ | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `acceptedEntities` | `List<String>` | Entity names actually enqueued for invalidation — the full declared set when `entities` was omitted, the validated requested set otherwise. Never empty on a real ack |

**Errors**

| Status              | When                                                                                            |
| ------------------- | ----------------------------------------------------------------------------------------------- |
| `VALIDATION_FAILED` | Missing `resyncId`, or any name in a non-empty `entities` list is not a declared db-sync entity |
| `UNAVAILABLE`       | The db-sync engine is not running (module disabled / failed / not started yet)                  |

**Note.** The reply is an ack sent after the invalidation requests are
enqueued — it does NOT wait for the forced cycles. Per-entity completion
is signalled later via `ResyncCompletedEvent` on the `sync` events family.

---

### `ResyncRowsCommand`

**Purpose.** Force the db-sync engine to re-sync selected rows of one
entity: the snapshot hash of each targeted PK is invalidated (a sentinel
entry is inserted for a PK the snapshot never had, so a platform ghost row
gets a `DELETED` re-emit) and the next CDC cycle re-publishes them. Pure
adapter operation. Redelivery merges into the same pending per-entity
invalidation set under the same `resyncId`; the platform sweep keyed on
the completion event is idempotent.

**Inputs**

| Field        | Type         | Required | Notes                                                                                                                                                                                                                                                                                                      |
| ------------ | ------------ | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `resyncId`   | `UUID`       | yes      | Platform-generated UUIDv7 identifying the resync operation                                                                                                                                                                                                                                                 |
| `entityName` | `String`     | yes      | Target entity name as declared by the adapter's schema provider (`EntityMapping.entityName()`)                                                                                                                                                                                                             |
| `pks`        | `List<Long>` | yes      | Primary keys to invalidate, non-empty, at most `MAX_PKS` = 1000 entries. A PK absent from both the snapshot and the host DB still produces a `DELETED` re-emit, repairing platform-side ghost rows                                                                                                         |
| `cascade`    | `boolean`    | no       | Default `false` (Gson primitive default). When `true`, the handler resolves — synchronously, before the ack — the rows of every declared entity whose `parentRefs()` reference `entityName` and invalidates them alongside the requested rows. Cascading from an entity nothing references is not an error |

**Result** (`ResyncRowsResult`)

| Field                 | Type                  | Notes                                                                                                                                                                                                                                                                                                                                                           |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `invalidatedByEntity` | `Map<String,Integer>` | Per-entity invalidated-row counts: the target entity maps to the number of distinct requested PKs; with `cascade=true` each child entity maps to the number of cascade-resolved rows. Entities that resolved zero cascade rows are omitted (target entity always present). Iteration order: target entity first, cascade children in provider declaration order |

**Errors**

| Status              | When                                                                                                                                       |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `VALIDATION_FAILED` | Missing `resyncId` / `entityName`, unknown `entityName`, or `pks` missing / empty / over `MAX_PKS` / carrying a null or non-positive entry |
| `UNAVAILABLE`       | The db-sync engine is not running                                                                                                          |

**Note.** The reply is an ack sent after enqueue, carrying the per-entity
invalidation counts known at ack time. Per-entity completion follows
asynchronously via `ResyncCompletedEvent`.

---

## Telegram commands

### `TelegramCharLinkCommand`

**Purpose.** Issue a Telegram-to-character link verification code. Sends
the code as an in-game system message to the resolved character; user
enters it back into the Telegram bot to confirm ownership.

**Inputs**

| Field              | Type     | Required | Notes                                                      |
| ------------------ | -------- | -------- | ---------------------------------------------------------- |
| `accountName`      | `String` | yes      | Account login owning the character                         |
| `charName`         | `String` | yes      | Character name (case-insensitive match within the account) |
| `confirmationCode` | `String` | yes      | Caller-generated short code (e.g. 6 alphanumerics)         |
| `telegramUserId`   | `Long`   | yes      | Telegram user-id for audit and uniqueness                  |

**Result** (`TelegramCharLinkResult`)

| Field    | Type   | Notes                                                                      |
| -------- | ------ | -------------------------------------------------------------------------- |
| `charId` | `Long` | Resolved character id (caller stores this against its pending-link record) |

**Errors**

| Status              | When                                                                    |
| ------------------- | ----------------------------------------------------------------------- |
| `NOT_FOUND`         | No character with that name on that account                             |
| `VALIDATION_FAILED` | Missing fields                                                          |
| `INTERNAL_ERROR`    | Could not deliver the in-game system message (rare; logged on the host) |

**Side effects on success.** None — link verification is a read-only
resolve + side-channel send; no sync trigger needed.

---

## Cross-cutting

### Owner verification

Commands the platform sends on behalf of a player acting with their own character implement
`OwnerVerified` and carry `ownerVerified` (spec R28): `SendChatMessageCommand`,
`StartPrivateStoreSellCommand`, `StartPrivateStorePackageSellCommand`, `StartPrivateStoreBuyCommand`,
`StopPrivateStoreCommand`, `BuyFromPrivateStoreCommand`, `SellToPrivateStoreCommand`, `SendPlayerMailCommand`, `AcceptMailCommand`, `StartOfflineFishingCommand`,
`StopOfflineFishingCommand`.

- `true` means the platform verified, against fresh master-account data, that the acting user owns
  the character. Everything else sends `false`: staff actions, socially linked characters, the
  platform speaking for itself, commands it did not verify freshly.
- It is not authorization. A host MAY use `true` only to relax checks bound to the player's device or
  game session, which a platform-issued command can never satisfy (a device-bound item lock is the
  typical one). Everything else is checked the same way regardless of the flag.

### Sync triggers

Every command that mutates persistent state calls
`ctx.sync().requestNow(entity, pk)` on the success path. Platform consumers
observe the change on the next sync cycle (typically within 1–2 s of the
reply) instead of waiting up to the configured CDC interval (default
60 s).

Mapping:

| Command                          | Triggered entities                                        |
| -------------------------------- | --------------------------------------------------------- |
| `CreateItemCommand`              | `item` (created stack), `character` (owner)               |
| `DeleteItemCommand`              | `item` (decremented/destroyed stack), `character`         |
| `TransferItemToCharacterCommand` | `item` (moved stack), `character` (from + to)             |
| `TransferCharToAccountCommand`   | `character` (re-bound)                                    |
| `SendMailCommand`                | `character` (recipient)                                   |
| `SendPlayerMailCommand`          | `character` (sender + recipient)                          |
| `AcceptMailCommand`              | `character` (receiver, and sender when COD paid)          |
| `ReadMailCommand`                | none: no `requestNow`, the `MailRead` event is the signal |
| `TelegramCharLinkCommand`        | none                                                      |

> The remaining commands added to this catalog (`announcement`, `ban`,
> `UpsertCharacterLockCommand`, `gd`, `privatestore`, `sync`) do not state
> an explicit `requestNow(...)` side effect in their DTO Javadoc, so they
> are intentionally left out of this mapping rather than guessed at.

### Status semantics

`CommandStatus` carries a `Tier` for coarse caller routing:

- **`OK`** → apply payload
- **`CLIENT_ERROR`** (`NOT_FOUND` / `INVALID_STATE` / `FORBIDDEN` /
  `VALIDATION_FAILED` / `RATE_LIMITED` / `UNSUPPORTED_COMMAND`) → surface
  to user; do not retry
- **`SERVER_ERROR`** (`UNAVAILABLE` / `INTERNAL_ERROR`) → alert ops; retry
  may succeed

`UNSUPPORTED_COMMAND` is adapter-emitted only — it means the deployed
core has no handler registered for the command type. Indicates a deploy
skew between platform and core.

### Identifier conventions

- `itemTemplateId` — catalog template (e.g. `57` = adena). Stable across
  servers and across the item's lifetime.
- `itemId` — per-stack object-id assigned by the host on creation.
  Unique per game-server lifetime; identifies one specific stack.
- `charId` — character primary key in the host DB. Stable. Spelled
  `characterId` on commands added from 2026-09 onward (`SendChatMessageCommand`, the offline-fishing commands),
  per the platform naming canon; older fields keep the short form because
  renaming a released wire field breaks the rail for no functional gain.
- All wire ids are `Long` to accommodate hosts that may use 64-bit ids;
  hosts using `int` internally bounds-check + downcast at the handler
  boundary.

### Partial success

The private-store start commands (sell, package sell, buy) are all-or-nothing (`LINES_REJECTED`);
`StartPrivateStoreResult` has no `dropped` field, per-line reasons exist only in
the refusal extension. `SendMailCommand`
keeps a partial-success field on the wire (`SendMailResult.itemErrors`, lines
dropped from a delivered mail) that consumers must read, but the reference
host delivers all-or-nothing and always sends it empty. All other commands
are all-or-nothing — either `OK` with the full effect applied, or a
non-OK status with no state change.

### Breaking changes of this release (private store, mail, runtime)

Ships in api `0.103.0` (with commons `0.12.0` and db-sync-core `0.11.0` for the items below). These commands are not open
to players yet, so the contract is changed directly, with no deprecated fields and no compatibility window:

- Renamed: `BuyFromPrivateStoreCommand.tax` is `buyerFeePercent` (`Integer`, required), `MAX_TAX_PERCENT` is
  `PrivateStoreFees.MAX_FEE_PERCENT` (one constant for both fees), `BuyFromPrivateStoreResult.taxAdena` is `buyerFeeAdena`, and the purchase-event metadata
  keys are renamed ([`019`](../019-death-status-ratings-events.md) R26).
- Removed: `StartPrivateStoreResult.dropped` and the `ALL_LINES_DROPPED` reason.
- Added: `sellerFeePercent`, `sellerFeeAdena`, the new metadata keys, `AcceptMailCommand` / `AcceptMailResult` /
  `WellKnownAcceptMailReasons`, `CharacterRuntimeDto.privateStoreSellSlots`
  ([`028`](../028-character-inventory-capacity.md)).
- Breaking (source): the canonical constructor of `CharacterRuntimeDto` takes a new last parameter,
  `privateStoreSellSlots`; callers and builders-by-constructor must pass it (`null` = not reported).
- Added (commons `0.12.0`): `UUIDv7.generate(Instant)`. Added (db-sync-core `0.11.0`): `ChildSource.rowFilter` (declarative
  `RowFilter`, bound parameters; see `003-db-sync`). Added (api `0.103.0`): `PrivateStoreFees.requirePercent` /
  `checkPercent`, `SellerFee` and `WellKnownPrivateStoreMetadata.sellerFee(metadata)`.
- Behaviour: `SendPlayerMailCommand` refuses a mail with attachments outside a peace zone with the new reason
  `NOT_IN_PEACE_ZONE` instead of `CHARACTER_BUSY`, on both branches (an offline sender is judged by its last
  known position).
- Behaviour: `LINES_REJECTED` replaces partial store opening; the host applies its native item availability
  checks; a purchase from an open store withholds the seller fee.
- **Deploy order.** Release the api, then deploy the platform and the hosts together (the platform before the
  hosts' daily restart). A mismatch window is harmless because no player-facing flow uses these commands yet.

### Changes of api `0.105.0` (mail, private store)

- Added: `ReadMailCommand` (`NxCommand<Void>`) and `WellKnownReadMailReasons`; see [`read-mail.md`](read-mail.md).
- Removed (events, breaking): `PrivateStoreSnapshotEvent` and its `Offer` line, with no deprecated copy and no
  `EventTypeRegistry` binding left. Added: `PrivateStoreStateEvent`, `StoreState`, `StoreLine`, the full state of one
  trader's store; see [`039`](../039-private-store-state-event.md) (R8 for the removal, migration gate there).

### Changes of api `0.107.0` (push facts moved out)

- Removed package `kafka.events.push` (`EffectRemovedEvent`, `ItemDepletedEvent`). These are platform-internal business facts
  on `<tenant>.push.events`, produced by the platform, not by hosts; their contract now lives outside the adapter. Hosts are
  not affected (no host produces or reads them); the wire is unchanged.

### Changes of the api release after `0.109.0` (private store buy start)

- **Breaking (source):** `DroppedLine.itemId` changes from `int` to `Integer` (`null` for a buy line, so
  `getItemId()` returns `Integer`) and `DroppedLine.lineIndex` is a required `int`, filled by the host for every line
  (the constructor takes `int`; the builder throws when it is unset). Old hosts omit `lineIndex`; a consumer reading the raw
  problem JSON treats a missing key as absent; `lines` and `sellerFeePercent` (`getLines()` / `getSellerFeePercent()`) move from the base
  `StartPrivateStoreCommand` to a new abstract sell branch shared by `StartPrivateStoreSellCommand` and
  `StartPrivateStorePackageSellCommand`. Callers that read them through the base type, or unbox `itemId`, must be adapted.
  The sell commands keep the same JSON keys and values; only the Gson field order changes. JSON key order is not part of
  the contract.
- Added: `StartPrivateStoreBuyCommand` (wire type = class name), `WantedLine`, `DroppedLine.lineIndex`, the reasons
  `NOT_ENOUGH_ADENA`, `DUPLICATE_ITEM_ID` and `DUPLICATE_ITEM_TEMPLATE_ID` and
  `PrivateStoreMetadata.buyerFee(metadata)`. Both fees are one value type, `PrivateStoreFee` (`adena`, `percent`); there is
  no `BuyerFee` and `SellerFee` is renamed to `PrivateStoreFee`.
- **Breaking (source), wire-compatible:** the private-store vocabularies are enums, the constant holders are removed with no
  deprecated copy. `WellKnownStartPrivateStoreReasons` is `PrivateStoreReason` (every start and stop reason the catalog lists, one
  set for both commands, including `STORE_NOT_OPEN`; its `DROPPED` is `DroppedLine.EXTENSION`), `WellKnownDroppedLineReasons` is `DroppedLineReason`,
  `WellKnownPrivateStoreMetadata` is `PrivateStoreMetadataKey` (wire key via `key()`), and its `sellerFee` / `buyerFee`
  parsers move to `PrivateStoreMetadata`. Each enum has a lenient `fromToken` that returns `null` for an unknown token, backed by one shared
  `app.l2nx.gs.adapter.api.Vocabularies.fromToken(Class, String)` (a per-enum cached map); `SessionSource` gets the same
  `fromToken`.
  The wire tokens and `DroppedLine.reason` (still a `String`) are unchanged; callers that referenced the old classes
  must be adapted.
- **Breaking (wire): the purchase `source` metadata reuses `SessionSource`.** Its values are `CLIENT` (a deal made in a
  game client) and `APP` (a deal made by an app command), the vocabulary sessions already use; the former
  `in_game` / `remote` values and their `SOURCE_IN_GAME` / `SOURCE_REMOTE` constants are gone. The host sends only
  the new values from this release on; the platform reads both forms until every game server of the tenant restarted
  on the new host.
- **Breaking (wire): no fee rate cap in the api.** `PrivateStoreFees` and `MAX_FEE_PERCENT` (50, shipped in `0.103.0`) are
  removed. Every fee percent (`sellerFeePercent`, `buyerFeePercent`, the purchase `buyerFeePercent`) validates only `>= 0`;
  the seller fee is also `<= 100`, because proceeds cannot go negative (`StartPrivateStoreSellingCommand` rejects more,
  `PrivateStoreMetadata.sellerFee` returns `null` for it). The rate is the platform's decision.
- **Breaking (source):** `BuyFromPrivateStoreCommand.MAX_LINES` and its constructor check are removed: the line limit is the host's
  own delivery-mail attachment limit, not an api constant. The host refuses more lines with `VALIDATION_FAILED` / `TOO_MANY_LINES`.
  `DroppedLine` rejects a blank `reason` like a `null` one, and `StartPrivateStoreCommand.title` is trimmed, a blank one reads `null`.
- Added: `WantedLine.firstDuplicateItemTemplateId(lines)`, mirroring `SellLine.firstDuplicateItemId` (it replaces the
  unreleased `firstDuplicate`).
- Rollout: release the api, then the platform; a host that has not restarted answers `UNSUPPORTED_COMMAND` for the new
  command, and the host picks it up on its next restart. The `source` change above is the one that straddles: the platform
  keeps reading the legacy `in_game` / `remote` values until every game server has restarted on the new host.

### Changes of api `0.113.0` (private store sell-to-store)

- Added (additive): `SellToPrivateStoreCommand` (wire type = class name, `OwnerVerified`), `SellToPrivateStoreResult` and
  `SoldLine`. No existing member changes; the event api is unchanged (`PrivateStorePurchaseEvent` with `storeType = BID`).
- Rollout: release the api, then the platform; a host that has not restarted answers `UNSUPPORTED_COMMAND` for the new
  command, and the host picks it up on its next restart.
