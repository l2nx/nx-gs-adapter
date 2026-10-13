# Commands — AcceptMail

> Owner: @n1rmata
> Status: design, not released. No api version carries this command yet.

## Problem

`SendPlayerMailCommand` ([`send-player-mail.md`](send-player-mail.md)) lets a player send mail from
the platform, but the recipient can only claim the attachments and pay cash on delivery (COD) inside
the game client. A player who is away from the game needs the recipient-side counterpart: accept the
attachments of one mail and pay its COD, whether the receiving character is online or offline. The
command moves items and adena, so like the send it must be safe against a retry and against a command
that sat in the Kafka backlog while the game-server was down.

Audience: platform mail-flow authors composing the command; host operators wiring the handler.

## Requirements

> Siblings: [`spec.md`](spec.md) (rail: `OwnerVerified` R28, `COMMAND_EXPIRED` R23, at-most-once R11),
> [`send-player-mail.md`](send-player-mail.md) (sender side, receipt and unknown-outcome model this
> command mirrors), [`catalog.md`](catalog.md) (wire contract entry).

**Must:**

- [todo] R1. `nx-gs-adapter-api.kafka.commands.mail.AcceptMailCommand` MUST ship as
  `NxCommand<AcceptMailResult>` and `OwnerVerified`, routed by the simple name `AcceptMailCommand`
  (enum constant name for the platform-side audit: `ACCEPT_MAIL`).
- [todo] R2. Payload fields:

  | Field              | Type      | Required | Constraints / notes                                                                                                                                                                                             |
  | ------------------ | --------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `receiverCharId`   | `Long`    | yes      | Character that accepts the mail; items and adena move to and from it. A different recipient on the live mail is `MAIL_NOT_FOUND`                                                                                  |
  | `mailId`           | `Long`    | yes      | Mail to accept                                                                                                                                                                                                  |
  | `expectedSenderCharId` | `Long` | yes | Sender the player saw on the mail (from `MailSent`); a different sender on the live mail is `MAIL_NOT_FOUND` |
  | `expectedCodAdena` | `Long`    | yes      | COD the player saw when deciding; `0` for a mail without COD. A mismatch with the live mail is `MAIL_NOT_FOUND` and nothing is charged                                                                            |
  | `acceptedFeeAdena` | `Long?`   | no       | Consent to a receive fee (R6). `null` = no consent given yet. When present it is the largest fee the player agreed to pay                                                                                       |
  | `ownerVerified`    | `boolean` | no       | See R28 in [`spec.md`](spec.md); absent reads as `false`                                                                                                                                                        |
  | `idempotencyKey`   | `UUID`    | yes      | Business identity of this accept, stable across retries of the same request; non-null; the host dedups and replays by it (R8), `correlationId` identifies one delivery attempt only                             |
  | `deadline`         | `Instant` | yes      | Execution deadline, see R7; non-null, same type and name as in `SendPlayerMailCommand`                                                                                                                          |

  **Mail identity.** The mail is identified by what the platform saw in `MailSent`: `mailId` +
  `receiverCharId` + `expectedSenderCharId` + `expectedCodAdena`. The host compares them with its live
  mail: no mail with this id, or a different recipient, sender or COD, is `MAIL_NOT_FOUND` ("the mail the
  player saw no longer exists"): a mail's recipient, sender and COD never change, and the platform checks
  the recipient before sending, so any mismatch means the id now belongs to another mail. How the host proves internally that the id still points to
  the same mail is the host's business and is not part of the contract. Known limitation: if the mail was
  deleted and its id reused by a new mail from the same sender to the same receiver with the same COD,
  accepting the new one is equivalent to accepting the one the player saw; this is accepted.

  The command is produced only for the owner of the receiving character (`ownerVerified` per R28). The
  host does not rely on that and validates the wire itself (R9).

- [todo] R3. `AcceptMailResult` MUST carry `mailId` (`long`), the moved items (`List<MailItemMovement>`,
  the same type the `MailAccepted` event carries, empty for a mail without item attachments),
  `codPaidAdena` (`long`, `0` when the mail had no COD) and `receiveFeeAdena` (`long`, `0` when the host
  charged no receive fee).
- [todo] R4. The command MUST be **all-or-nothing**: either every attachment of the mail moved to the
  receiver and every charge (COD, receive fee) was taken, or nothing was charged and nothing moved.
  There is no partial accept. A failure after the first mutation MUST restore the receiver fully (fee and
  COD refunded, items back in the mail or never moved); the host MUST NOT leave a charge without the items, and MUST NOT lose the COD on a partial failure: the
  sender is credited exactly when the receiver was charged and the items moved.
- [todo] R5. Every non-OK business reply MUST carry a stable machine-readable `reason` in
  `CommandProblem.extensions` (see Status mapping); the host never sends player-facing text.
- [todo] R6. **Receive fee consent.** A host MAY charge a receive fee, for example when the receiver is
  outside a peace zone; the contract does not say when or how much, and the platform does not compute
  it. When the fee applies and `acceptedFeeAdena` is `null` or lower than the fee, the host MUST refuse
  with `RECEIVE_FEE_REQUIRED` before charging anything, and the reply carries the fee amount in the
  `feeAdena` extension. The platform shows the amount to the player and retries with
  `acceptedFeeAdena` set; the retry keeps the same `idempotencyKey` with a new `correlationId`: the first
  attempt ended in a business refusal, so its `FAILED` receipt is reopened (R8). The consent amount is not
  part of the key's identity: a replay of a completed accept returns the stored result whatever
  `acceptedFeeAdena` it carries. The host charges exactly the fee it computed, never the
  larger `acceptedFeeAdena`. A host that does not allow receiving in the receiver's current state at all
  (for example, outside a town without the required item, or under an active jail sanction regardless of
  location) refuses with `RECEIVE_FORBIDDEN`. For an offline receiver the location is its last known
  position.
- [todo] R7. `deadline` is required and non-null. It MUST be checked first, before anything is charged or
  moved. A passed deadline yields `COMMAND_EXPIRED` (its own `CommandStatus`, rail R23) with no
  mutation. The only thing read first is the durable receipt by `idempotencyKey`, read-only: an existing
  OK receipt replays the stored result, an in-flight one is `OUTCOME_UNKNOWN` as in R8; an absent or
  `FAILED` receipt is `COMMAND_EXPIRED`.
- [todo] R8. The command MUST be idempotent per `idempotencyKey`, with the same model as
  `SendPlayerMailCommand` R6: durable per-command receipts keyed by `idempotencyKey`, not by
  `correlationId` and not by the in-memory dedup store. A replay of a completed accept returns the stored
  full result; a replay while the first attempt is in flight reports an unknown outcome (`INTERNAL_ERROR`
  class with reason `OUTCOME_UNKNOWN`, not a business refusal) instead of executing again, and the
  platform marks the reply `retrySafe = false`. A commit whose outcome the host cannot determine is also
  `OUTCOME_UNKNOWN`: the receipt is held and resolved later, never answered with a guess. `OUTCOME_UNKNOWN`
  is an `INTERNAL_ERROR` with a problem only, no payload (the rail forbids a payload on a non-`OK` reply).
  A caller treats an accept as done only on a reply with status `OK`, and waits before asking again; how the
  platform renders an unknown outcome to its own clients is the platform's choice. The receipt turns `OK`
  only after every durable write succeeded. A business refusal marks the receipt `FAILED`; a retry with the same key and a new `correlationId` reopens it and
  restarts cleanly.
- [todo] R9. The host MUST validate the wire command itself before the receipt lookup: required fields
  present (including `expectedSenderCharId`); ids within the host id range (out of range is `VALIDATION_FAILED` with a `field` extension);
  `expectedCodAdena` non-negative; `acceptedFeeAdena` non-negative when present.
- [todo] R10. Checks that are business refusals, not wire errors: mail exists with the recipient, sender and
  COD the player saw (`MAIL_NOT_FOUND`); mail has attachments or COD (`NO_ATTACHMENTS`); receiver has room for the
  items (`INVENTORY_FULL`) and weight (`OVERWEIGHT`: a player accept is refused when the attachments would
  overload the receiver, as in game; `ownerVerified` does not bypass it); receiver holds `COD + fee` in
  adena, one check (`NOT_ENOUGH_ADENA`); an attached adena amount that, added to the receiver's balance,
  would exceed the host's adena limit (`ADENA_OVERFLOW`, refused rather than clamped); item lock
  (`ITEM_LOCKED`, R11). Equipped stackable items (for example ammunition) count as existing stacks for the
  merge and slot plan.
- [todo] R11. The item lock check is policy, as in `SendPlayerMailCommand` R8: `ownerVerified = true`
  MUST bypass it, consistent with R28; `false` leaves the lock in force and a locked receiver is refused
  with `ITEM_LOCKED`. Other
  native receive restrictions surface as `TRANSACTIONS_FORBIDDEN` / `CHARACTER_BANNED`. An active jail
  sanction is `RECEIVE_FORBIDDEN` regardless of where the receiver is; there is no `CHARACTER_JAILED`
  reason on this command. When the host's native receive path restricts receiving by the receiver's state
  (for example a dead character outside a town), that is `RECEIVE_FORBIDDEN` as well.
- [todo] R12. **Receiver may be online or offline; the host serves both**, and the checks it applies are
  the same ones its native receive path applies. Observable differences are limited to
  `CHARACTER_BUSY` (receiver in a trade or enchant in game; only an online receiver can hit it),
  `STORE_OPEN` (a player at the keyboard with an open private store must close it first; an offline trader
  with an open store MAY accept, adding stacks does not touch the store's list) and
  `RECEIVER_DATA_UNAVAILABLE` (R13). Presence is decided under the same character mutation lock
  that login and logout use, so a login waits for an offline accept to commit and no half-accepted state
  is observable.
- [todo] R13. **`RECEIVER_DATA_UNAVAILABLE`.** An offline receiver's capacity facts (inventory limit,
  carry weight and similar) may be impossible to know exactly without the character logging in. If the
  host cannot prove that the mail fits using the data it has, it MUST refuse with
  `RECEIVER_DATA_UNAVAILABLE` and change nothing; the platform tells the player to log in to the game
  once. The host MUST NOT guess in favour of accepting. How it derives the facts is host-internal. A stored
  inventory limit must not outlive an offline access-level change: the host clears the stored capacity
  facts on such a change, so the accept falls back to the base limit or `RECEIVER_DATA_UNAVAILABLE`.
- [todo] R14. **Events.** `MailAccepted` (`MailAcceptedEvent`) is the single source of truth for the
  mail's new state on the platform: the platform projection moves the mail to accepted only on this event,
  never from the command reply. The host MUST publish it after every successful accept, from this command
  and from the native in-game accept alike, and MUST publish it **even when `attachments` is empty** (a
  text-only or COD-only mail); skipping the event for an empty list leaves the mail sent forever on the
  platform. The event is published after the accept is committed, never before. If the process dies between the
  commit and the publish, the host's boot recovery MUST re-publish `MailAccepted` from the persisted
  receipt; so the event may arrive more than once and consumers MUST be idempotent. A re-published event
  carries the original completion time in its `eventId`, not the time of the restart, and a consumer MUST
  NOT move the accepted time of a mail it has already marked accepted. Cancelled mails follow
  the same rule: `MailCancelled` is published even with no attachments.
- [todo] R15. **Expiry.** A mail that expires in the host (returned to the sender or deleted by the
  host's mail expiry task) SHOULD be announced the same way: publish `MailReturned` (`MailReturnedEvent`)
  for an expiry return and `MailDeleted` (`MailDeletedEvent`) for an expiry delete; `MailDeletedEvent.side` is `RECEIVER` there: `MailDeletionSide` has only
  `SENDER` and `RECEIVER` and no system side, an expiry delete removes the mail from the box of its
  current recipient (for a returned mail, the original sender), and a new enum value would break
  consumers with exhaustive switches. Without them the
  platform keeps offering accept for a mail that no longer exists; the command then answers
  `MAIL_NOT_FOUND`, which is the safety net, not the design.
- [todo] R16. A successful accept triggers `requestNow("character", ...)` for the receiver and, when COD
  was paid, for the sender, as `SendPlayerMailCommand` does.

- [todo] R17. **Mail state after accept**, as in game: the attachments are removed (`hasAttachments`
  becomes false) and the mail stays readable in the receiver's box, still showing its COD amount (a consumer
  shows it as paid once the mail is accepted). The host does not delete the mail row.
- [todo] R18. **Refusal numbers.** Refusals that depend on a quantity carry it in `CommandProblem.extensions`
  next to `reason`: `NOT_ENOUGH_ADENA` carries `requiredAdena` and `availableAdena`; `INVENTORY_FULL`
  carries `requiredSlots` and `freeSlots`; `OVERWEIGHT` carries `requiredWeight` and `freeWeight`;
  `RECEIVE_FEE_REQUIRED` carries `feeAdena` (R6). The host fills them from the plan it computed.
- [todo] R19. **Atomicity against the host's own mail paths.** An accept is atomic against the host's native
  in-game receive, cancel and expiry of the same mail: whichever path takes the receiver (and sender) lock
  first wins, the others find the mail already settled and refuse without any debit or item movement. After
  an accept commits, the host's in-memory mail state MUST already reflect it (attachments taken, stale item
  objects discarded), so a later native receive or cancel cannot double-debit COD or duplicate items. The
  sender's lock is taken even when no COD is due, because cancel and expiry mutate the same attachment rows.
  Every mutation of attachment rows verifies that it touched the expected rows, and a miss is
  `MAIL_NOT_FOUND`; so is a mail flagged with attachments that are empty in storage. This is an integrity requirement, not an implementation detail.

**Non-goals:**

- Refuse / return the mail and delete the mail: stay in the game client, no command.
- Accepting several mails in one command: one command is one mail.
- Staff accept (an admin accepting a mail for a character): not provided, there is no use case. A staff
  tool needing it adds its own command.
- Partial accept (some attachments only): not supported, see R4.
- Computing or configuring the receive fee on the wire: the fee is host-defined, only consent travels.

### Edge cases

- **Receiver online and busy** (trade, enchant): `CHARACTER_BUSY`; if the character lock cannot be
  taken within its short timeout the reply is also `CHARACTER_BUSY`.
- **Sender online or offline when COD is paid**: the host credits the sender either way and the result
  is the same; the credit is part of the accept, not a separate best-effort step.
- **Retry after `TIMEOUT` on the platform side**: safe, same `idempotencyKey` replays the stored outcome.
- **Mail already accepted, returned or deleted**: `MAIL_NOT_FOUND`.
- **Fee changed between the refusal and the retry**: the host recomputes; if the new fee exceeds
  `acceptedFeeAdena` the answer is `RECEIVE_FEE_REQUIRED` again with the new amount.

## Technical design

### Wire shape

Same rail as other commands: key `longBytesBe(receiverCharId)` is the producer's choice, headers
`Nx-Message-Type = AcceptMailCommand`, `Nx-Correlation-Id` (stable per logical accept),
`Nx-Target-Server-Id`. Reply type header is derived per rail R26: `AcceptMailResult`.

Example reply value (success; the `items` element is a `MailItemMovement`, its keys here are
illustrative):

```json
{
  "status": "OK",
  "payload": {
    "mailId": 10243,
    "items": [{ "itemId": 268451233, "itemTemplateId": 57, "count": 5000 }],
    "codPaidAdena": 1000,
    "receiveFeeAdena": 500
  }
}
```

Example reply value (fee consent needed):

```json
{
  "status": "INVALID_STATE",
  "problem": {
    "title": "Receive fee required",
    "extensions": { "reason": "RECEIVE_FEE_REQUIRED", "feeAdena": 500 }
  }
}
```

Example reply value (unknown outcome; the platform marks it `retrySafe = false`):

```json
{
  "status": "INTERNAL_ERROR",
  "problem": { "title": "Outcome unknown", "extensions": { "reason": "OUTCOME_UNKNOWN" } }
}
```

### Status mapping

Same convention as `SendPlayerMailCommand`: the status classifies the refusal, `extensions.reason`
names it. The classification matches the send.

| Status            | `extensions.reason`                                                                                                                                                                |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NOT_FOUND`       | `MAIL_NOT_FOUND` (also a different recipient, sender or COD, receiver not found or pending deletion)                                                                                              |
| `FORBIDDEN`       | `CHARACTER_BANNED`, `TRANSACTIONS_FORBIDDEN`, `ITEM_LOCKED`, `RECEIVE_FORBIDDEN`                                                                                              |
| `INVALID_STATE`   | `NO_ATTACHMENTS`, `INVENTORY_FULL`, `OVERWEIGHT`, `NOT_ENOUGH_ADENA`, `ADENA_OVERFLOW`, `RECEIVE_FEE_REQUIRED`, `RECEIVER_DATA_UNAVAILABLE`, `CHARACTER_BUSY`, `STORE_OPEN`, `RESTORE_IN_PROGRESS` |
| `COMMAND_EXPIRED` | its own status (rail R23), no reason needed: the deadline passed, nothing changed                                                                                                  |
| `INTERNAL_ERROR`  | `OUTCOME_UNKNOWN` (problem only, no payload, R8; replay finds the first attempt in flight, or the commit outcome is unknown; `retrySafe = false`)                                                                |

`VALIDATION_FAILED` with a `field` extension covers a malformed command (R9).

### Well-known reasons

`WellKnownAcceptMailReasons` (api constants; the set is open, consumers MUST tolerate unknown tokens):

| Reason                      | Meaning                                                                                                                |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `MAIL_NOT_FOUND`            | The mail the player saw no longer exists: no mail with this `mailId`, recipient, `expectedSenderCharId` and `expectedCodAdena`, or it was returned or deleted |
| `NO_ATTACHMENTS`            | Nothing to accept: no item attachments and no COD to settle                                                            |
| `INVENTORY_FULL`            | The receiver has no room for the attachments; `requiredSlots` / `freeSlots` (R18)                                      |
| `OVERWEIGHT`                | The attachments would overload the receiver, as in game; `requiredWeight` / `freeWeight` (R18)                         |
| `NOT_ENOUGH_ADENA`          | The receiver cannot cover COD plus receive fee; `requiredAdena` / `availableAdena` (R18)                               |
| `ADENA_OVERFLOW`            | The receiver's adena plus the attached adena would exceed the host's adena limit                                       |
| `STORE_OPEN`                | The receiver is at the keyboard with an open private store and must close it first                                     |
| `RECEIVE_FEE_REQUIRED`      | Host charges a receive fee and consent is missing or too low; the fee amount is in the extension (R6)                  |
| `RECEIVE_FORBIDDEN`         | Receiving is not allowed in the receiver's current state (host-defined, e.g. outside a town without the required item, or an active jail sanction) |
| `RECEIVER_DATA_UNAVAILABLE` | Offline receiver's capacity facts are unknown and the mail cannot be proven to fit (R13)                               |
| `ITEM_LOCKED`               | Receiver's items are locked and `ownerVerified` is not set (policy, R11)                                               |
| `TRANSACTIONS_FORBIDDEN`    | Receiver's access level forbids transactions                                                                           |
| `CHARACTER_BUSY`            | Receiver is in a trade or enchant in game, or the character lock timed out                                            |
| `CHARACTER_BANNED`          | Receiver has an active game ban                                                                                        |
| `COMMAND_EXPIRED`           | Mirrors the dedicated status for consumers that key on reasons                                                         |
| `OUTCOME_UNKNOWN`           | Result not determinable; platform keeps its guard held (R8)                                                            |

### Host semantics (summary)

Follows the order of [`send-player-mail.md`](send-player-mail.md) › Host semantics: deadline check
(R7), wire validation (R9), receipt lookup by `idempotencyKey`, read-only gates, claim of the receipt,
character mutation lock for the receiver (and the sender when COD is paid, taken in a fixed order so two
locks cannot deadlock), presence decided inside the lock, gates re-checked under the lock, then mutate.

- **Online receiver**: the receipt records the plan (fee, COD) before the first debit. Fee and COD are
  debited, items move, the sender is credited, `MailAccepted` is published. A failure midway refunds fee
  AND COD (R4).
- **Offline receiver**: one database transaction under the same lock: items move to the receiver's
  inventory with stack merge, fee and COD debited, COD credited to the sender (merged into the
  sender's existing adena stack), the attachments removed from the mail, the receipt written. The in-memory
  mail state is settled under the same lock right after the commit (R19); an online sender's balance and
  the event follow, as marked receipt steps. Until an unknown commit outcome is resolved, native receive of
  that mail is refused.
- Boot recovery resolves a receipt left in flight from persisted facts; unambiguous cases finish or roll
  back, ambiguous ones are held for an operator alert and manual reconciliation, with no automatic refund,
  as for the player send. A refund is automatic only when the receipt holds durable proof that the debit
  happened. Exact storage layout is the host's documentation.

### Platform side

REST flow, pre-checks, ownership and ban-zone rules, rate limits and the projection live in the platform
specs. The command is issued with `CommandInitiator.user(...)` and a `deadline` of now plus the platform
reply timeout.

## Rollout

Additive wire (new command, new DTOs); the adapter api version is bumped. Order: release `api/vX.Y.Z`
first, then the host, then the platform. Before the host registers the handler it answers
`UNSUPPORTED_COMMAND`; no compatibility straddle. The event obligations in R14 and R15 fix the host's
publishing and ship with the host release, independent of the command. `AcceptMailCommand` joins the
`OwnerVerified` list (`spec.md` R28, `catalog.md` › Owner verification).

## Edge flows

**Invariants.** mail identity by `mailId` + `receiverCharId` + `expectedSenderCharId` +
`expectedCodAdena` (a foreign and a missing mail are indistinguishable); a system-sender mail is acceptable
(sender id matches the stored one); the consent amount is not part of the idempotency identity; the host
charges exactly the fee it computed, never the larger consent; a retry after OK with a new key finds no
attachments and moves nothing; the item-lock policy and `ownerVerified` gate match the send; lock order is
ascending by character id, so two locks cannot deadlock.

| Vector / edge flow                                                         | Expected behaviour                                                                 | Anchor        |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------- |
| Offline trader with an open store accepts a delivery                       | Allowed under the character lock                                                   | R12           |
| Player at the keyboard with an open store accepts                          | `STORE_OPEN`, nothing charged                                                      | R12           |
| Player declined the fee, later accepts from a town                         | Nothing was charged; the later accept passes with the same key or a new one        | R6, R8        |
| Consent given, receiver meanwhile entered a town (fee now 0)               | Accept succeeds with `receiveFeeAdena = 0`                                         | R6            |
| `acceptedFeeAdena` far above the fee                                       | Exactly the computed fee is charged                                                | R6            |
| Same key replayed after OK with a different `acceptedFeeAdena`            | The stored OK result is returned                                                   | R8            |
| Native receive and app accept of one mail at once                          | One winner; the other refuses before any debit                                     | R19           |
| Mail without COD expires during an offline accept                          | Either accept or return, never both, never loss                                    | R19           |
| Sender cancels the mail during or after an offline accept                  | Zero net change; cancel finds the mail settled                                     | R19           |
| Receiver opened the mail in game, logged out, app accept, then logs in     | Native receive refuses, nothing charged                                            | R19           |
| Offline commit outcome unknown, receiver logs in and presses receive       | Refused until the outcome is resolved                                              | R8            |
| Crash between the debit marker and the debit                               | Receipt held for an operator alert, no automatic refund                            | R8            |
| Crash after commit, mail id reused before the event is re-published        | Event carries the original completion time; consumers keep the first accepted time | R14           |
| System-sender mail with a receive fee and no COD                           | Fee charged, nothing credited, no sender lock needed for credit                    | R10, R19      |
| Sender character deleted                                                   | COD credited as native, event names the real sender                                | R4            |
| Receiver jailed while offline, last position in a town                     | `RECEIVE_FORBIDDEN`                                                                | R11           |
| Receiver dead outside a town while offline                                 | `RECEIVE_FORBIDDEN`                                                                | R11           |
| Receiver in an instance or on a ship while offline                         | Not a peace zone, so the fee or forbidden rule applies                             | R6            |
| Adena attachment with the receiver at the adena limit                      | `ADENA_OVERFLOW`, not a clamp                                                      | R10           |
| Equipped ammunition plus a mail with the same ammunition                   | Merges into the equipped stack, no new slot                                        | R10           |
| Offline access-level change, then accept                                   | Base limit applies or `RECEIVER_DATA_UNAVAILABLE`                                  | R13           |
| Staff transfer or delete of a summoned companion's control item            | `ITEM_IN_USE`                                                                      | catalog       |
| Staff delete or transfer of an item listed in an open store                | `ITEM_IN_STORE`                                                                    | catalog       |
| Receiver pending restore as an offline trader                              | `RESTORE_IN_PROGRESS` before any mutation                                          | Status mapping |

## Open questions

None.

## Links

- Sender-side counterpart and receipt model: [`send-player-mail.md`](send-player-mail.md)
- Rail: [`spec.md`](spec.md); wire entry: [`catalog.md`](catalog.md)
- Deferred contract work: [`TODO.md`](../../TODO.md)
- Platform contract and REST flow: platform mail spec family, see
  `nx-gameservers/docs/specs/037-mail/player-send.md`
- Delivery framework (deadline, durable receipts): `nx-gameservers/docs/specs/068-critical-commands-framework.md`
- Host documentation: not linked here, this repository does not name tenant hosts.
