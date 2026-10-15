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

- [todo] R1. `StartOfflineFishingCommand` and `StopOfflineFishingCommand` MUST ship as `NxCommand`s routed by their simple
  names, with result types `StartOfflineFishingResult` and `StopOfflineFishingResult`.
- [todo] R2. `StartOfflineFishingCommand` payload. The canonical protection, dedup and idempotency fields are the ones the
  private-store and mail commands already use (`ownerVerified` per [`spec.md`](spec.md) R28, `deadline` per R23,
  `idempotencyKey` as in [`send-player-mail.md`](send-player-mail.md) R6); the initiator is the envelope's
  `CommandInitiator`, not a payload field:

  | Field             | Type      | Required | Notes                                                                                                                              |
  | ----------------- | --------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------- |
  | `charId`          | `int`     | yes      | Character that goes fishing                                                                                                        |
  | `masterAccountId` | `UUID`    | yes      | Master account of the initiator, put in by the platform; stored in the session row and used by the host for the one-fishing limit  |
  | `initiatorIp`     | `String?` | no       | IP of the app request that triggered the command, put in by the platform; stored in the session and returned in the session event |
  | `durationMs`      | `long`    | yes      | Chosen duration, positive. The player picks it from options the frontend builds from the character's `usage`; the host checks it against the minimum and maximum of its app settings and refuses with `INVALID_DURATION` outside them |
  | `rodItemId`       | `int`     | yes      | Inventory instance id (not a template id) of the rod the player chose                                                              |
  | `baitItemId`      | `int`     | yes      | Inventory instance id of the bait stack the player chose                                                                           |
  | `ownerVerified`   | `boolean` | no       | See R28 in [`spec.md`](spec.md); absent reads as `false`                                                                           |
  | `deadline`        | `Instant` | yes      | Non-null; checked first, the host replies `COMMAND_EXPIRED` before touching anything once passed                                   |
  | `idempotencyKey`  | `UUID`    | yes      | Business identity of the start, stable across retries; non-null; becomes the session's `idempotency_key` (R5). `correlationId` identifies one delivery attempt only |

  Any command that changes the game world carries these canonical fields.

- [todo] R3. `StartOfflineFishingResult`: `sessionId` (`UUID`) and the effective plan - the duration the host will actually
  run, which may be shorter than requested (R7). The platform shows the plan; the duration options themselves are built
  by the frontend, so no option list or ladder is computed in the backend contract. The spot the character stands on is
  [NEEDS CLARIFICATION: fields of the result's spot].
- [todo] R4. `StopOfflineFishingCommand` ends the character's running session. Payload: `charId`, `masterAccountId`,
  `initiatorIp`, `ownerVerified`, `deadline`, `idempotencyKey` (the same fields and meaning as on start; the session is
  addressed by `charId`). The result fields are [NEEDS CLARIFICATION]. Stopping a character that has no running session
  is idempotent (see the edge-flow table).
- [todo] R5. Start MUST be idempotent: `idempotencyKey` is the session's `idempotency_key`; a repeated command finds the
  session it created and returns the same result, never a second session.
- [todo] R6. Every refusal MUST carry a stable `reason` from the shared enum below in `CommandProblem.extensions`;
  the host never sends player-facing text. `RECENTLY_LOGGED_OUT` and `COOLDOWN` also carry `retryAt` (`Instant`).
  `ALREADY_ACTIVE` carries a `scope`.
- [todo] R7. A duration longer than what the character may still fish today (the daily stop) is not a refusal: the session
  runs shorter and the result shows the effective plan. Rod and bait are the player's choice: the host checks that the
  items belong to the character, the rod is a rod, the bait is allowed for fishing and its count is above zero. When the
  chosen bait runs out the session ends with `OUT_OF_BAIT`; it never switches to another bait.
- [todo] R8. The host decides every rule (D1): the platform delivers the command and shows the answer. The host's rules
  and limits are configuration of the host (its app settings file, see [`038`](../038-app-offline-sessions.md) R18) and not
  part of this contract; the reason codes below are. A session type the host has disabled is refused with
  `FEATURE_DISABLED`.

**Non-goals:**

- A command that queries the available durations or spots. The frontend builds duration options from the character's
  activity usage ([`017`](../017-character-runtime-activity.md)); the backend specs carry no ladder algorithm.
- Trust-level gating of the start, shop for fishermen, potions, quest flows, a "fishing for one hour" bot task: backlog
  items on the platform side.
- Player-facing text of the refusals.

### Reason codes

Shared enum in `nx-gs-adapter-api` (names are `UPPER_SNAKE_CASE`):

| Reason code              | Meaning                                                                                                   | Extras                                         |
| ------------------------ | --------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| `RECENTLY_LOGGED_OUT`    | The character left the game less than N minutes ago (N is host configuration, default 2); the leave time is recorded on every client disconnect | `retryAt`                       |
| `COOLDOWN`               | The previous start was too recent (host configuration, default 15 minutes from the real start of the last session) | `retryAt`                                    |
| `CHARACTER_ONLINE`       | The character is in the game with a client                                                                |                                                |
| `DEAD`                   | The character is dead                                                                                     |                                                |
| `KARMA`                  | The character has karma                                                                                   |                                                |
| `CHARACTER_JAILED`       | The character is in jail                                                                                  |                                                |
| `CHARACTER_BANNED`       | The character or account is banned                                                                        |                                                |
| `UNSAFE_LOGOUT_LOCATION` | The character logged out in a place from which a session may not start (olympiad, event, instance, siege) |                                                |
| `IN_PRIVATE_STORE`       | The character has a private store open                                                                    |                                                |
| `NO_ROD`                 | The chosen rod is missing                                                                                 |                                                |
| `NO_BAIT`                | The chosen bait is missing or its count is zero                                                           |                                                |
| `INVALID_GEAR`           | The chosen items are not a valid rod / allowed bait for this character                                    |                                                |
| `DAILY_LIMIT`            | The daily fishing allowance is used up                                                                    |                                                |
| `RESTART_SOON`           | A scheduled restart is closer than the host's window (default 10 minutes)                                 |                                                |
| `INVALID_DURATION`       | `durationMs` is outside the host's minimum..maximum fishing duration                                      |                                                |
| `FEATURE_DISABLED`       | The session type is turned off in the host's app settings on this server                                  |                                                |
| `NO_FREE_SPOT`           | Every spot of the pool is taken                                                                           |                                                |
| `ALREADY_ACTIVE`         | A fishing session already runs                                                                            | `scope`: `MASTER_ACCOUNT`, `ACCOUNT` or `HWID` |

`CHARACTER_BANNED` and `CHARACTER_JAILED` are the same codes the other character-acting commands use ([`spec.md`](spec.md)
R29). The set of codes is the design's; the CommandStatus carrying each (`INVALID_STATE` for state refusals, `FORBIDDEN` for
bans and similar, `VALIDATION_FAILED` for malformed input) follows the convention of
[`send-player-mail.md`](send-player-mail.md#status-mapping) and is [NEEDS CLARIFICATION: per-code status table].
A character that is already fishing in the game client counts for the same limits as an offline session (the
one-fishing rule covers live and offline fishing together).

### Edge-flow table

| Situation                                              | Result                                                                                                                                                                                |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Duplicate start (same request id)                      | The stored result of the first start; no second session                                                                                                                               |
| Start with a different request id while a session runs | `ALREADY_ACTIVE` (scope by which limit hit)                                                                                                                                           |
| Start while the character is online                    | `CHARACTER_ONLINE`                                                                                                                                                                    |
| Start within N minutes of leaving the game             | `RECENTLY_LOGGED_OUT` + `retryAt`                                                                                                                                                     |
| Start before the cooldown has passed                   | `COOLDOWN` + `retryAt`                                                                                                                                                                |
| Start close to a scheduled restart                     | `RESTART_SOON`                                                                                                                                                                        |
| Login of the character mid-session                     | Session closes with `LOGIN` before the live character loads; the end presence event is emitted ([`038`](../038-app-offline-sessions.md) R9)                                            |
| Login of another character of the same account         | Session continues                                                                                                                                                                     |
| Restart mid-session                                    | Session restored after the host's delay on the same spot (or a free one); presence event with `resumed = true`. No free spot: `RESTORE_FAILED`                                        |
| Bait runs out                                          | Session ends `OUT_OF_BAIT`                                                                                                                                                            |
| Inventory full or overweight at a catch                | Session ends `INVENTORY_FULL`; checked at every catch                                                                                                                                 |
| Daily allowance reached while running                  | Session ends `DAILY_LIMIT`                                                                                                                                                            |
| Duration longer than today's remaining allowance       | Not a refusal: shorter effective plan in the result                                                                                                                                   |
| Duration outside the host's minimum..maximum           | `INVALID_DURATION`                                                                                                                                                                    |
| Session type disabled in the host's app settings       | `FEATURE_DISABLED`; running sessions finish                                                                                                                                           |
| Stop of a session that is not running                  | Idempotent success without effect (assumed; see open questions)                                                                                                                       |
| Stop while running                                     | Session closes `STOPPED`; an `active_ms` below the return threshold (one shared threshold of the host's app settings, 30 minutes by default) returns the character to its origin, the threshold or more leaves it on the fishing spot |
| All spots taken                                        | `NO_FREE_SPOT`                                                                                                                                                                        |
| Ban or kick of the character                           | Session ends `BANNED` / `KICKED`                                                                                                                                                      |

### Session end and report

There is no dedicated end event. On every end the adapter emits the existing `CharacterPresenceEvent` with
`online = false`, `source = APP`, `appType = FISHING` and the end reason in `metadata.logout_reason` (`time_up`,
`stopped`, `login`, `out_of_bait`, `daily_limit`, `inventory_full`, `banned`, `kicked`, `restore_failed`). It may carry
an optional `report` block; the report is an **initial design, not approved**, see
[`038`](../038-app-offline-sessions.md) › "Session report - initial design, not approved (separate design session
pending)". Fishing notifications are a deferred, separate task: the platform can later derive "fishing ended" from the activity usage
(the fishing row's `activeSince` clears, for live and offline fishing alike), adding the end event's report for an offline
session.

## Technical design

### Wire shape

Same rail as the other commands: `Nx-Message-Type = StartOfflineFishingCommand` / `StopOfflineFishingCommand`,
`Nx-Correlation-Id` (stable per logical start), `Nx-Target-Server-Id`. Permission on the platform side:
`GS_COMMANDS_OFFLINE_FISHING_OWN`, one permission for start and stop, own-character scope.

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

The handler validates against the host rules, then asks the app-session engine for a `FISHING` session
([`038`](../038-app-offline-sessions.md)). The unknown-outcome behavior (reply lost after the session was created) is
covered by R5: the retry returns the created session. The platform's shape for an unknown outcome is the same as for the
private store start. The start stores `initiatorIp` in the session; the session event returns it as `ip`, and the
character's hwid snapshot as `hwid`, both marked as coming from the app.

## Rollout

Additive wire (new commands, new reason enum), api minor version. Until the host registers the handlers the adapter
replies `UNSUPPORTED_COMMAND` and the platform answers `501` to its clients; the platform ships first, the permission is
granted only after the host phase ([`038`](../038-app-offline-sessions.md) Rollout).

## Open questions

- [ ] [NEEDS CLARIFICATION: result shape of the spot and the stop result fields]
- [ ] [NEEDS CLARIFICATION: stop of a non-running session - idempotent success (assumed) or a `NOT_FOUND`-class status]
- [ ] [NEEDS CLARIFICATION: `CommandStatus` per reason code (proposed: `INVALID_STATE` for state refusals, `FORBIDDEN` for
      bans, `VALIDATION_FAILED` for malformed input such as `INVALID_DURATION` and `INVALID_GEAR`)]
- [ ] [NEEDS CLARIFICATION: the session report block (initial design only)]

## Links

- Session mechanics: [`038-app-offline-sessions`](../038-app-offline-sessions.md)
- Rail: [`spec.md`](spec.md); wire entry: [`catalog.md`](catalog.md)
- Platform contract: `nx-gameservers/docs/specs/086-offline-fishing.md`
- Host documentation: not linked here, this repository does not name tenant hosts.
