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
- [todo] R2. `StartOfflineFishingCommand` payload:

  | Field             | Type   | Required | Notes                                                                                                                             |
  | ----------------- | ------ | -------- | --------------------------------------------------------------------------------------------------------------------------------- |
  | `charId`          | `int`  | yes      | Character that goes fishing                                                                                                       |
  | `masterAccountId` | `UUID` | yes      | Master account of the initiator, put in by the platform; stored in the session row and used by the host for the one-fishing limit |
  | `durationMs`      | `long` | yes      | Chosen duration, positive. The player picks it; the platform computes the offered options from the character's activity usage     |
  | `rodItemId`       | `int`  | yes      | Inventory instance id (not a template id) of the rod the player chose                                                             |
  | `baitItemId`      | `int`  | yes      | Inventory instance id of the bait stack the player chose                                                                          |

  Whether `ownerVerified`, `deadline` and an explicit idempotency field are also carried is [NEEDS CLARIFICATION: the
  design names the five fields above only; the platform's `requestId` (UUIDv7) maps to the session's
  `start_correlation_id`, see R5].

- [todo] R3. `StartOfflineFishingResult`: `sessionId` (`UUID`), the spot the character stands on, and the effective plan
  (the duration the host will actually run, which may be shorter than requested, see R7). Exact fields of "spot" and
  "plan" are [NEEDS CLARIFICATION].
- [todo] R4. `StopOfflineFishingCommand` ends the character's running session. Its payload (`charId` only, or `charId` and
  `sessionId`) and result fields are [NEEDS CLARIFICATION]. Stopping a character that has no running session is
  idempotent (see the edge-flow table).
- [todo] R5. Start MUST be idempotent: the platform's request id is the session's `start_correlation_id`; a repeated
  command finds the session it created and returns the same result, never a second session.
- [todo] R6. Every refusal MUST carry a stable `reason` from the shared enum below in `CommandProblem.extensions`;
  the host never sends player-facing text. `RECENTLY_LOGGED_OUT` and `COOLDOWN` also carry `retryAt` (`Instant`).
  `ALREADY_ACTIVE` carries a `scope`.
- [todo] R7. A duration longer than what the character may still fish today (the daily stop) is not a refusal: the session
  runs shorter and the result shows the effective plan. Rod and bait are the player's choice: the host checks that the
  items belong to the character, the rod is a rod, the bait is allowed for fishing and its count is above zero. When the
  chosen bait runs out the session ends with `OUT_OF_BAIT`; it never switches to another bait.
- [todo] R8. The host decides every rule (D1): the platform delivers the command and shows the answer. The host's rules
  and limits are configuration of the host and not part of this contract; the reason codes below are.

**Non-goals:**

- A command that queries the available durations or spots. The platform derives duration options from the character's
  activity usage ([`017`](../017-character-runtime-activity.md)).
- Trust-level gating of the start, shop for fishermen, potions, quest flows, a "fishing for one hour" bot task: backlog
  items on the platform side.
- Player-facing text of the refusals.

### Reason codes

Shared enum in `nx-gs-adapter-api` (names are `UPPER_SNAKE_CASE`):

| Reason code              | Meaning                                                                                                   | Extras                                         |
| ------------------------ | --------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| `RECENTLY_LOGGED_OUT`    | The character left the game less than N minutes ago (N is host configuration, default 2)                  | `retryAt`                                      |
| `COOLDOWN`               | The previous start was too recent (15 minutes from the real start of the last session)                    | `retryAt`                                      |
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
| `NO_FREE_SPOT`           | Every spot of the pool is taken                                                                           |                                                |
| `ALREADY_ACTIVE`         | A fishing session already runs                                                                            | `scope`: `MASTER_ACCOUNT`, `ACCOUNT` or `HWID` |

The set of codes is the design's; the CommandStatus carrying each (`INVALID_STATE` for state refusals, `FORBIDDEN` for
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
| Login of the character mid-session                     | Session closes with `LOGIN` before the live character loads; the report is emitted ([`038`](../038-app-offline-sessions.md) R9)                                                       |
| Login of another character of the same account         | Session continues                                                                                                                                                                     |
| Restart mid-session                                    | Session restored after the host's delay on the same spot (or a free one); `OfflineSessionResumed`. No free spot: `RESTORE_FAILED`                                                     |
| Bait runs out                                          | Session ends `OUT_OF_BAIT`                                                                                                                                                            |
| Inventory full or overweight at a catch                | Session ends `INVENTORY_FULL`; checked at every catch                                                                                                                                 |
| Daily allowance reached while running                  | Session ends `DAILY_LIMIT`                                                                                                                                                            |
| Duration longer than today's remaining allowance       | Not a refusal: shorter effective plan in the result                                                                                                                                   |
| Stop of a session that is not running                  | Idempotent success without effect [NEEDS CLARIFICATION: or a `NOT_FOUND`-class status; the design says only that stop of a non-running session is an edge flow]                       |
| Stop while running                                     | Session closes `STOPPED`; short sessions return the character to its origin, 30 minutes and more of `active_ms` leave it on the fishing spot (spot-return rule is host configuration) |
| All spots taken                                        | `NO_FREE_SPOT`                                                                                                                                                                        |
| Ban or kick of the character                           | Session ends `BANNED` / `KICKED`                                                                                                                                                      |

### Report shape

On every end the adapter emits `OfflineSessionEnded` with the end reason and a report: time, catch by kind (`itemTemplateId`

- `count`), experience, SP, adena, items. Field names are [NEEDS CLARIFICATION] and are fixed with the events in
  [`038`](../038-app-offline-sessions.md) R16.

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
private store start.

## Rollout

Additive wire (new commands, new reason enum), api minor version. Until the host registers the handlers the adapter
replies `UNSUPPORTED_COMMAND` and the platform answers `501` to its clients; the platform ships first, the permission is
granted only after the host phase ([`038`](../038-app-offline-sessions.md) Rollout).

## Open questions

- [ ] [NEEDS CLARIFICATION: `ownerVerified`, `deadline`, explicit idempotency field on the start command]
- [ ] [NEEDS CLARIFICATION: stop payload and result; behavior for a non-running session]
- [ ] [NEEDS CLARIFICATION: result shape of the spot and the effective plan]
- [ ] [NEEDS CLARIFICATION: `CommandStatus` per reason code]

## Links

- Session mechanics: [`038-app-offline-sessions`](../038-app-offline-sessions.md)
- Rail: [`spec.md`](spec.md); wire entry: [`catalog.md`](catalog.md)
- Platform contract: `nx-gameservers/docs/specs/086-offline-fishing.md`
- Host documentation: not linked here, this repository does not name tenant hosts.
