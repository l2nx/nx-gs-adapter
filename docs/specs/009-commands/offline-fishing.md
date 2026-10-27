# Commands - offline fishing

> Owner: @n1rmata

## Problem

A player wants their character to fish while they are not in the game client. The platform sends a start command with the
player's choices (duration, rod, bait); the host decides whether the character may go fishing, places it on a fishing
spot without a client and runs the catch cycles; the player or a limit ends it later. The platform needs stable
machine-readable refusal codes to show the player why a start was refused and when to retry, and an idempotent start so
a retried request never creates a second session.

The session mechanics (table, lifecycle, events, restart) are in
[`038-app-offline-sessions`](../038-app-offline-sessions.md); this spec is the command contract.

## Requirements

> Siblings: [`spec.md`](spec.md) (rail: `OwnerVerified`, `COMMAND_EXPIRED`), [`catalog.md`](catalog.md) (wire entry),
> [`send-player-mail.md`](send-player-mail.md) (reference for the reason/receipt conventions).

**Must:**

- [done] R1. `StartOfflineFishingCommand` and `StopOfflineFishingCommand` MUST ship as `NxCommand`s routed by their simple
  names, with result types `StartOfflineFishingResult` and `StopOfflineFishingResult`.
- [done] R2. `StartOfflineFishingCommand` payload. The canonical protection, dedup and idempotency fields are the ones the
  private-store and mail commands already use (`ownerVerified` per [`spec.md`](spec.md) R28, `deadline` per R23,
  `idempotencyKey` as in [`send-player-mail.md`](send-player-mail.md) R6). The initiator is the command's `initiator`
  (`CommandInitiator`, [`spec.md`](spec.md) R30), not a payload field: the session row stores `initiator.userId` as
  `master_account_id` (the host reads it back through the session-history read for the master-account limit, which
  counts app sessions only) and `initiator.ip` as `initiator_ip` (a placeholder as `null`, returned in the session
  event). The `masterAccountId` and `initiatorIp` fields left the payload with R30 (not yet released):

  | Field               | Type       | Required | Notes                                                                                                                                                                                                                                 |
  | ------------------- | ---------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `characterId`       | `int`      | yes      | Character that goes fishing                                                                                                                                                                                                           |
  | `durationMs`        | `long`     | yes      | Chosen duration, positive. The player picks it from options the frontend builds from the character's `usage`; the host checks it against the minimum and maximum of its app settings and refuses with `INVALID_DURATION` outside them |
  | `rodItemId`         | `int`      | yes      | Inventory instance id (not a template id) of the rod the player chose                                                                                                                                                                 |
  | `baitItemId`        | `int`      | yes      | Inventory instance id of the bait stack the player chose                                                                                                                                                                              |
  | `fishingShotItemId` | `Integer?` | no       | Inventory instance id of the fishing-shot stack the player chose (R9); `null` fishes without shots                                                                                                                                    |
  | `ownerVerified`     | `boolean`  | no       | See R28 in [`spec.md`](spec.md); absent reads as `false`                                                                                                                                                                              |
  | `deadline`          | `Instant`  | yes      | Non-null; checked first, the host replies `COMMAND_EXPIRED` before touching anything once passed                                                                                                                                      |
  | `idempotencyKey`    | `UUID`     | yes      | Business identity of the start, stable across retries; non-null; becomes the session's `idempotency_key` (R5). `correlationId` identifies one delivery attempt only                                                                   |

  Any command that changes the game world carries these canonical fields.

- [done] R3. `StartOfflineFishingResult`: `sessionId` (`UUID`), `spotPool` (`String`), `spotId` (`int`) and
  `effectiveDurationMs` (`long`) - the duration the host will actually run, which may be shorter than requested (R7). The
  platform shows the plan; the duration options themselves are built by the frontend, so no option list or ladder is
  computed in the backend contract.
- [done] R4. `StopOfflineFishingCommand` ends the character's running session. Payload: `characterId`, `ownerVerified`,
  `deadline`, `idempotencyKey`, plus the command's `initiator` (the same fields and meaning as on start; the session is
  addressed by `characterId`). The result is `StopOfflineFishingResult {sessionId, stopped}`. Stopping a character that has no
  running session is an idempotent success `{sessionId: null, stopped: false}` (see the edge-flow table).
- [done] R5. Start MUST be idempotent: `idempotencyKey` is the session's `idempotency_key`; a repeated command finds the
  session it created and returns the same result, never a second session.
- [done] R6. Every refusal MUST carry a stable `reason` from the shared enum below in `CommandProblem.extensions`;
  the host never sends player-facing text. `RECENTLY_LOGGED_OUT` and `COOLDOWN` also carry `retryAt` (`Instant`);
  `DAILY_LIMIT` carries it as the moment the daily allowance resets, when the host knows that moment.
  `ALREADY_ACTIVE` carries a `scope`.
- [host rule] R7. A duration longer than what the character may still fish today (the daily stop) is not a refusal: the session
  runs shorter and the result shows the effective plan. Rod and bait are the player's choice: the host checks that the
  items belong to the character, the rod is a rod, the bait is allowed for fishing and its count is above zero. When the
  chosen bait runs out the session ends with `APP_OUT_OF_BAIT`; it never switches to another bait.
- [host rule] R8. The host enforces every rule: the platform delivers the command and shows the answer, the adapter
  carries the command, the session and the rule parameters but evaluates no rule. The tenant-agnostic parameters are keys
  of the adapter's app settings file `l2nx-app.properties` ([`038`](../038-app-offline-sessions.md) R20), read by the host
  through `NxAppRules`; host-only rules stay in the host's own configuration. The host reads the session facts a rule
  needs (previous start, open sessions of a master account) through the adapter's session-history read (`038` R21),
  never from the table. The reason codes below are the contract. A session type the server has disabled is refused with
  `FEATURE_DISABLED`.
- [done] R9. **Fishing shot.** A start MAY name a fishing shot (`fishingShotItemId`). The host checks that the item
  belongs to the character and its count is above zero (`NO_FISHING_SHOT`), that it is a fishing shot
  (`INVALID_FISHING_SHOT`), and that its grade fits the chosen rod (`FISHING_SHOT_GRADE_MISMATCH`). During the session
  the host charges one shot before every cast, as its own fishing-shot use does, and stores the choice in the session's
  type state. When the stack runs out the session goes on without shots: running out of shots is not an end reason.
  What a shot does to a catch is the host's game mechanics, not part of this contract. Implemented in the adapter, not yet released.
- [done] R10. **`CheckOfflineFishingCommand`** is the check command of this session type ([`spec.md`](spec.md)
  R31): the start's payload without `idempotencyKey` and with every player choice optional, result
  `AppSessionCheckResult`. It runs the start's checks in the start's order, `COOLDOWN`, `RECENTLY_LOGGED_OUT` and
  `DAILY_LIMIT` with `retryAt` as on start, and the daily stop as `effectiveDurationMs`, and changes nothing. It is how the platform shows a cooldown or
  a missing item before the player presses start. Implemented in the adapter, not yet released.
- [done] R11. **Running session in the activity usage.** While the session runs, the host fills the `OFFLINE_FISHING`
  activity-usage row's metadata with `remaining`, `elapsed`, `rodItemId`, `baitItemId`, `fishingShotItemId`, `spotPool`
  and `spotId` ([`017`](../017-character-runtime-activity.md) R17), so every device of the player sees the plan and the
  gear, not only the one that started it. Implemented in the adapter, not yet released.
- [done] R12. **IP limit.** The app session counts in the host's per-IP fishing limit (scope `IP`, a new
  `AlreadyActiveScope` value) under `initiator.ip` of the start. Live fishing counts app sessions under that IP too.
  The IP passes `ClientIdentifiers`; a placeholder limits nothing. The limit itself is a host rule, off
  when the host's per-IP limit is off. Implemented in the adapter, not yet released.

**Non-goals:**

- A command that queries the available durations or spots. The frontend builds duration options from the character's
  activity usage ([`017`](../017-character-runtime-activity.md)); the backend specs carry no ladder algorithm. The check
  command (R10) answers "would this start now", it lists nothing.
- Trust-level gating of the start, shop for fishermen, potions, quest flows, a "fishing for one hour" bot task: backlog
  items on the platform side.
- Player-facing text of the refusals.

### Reason codes

Shared enum in `nx-gs-adapter-api` (names are `UPPER_SNAKE_CASE`):

| Reason code                   | Meaning                                                                                                                                                                                                                   | Extras             |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| `RECENTLY_LOGGED_OUT`         | The character left the game less than `app.session.recent-logout-minutes` ago (default 2); the leave time is recorded on every client disconnect                                                                          | `retryAt`          |
| `COOLDOWN`                    | The previous app fishing session of the same master account (any character) started too recently: less than `app.fishing.start-cooldown-minutes` (default 15) since its real start; `retryAt` = that start + the cooldown | `retryAt`          |
| `CHARACTER_ONLINE`            | The character is in the game with a client                                                                                                                                                                                |                    |
| `DEAD`                        | The character is dead                                                                                                                                                                                                     |                    |
| `KARMA`                       | The character has karma                                                                                                                                                                                                   |                    |
| `CHARACTER_JAILED`            | The character is in jail                                                                                                                                                                                                  |                    |
| `CHARACTER_BANNED`            | The character or account is banned                                                                                                                                                                                        |                    |
| `UNSAFE_LOGOUT_LOCATION`      | The character logged out in a place from which a session may not start (olympiad, event, instance, siege)                                                                                                                 |                    |
| `IN_PRIVATE_STORE`            | The character has a private store open                                                                                                                                                                                    |                    |
| `NO_ROD`                      | The chosen rod is missing                                                                                                                                                                                                 |                    |
| `NO_BAIT`                     | The chosen bait is missing or its count is zero                                                                                                                                                                           |                    |
| `INVALID_ROD`                 | The chosen item is not a rod of this character                                                                                                                                                                            |                    |
| `INVALID_BAIT`                | The chosen item is not an allowed bait for this character                                                                                                                                                                 |                    |
| `NO_FISHING_SHOT`             | The chosen fishing shot is missing or its count is zero                                                                                                                                                                   |                    |
| `INVALID_FISHING_SHOT`        | The chosen item is not a fishing shot                                                                                                                                                                                     |                    |
| `FISHING_SHOT_GRADE_MISMATCH` | The chosen fishing shot's grade does not fit the chosen rod                                                                                                                                                               |                    |
| `DAILY_LIMIT`                 | The daily fishing allowance is used up; `retryAt` = the moment it resets, when the host knows it                                                                                                                          | `retryAt`          |
| `RESTART_SOON`                | A scheduled restart is closer than `app.session.restart-window-minutes` (default 15 minutes)                                                                                                                              |                    |
| `INVALID_DURATION`            | `durationMs` is outside `app.fishing.min-duration-minutes`..`app.fishing.max-duration-minutes`                                                                                                                            |                    |
| `FEATURE_DISABLED`            | The session type is turned off on this server (`app.fishing.enabled`)                                                                                                                                                     |                    |
| `NO_FREE_SPOT`                | Every spot of the pool is taken                                                                                                                                                                                           |                    |
| `ALREADY_ACTIVE`              | The character already has an open app session, or a limit of one fishing is hit                                                                                                                                           | `scope`, see below |

**`ALREADY_ACTIVE` scopes** (`AlreadyActiveScope`, one per refusal, the first limit hit):

| Scope            | Limit                                                                                                                                                        |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `CHARACTER`      | the character already has an open app session of any type (the engine's `CHARACTER_BUSY`, one active session per character)                                  |
| `MASTER_ACCOUNT` | another fishing app session of the same master account is open; counts app sessions only, because the host does not know the master account of a live player |
| `ACCOUNT`        | the game account already fishes, live or through the app                                                                                                     |
| `HWID`           | the device (the session's hwid snapshot) already fishes, live or through the app; a missing or placeholder hwid is `null` and limits nothing                 |
| `IP`             | the IP already fishes, live or through the app (R12); an app session counts under the start's `initiator.ip`                                                  |

Until R12 ships there is no IP scope and app sessions are not counted in any per-IP limit.

`CHARACTER_BANNED` and `CHARACTER_JAILED` are the same codes the other character-acting commands use ([`spec.md`](spec.md)
R29). The set of codes is the design's. The `CommandStatus` carrying each follows the convention of
[`send-player-mail.md`](send-player-mail.md#status-mapping):

| Status              | Reason codes                                                                                             |
| ------------------- | -------------------------------------------------------------------------------------------------------- |
| `INVALID_STATE`     | `NO_ROD`, `NO_BAIT`, `NO_FISHING_SHOT`, and every other state refusal not listed below                   |
| `VALIDATION_FAILED` | `INVALID_ROD`, `INVALID_BAIT`, `INVALID_FISHING_SHOT`, `FISHING_SHOT_GRADE_MISMATCH`, `INVALID_DURATION` |
| `FORBIDDEN`         | `CHARACTER_BANNED`, `CHARACTER_JAILED`                                                                   |

A character that is already fishing in the game client counts for the account and device limits as an offline session
does (those limits cover live and offline fishing together); the master-account limit sees app sessions only. A live login
of the fishing character closes its session (`APP_LOGIN`, login is never blocked); a live login of another character of
the same account keeps the session, and that character's own live fishing is then refused by the account limit.

### Edge-flow table

| Situation                                                                                   | Result                                                                                                                                                                                                                                                                                                                                                                                                     |
| ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Duplicate start (same request id)                                                           | The stored result of the first start; no second session                                                                                                                                                                                                                                                                                                                                                    |
| Start with a different request id while a session runs                                      | `ALREADY_ACTIVE` (scope by which limit hit, `CHARACTER` for the same character)                                                                                                                                                                                                                                                                                                                            |
| Start while the character is online                                                         | `CHARACTER_ONLINE`                                                                                                                                                                                                                                                                                                                                                                                         |
| Start within N minutes of leaving the game                                                  | `RECENTLY_LOGGED_OUT` + `retryAt`                                                                                                                                                                                                                                                                                                                                                                          |
| Start before the cooldown has passed                                                        | `COOLDOWN` + `retryAt`                                                                                                                                                                                                                                                                                                                                                                                     |
| Start close to a scheduled restart                                                          | `RESTART_SOON`                                                                                                                                                                                                                                                                                                                                                                                             |
| Login of the character mid-session                                                          | Session closes with `APP_LOGIN` before the live character loads; the end presence event is emitted ([`038`](../038-app-offline-sessions.md) R9)                                                                                                                                                                                                                                                            |
| Login of another character of the same account                                              | Session continues                                                                                                                                                                                                                                                                                                                                                                                          |
| Restart mid-session                                                                         | Session restored once the host's world is loaded (`hostReady`), on the same spot (or a free one of the pool); presence event with `resumed = true`. No free spot: `APP_RESTORE_FAILED`. Until the restore reaches it the session keeps its account and device limit slots and its spot: the host reads every open fishing session with `NxAppSessions.findOpen` right before `hostReady` and reserves them |
| Bait runs out                                                                               | Session ends `APP_OUT_OF_BAIT`                                                                                                                                                                                                                                                                                                                                                                             |
| Fishing shots run out                                                                       | Session goes on without shots (R9)                                                                                                                                                                                                                                                                                                                                                                         |
| Check (any outcome)                                                                         | `OK` with `allowed` and the first `reason`; no lock, no reservation, nothing stored (R10)                                                                                                                                                                                                                                                                                                                  |
| Inventory full or overweight at a catch                                                     | Session ends `APP_INVENTORY_FULL`; checked at every catch                                                                                                                                                                                                                                                                                                                                                  |
| Daily allowance reached while running                                                       | Session ends `APP_DAILY_LIMIT`                                                                                                                                                                                                                                                                                                                                                                             |
| Duration longer than today's remaining allowance                                            | Not a refusal: shorter effective plan in the result                                                                                                                                                                                                                                                                                                                                                        |
| Duration outside the host's minimum..maximum                                                | `INVALID_DURATION`                                                                                                                                                                                                                                                                                                                                                                                         |
| Session type disabled in the app settings file                                              | `FEATURE_DISABLED`; running sessions finish                                                                                                                                                                                                                                                                                                                                                                |
| Stop of a session that is not running                                                       | Idempotent success `{sessionId: null, stopped: false}`; nothing changes                                                                                                                                                                                                                                                                                                                                    |
| Stop while running                                                                          | Session closes `APP_STOPPED`; an `active_ms` below `app.session.return-threshold-minutes` (one threshold for all session types, 30 minutes by default) returns the character to its origin, the threshold or more leaves it on the fishing spot                                                                                                                                                            |
| Stop before the post-restart restore reached the character                                  | Restore is cancelled, session closes `APP_STOPPED_BEFORE_RESTORE`; the character never re-entered the world, the return rule applies                                                                                                                                                                                                                                                                       |
| Stop while the server is down                                                               | Never reaches the host: the platform answers `409 SERVER_OFFLINE` before dispatch; the session stays open and is restored after the start                                                                                                                                                                                                                                                                  |
| All spots taken                                                                             | `NO_FREE_SPOT`                                                                                                                                                                                                                                                                                                                                                                                             |
| Ban or kick of the character                                                                | Session ends `BANNED` / `KICKED`                                                                                                                                                                                                                                                                                                                                                                           |
| The game stops the fishing loop (teleport, rod or bait vanished, the loop fails to restart) | Session ends `APP_INTERRUPTED`: not a staff kick and not the player's stop                                                                                                                                                                                                                                                                                                                                 |

### Session end

There is no dedicated end event. On every end the adapter emits the existing `CharacterPresenceEvent` with
`online = false`, `source = APP`, `activity = OFFLINE_FISHING` and the end reason in the required `logoutReason`
(`APP_TIME_UP`, `APP_STOPPED`, `APP_STOPPED_BEFORE_RESTORE`, `APP_LOGIN`, `APP_OUT_OF_BAIT`, `APP_DAILY_LIMIT`, `APP_INVENTORY_FULL`,
`APP_INTERRUPTED`, `BANNED`, `KICKED`, `APP_RESTORE_FAILED`). `APP_INTERRUPTED` (api 0.115.0) goes out in order: adapter
enum, then the platform enum and its CHECK, then the host. A session report is an initial design only, not approved and not part of the contract, see
[`038`](../038-app-offline-sessions.md) › "Session report - initial design, not approved (separate design session
pending)". Fishing notifications are a deferred, separate task: the platform can later derive "fishing ended" from the
activity usage (the `FISHING` row's `activeSince` clears, for live and offline fishing alike).

## Technical design

### Wire shape

Same rail as the other commands: `Nx-Message-Type = StartOfflineFishingCommand` / `StopOfflineFishingCommand` /
`CheckOfflineFishingCommand`, `Nx-Correlation-Id` (stable per logical start), `Nx-Target-Server-Id`.
Permission on the platform side: `GS_COMMANDS_OFFLINE_FISHING_OWN`, one permission for start, stop and check,
own-character scope. Caching and rate-limiting checks is the platform's job.

Example refusal:

```json
{
  "status": "INVALID_STATE",
  "problem": {
    "title": "Cannot start fishing",
    "extensions": {
      "reason": "RECENTLY_LOGGED_OUT",
      "retryAt": "2026-10-15T12:02:00Z"
    }
  }
}
```

### Host semantics (summary)

The handler validates against the host rules, then asks the app-session engine for an `OFFLINE_FISHING` session through
`ctx.appSessions().start(...)` ([`038`](../038-app-offline-sessions.md)) and maps the outcome: `STARTED` / `EXISTING`
to the result, `CHARACTER_BUSY` to `ALREADY_ACTIVE` with `scope = CHARACTER` (see the scope table), `SPOT_TAKEN` to
another spot or
`NO_FREE_SPOT`, `PLACEMENT_REFUSED` to its reason; stop is `ctx.appSessions().stop(characterId, OFFLINE_FISHING)`. The unknown-outcome behavior (reply lost after the session was created) is
covered by R5: the retry returns the created session. The platform's shape for an unknown outcome is the same as for the
private store start. The start stores the initiator's IP in the session; the session event returns it as `ip`, and the
character's hwid snapshot as `hwid`, both marked as coming from the app. A check runs the same handler steps
in check mode and maps the first refusal into `AppSessionCheckResult` instead of a `CommandProblem`.

## Rollout

Additive wire (new commands, new reason enum), api minor version. Until the host registers the handlers the adapter
replies `UNSUPPORTED_COMMAND` to an unknown `Nx-Message-Type` (`CommandsConsumer`) and the platform answers `501` to its
clients; the platform ships first, the permission is
granted only after the host phase ([`038`](../038-app-offline-sessions.md) Rollout).

Status: the commands, results and reason codes are released, and the reference host runs offline fishing: its start
is gated only by the server's `app.fishing.enabled` (`FEATURE_DISABLED` when off). The adapter side it needs (`038`
R20-R22: app settings file, session-history read, `ClientIdentifiers`) shipped in api 0.111.0 / core 0.45.0.

Next release (implemented in the adapter, not yet released):

- R9 (fishing shot), R10 (check), R12 (`IP` scope) add a field, a command and enum values: an api release first, then
  the platform (enums, CHECKs, REST), then the host. Until the host registers the check handler the platform gets
  `UNSUPPORTED_COMMAND`.
- R11 is host-only (the metadata map is open); the key enum ([`017`](../017-character-runtime-activity.md) R17) ships in
  the same api release.
- The initiator replaces `masterAccountId` / `initiatorIp` per [`spec.md`](spec.md) › Rollout: one api release, the
  platform straddles with its own legacy mapping until every game server restarted on the new host.

## Open questions

- [ ] Session report: initial design only, the owner's design session is pending (see
      [`038`](../038-app-offline-sessions.md)).

## Links

- Session mechanics: [`038-app-offline-sessions`](../038-app-offline-sessions.md)
- Rail: [`spec.md`](spec.md); wire entry: [`catalog.md`](catalog.md)
- Platform contract: `nx-gameservers/docs/specs/086-offline-fishing.md`
- Host documentation: not linked here, this repository does not name tenant hosts.
