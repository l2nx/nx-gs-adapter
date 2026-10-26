# Commands - revive character

> Owner: @n1rmata
>
> Counterparts: `nx-gameservers/docs/specs/092-character-revive.md` (platform endpoint, permission, app settings),
> and the reference host's own mapping doc.

## Problem

A player leaves the game client running (for example on auto-hunt) and goes away from the computer; the character
dies. To use the character from the app again (fishing, a private store, an app-phantom), the player has to get it up
without the client. In the game the dead player picks a restart point ("to village", "to clan hall", "to castle",
"to fortress"); the app needs the same action as a command.

The dead character may still be in the world with its client connected, or already out of the game (the client was
closed or dropped). The player decides whether the client keeps the revived character or is closed, so the character
stays out of the game standing at the restart point.

The kind of restart point is a game concept the platform names (`TOWN` today; clan hall, castle and fortress later);
how the host picks the exact location inside it is the server's configuration, not part of the command.

Terms follow the L2NX glossary: `restart-point` (the place a character gets up at after death), `revive` (a dead
character getting up again). `spot-pool` is an app-session term of the adapter, not a game one.

## Requirements

> Siblings: [`spec.md`](spec.md) (rail: `OwnerVerified`, `COMMAND_EXPIRED`), [`catalog.md`](catalog.md) (wire entry),
> [`offline-fishing.md`](offline-fishing.md) (reference for refusal codes in `extensions`).

**Must:**

- [done] R1. `ReviveCharacterCommand` MUST ship as an `NxCommand` in package `api.kafka.commands.character.revive`, routed by
  its simple name, with result type `ReviveCharacterResult`.
- [done] R2. `ReviveCharacterCommand` payload. The protection, dedup and idempotency fields are the canonical ones
  of every command that changes the game world ([`offline-fishing.md`](offline-fishing.md) R2):

  | Field              | Type      | Required | Notes                                                                                                                                                                                                                                             |
  | ------------------ | --------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `characterId`      | `int`     | yes      | Character to revive                                                                                                                                                                                                                               |
  | `masterAccountId`  | `UUID`    | yes      | Master account of the initiator, put in by the platform. [planned] Removed in favour of `initiator.userId` ([`spec.md`](spec.md) R30)                                                                                                             |
  | `restartPoint`     | `String`  | yes      | `RestartPoint` token (R3)                                                                                                                                                                                                                         |
  | `disconnectClient` | `boolean` | no       | Applies to a character in the world only. `false`: the character gets up at the restart point and the client keeps playing it. `true`: the client is closed and the character stays out of the game at the restart point. Absent reads as `false` |
  | `ownerVerified`    | `boolean` | no       | See R28 in [`spec.md`](spec.md); absent reads as `false`                                                                                                                                                                                          |
  | `deadline`         | `Instant` | yes      | Checked first; past it the host replies `COMMAND_EXPIRED` before touching anything                                                                                                                                                                |
  | `idempotencyKey`   | `UUID`    | yes      | Business identity of the revive, stable across retries                                                                                                                                                                                            |

  The constructor rejects a `null` `masterAccountId`, `restartPoint`, `deadline` or `idempotencyKey` and a blank
  `restartPoint`; Gson bypasses the constructor, so the host re-validates (`VALIDATION_FAILED`).

- [done] R3. `RestartPoint` is an open vocabulary: an enum whose `name()` is the wire token, with a lenient
  `fromToken` (`null` for an unknown token). The field stays a `String` on the wire, so a token added in a later api
  release reaches an older host as a string, and that host refuses it with `RESTART_POINT_UNSUPPORTED` instead of
  failing to deserialize. Today: `TOWN` (the engine's "to village"). Clan hall, castle and fortress are added with
  their own releases, each with its own app settings group (R7).
- [done] R4. `ReviveCharacterResult`: `restartPoint` (`String`, echoed), `x`, `y`, `z` (`int`, where the character
  stands after the revive) and `clientDisconnected` (`boolean`, `true` only when a connected client was closed by this
  command).
- [done] R5. Idempotency. The host records the outcome under `idempotencyKey` in the command receipts store
  ([`040`](../040-command-receipts.md)); a retry with the same key answers the recorded result and does not revive or
  disconnect again. Without that a retry after success would see a living character and answer `NOT_DEAD`.
- [done] R6. Refusals carry a reason code under `ReviveRefusal.REASON` in `CommandProblem.extensions`, from the
  closed enum `ReviveRefusalReason` (table below). It is a separate enum from `AppSessionRefusalReason`: a revive is
  not an app session and shares only some codes with it.
- [done] R7. App settings keys (catalog in [`038`](../038-app-offline-sessions.md), public table in
  `docs/public/{en,ru}/configuration.md`):

  | Key                         | Type                   | Default  | Meaning                                                                                                                                |
  | --------------------------- | ---------------------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------- |
  | `app.revive.enabled`        | boolean                | `true`   | Revive from the app on this server; off: `FEATURE_DISABLED`. On by default: with `NATIVE` it needs no other key                        |
  | `app.revive.town.mode`      | `NATIVE` / `SPOT_POOL` | `NATIVE` | How the host picks the location for `TOWN`: `NATIVE` as the game itself does for "to village", `SPOT_POOL` a spot from the pools below |
  | `app.revive.town.spot-pool` | list of pool names     | empty    | Pools for `SPOT_POOL`; `SPOT_POOL` with an empty list is a file validation error. May be shared with `app.fishing.spot-pool`           |

  Keys are grouped per restart point (`town.*`), so a later restart point adds its own group without renaming these.

### Reason codes

| Reason                      | When                                                                                            | Status                          |
| --------------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------- |
| `FEATURE_DISABLED`          | `app.revive.enabled = false`                                                                    | `INVALID_STATE`                 |
| `COMMAND_EXPIRED`           | `deadline` passed                                                                               | rail ([`spec.md`](spec.md) R23) |
| `RESTART_POINT_UNSUPPORTED` | The host does not support `restartPoint`                                                        | `VALIDATION_FAILED`             |
| `RESTART_POINT_UNAVAILABLE` | The restart point exists but not for this character (no clan hall, no castle); never for `TOWN` | `INVALID_STATE`                 |
| `CHARACTER_BANNED`          | Active game-login ban                                                                           | `FORBIDDEN`                     |
| `CHARACTER_JAILED`          | Jailed                                                                                          | `FORBIDDEN`                     |
| `NOT_DEAD`                  | The character is alive                                                                          | `INVALID_STATE`                 |
| `UNSAFE_LOGOUT_LOCATION`    | Dead in an instance, olympiad, event, siege or on a vehicle                                     | `INVALID_STATE`                 |

A missing character is `NOT_FOUND` without a reason code, as for every command. There is no `CHARACTER_ONLINE`: a
connected client is handled by `disconnectClient`, not refused.

### Edge-flow table

| Situation                                          | Outcome                                                                                           |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Dead, out of the game                              | Revived at the restart point, stays out of the game; `clientDisconnected = false`                 |
| Dead in the world, `disconnectClient = false`      | Gets up at the restart point in the world, the client keeps playing; `clientDisconnected = false` |
| Dead in the world, `disconnectClient = true`       | Client closed, then revived out of the game at the restart point; `clientDisconnected = true`     |
| Out of the game with `disconnectClient = true`     | Flag ignored, revived out of the game; `clientDisconnected = false`                               |
| Retry with the same `idempotencyKey` after success | Recorded result, nothing changes                                                                  |
| Alive                                              | `NOT_DEAD`                                                                                        |

## Technical design

### Wire shape

Plain Java 8 command and result classes with builders, `equals`, `hashCode`, `toString`, as
`StartOfflineFishingCommand`. `ReviveRefusal` holds the `REASON` extension key, as `AppSessionRefusal`. App settings
keys go through the existing app settings snapshot; the host reads them typed, the adapter validates the file.

### Host semantics (summary)

- The restart point is the engine's: the host revives the way its own restart request does and adds no rules beyond
  the refusals above.
- A character in the world without `disconnectClient` takes the engine's in-world restart path. With
  `disconnectClient` the client is closed first and the character is revived out of the game: the in-world revive is
  applied only when the client confirms the teleport, so closing the client after it could store a corpse at the new
  location.
- A client closed by this command does not count as the player leaving the game for the "recently logged out" start
  rule of app sessions ([`038`](../038-app-offline-sessions.md)): the next app action (fishing, store, app-phantom)
  is not blocked by it.
- `SPOT_POOL`: a random spot of all listed pools that no running app session holds; when none is free, a random spot
  of all of them - several revived characters may share a spot. The revive holds no spot. A character with karma
  always takes the `NATIVE` location.

## Rollout

Additions only: an older host does not know the command and replies as for any unknown command; the platform hides
the action until the server's app settings snapshot carries the `revive` group with `enabled = true`.

## Open questions

None.

## Links

- [`spec.md`](spec.md), [`catalog.md`](catalog.md), [`offline-fishing.md`](offline-fishing.md)
- [`038-app-offline-sessions.md`](../038-app-offline-sessions.md), [`040-command-receipts.md`](../040-command-receipts.md)
- `nx-gameservers/docs/specs/092-character-revive.md`
