# Commands — SendMail

> Owner: @n1rmata

## Problem

The platform sends system mails to characters from two operator surfaces — the
web admin UI and the Telegram bot — using a hand-rolled RabbitMQ wire
(`SendMailRequestV1` consumed by both `tg-to-<server>` and `admin-to-<server>`
queues, both dispatching to `MailService.sendMailAndReply`). The legacy surface has
the per-spec problems documented in [`commands/spec.md`](spec.md):
free-form `message` field doubling as both error string and partial-failure report,
no structured error code, correlation id in body instead of headers, autoAck before
handler runs, no host-thread hop. The [`commands`](spec.md) infrastructure
slice shipped the runtime; this slice is the first concrete-DTO migration off
RabbitMQ onto the new commands rail.

Per [`commands/spec.md` line 406-408](spec.md): migration is per-command
cutover. Web side feature-flags individual commands to Kafka or RabbitMQ during
transition; the host runs both consumer surfaces in parallel; the legacy DTO
case is removed only when the platform stops emitting it. This slice does NOT
remove the `RabbitMqTgConsumer` / `RabbitMqWebAdminConsumer` SendMailRequestV1
arms — those retire in a follow-up cleanup once the web side is fully cut over.

Audience: host operators wiring the new handler; platform-side mail-flow
authors composing `SendMailCommand` records onto the commands topic.

## Requirements

> Sibling features carry the wire + dispatch plumbing:
>
> - [`commands`](spec.md) — Kafka topic + consumer + dispatch table + > reply path + heartbeat slot. UNCHANGED by this slice.
> - [`adapter-modules`](../002-adapter-modules/spec.md) — Tier-1 ServiceLoader-based > `AdapterModule` discovery used by the host to wire its handlers via > its commands module.

**Must:**

- [done] R1. `nx-gs-adapter-api.kafka.commands.mail.SendMailCommand` MUST ship
  as `NxCommand<SendMailResult>` with the following fields:
  - `Long charId` — REQUIRED. Recipient character primary key.
  - `@Nullable String author` — OPTIONAL. Display name of the sender; null/blank
    delegates to the host's system-default sender name.
  - `String title` — REQUIRED. Subject line.
  - `@Nullable String body` — OPTIONAL. Body text; null treated as empty.
  - `@Nullable List<MailItem> items` — OPTIONAL. Attachment lines; null/empty
    produces a text-only mail. Stored as an unmodifiable list; null normalized
    to `Collections.emptyList()` on read.
  - `@Nullable Long adena` — OPTIONAL. Adena placed in the mail; the host creates it as an
    item-template attachment (template `57`). Positive when present; `null` = no adena. Additive
    field: an old host ignores it, an old producer reads as `null`. [todo]

  Constructor enforces non-null `charId` and `title` via `IllegalArgumentException`
  for programmatic construction. Wire-path Gson bypasses the constructor; handler
  re-validates and emits `VALIDATION_FAILED` on missing fields.

- [done] R2. `nx-gs-adapter-api.kafka.commands.mail.MailItem` MUST ship as a POJO
  with two fields: `Long itemTemplateId` (REQUIRED) and `Long count` (REQUIRED,
  positive). Constructor rejects null on either + non-positive `count` via IAE.
  Builder defaults `count` to `1`. Handler re-validates the wire path.

  Naming: `itemTemplateId` (not `itemId`) is deliberate — it is the catalog
  template id from which the attachment stack is created, NOT the per-instance
  object-id used by `DeleteItemCommand`. Different identity, different lifetime.

- [done] R3. `nx-gs-adapter-api.kafka.commands.mail.SendMailResult` MUST ship as
  the success-payload type carrying:
  - `List<Long> createdMailIds` — primary keys of the materialized mail rows.
    Multi-element when the host batches the inbound `items` across multiple
    mails (host-defined cap; e.g. a host config constant).
    Non-null on read; null in constructor normalized to empty list.
  - `List<ItemDeliveryError> itemErrors` — per-line attachment failures on an
    OK envelope. Non-null on read. The wire keeps the field so a host that
    delivers partially can report it, and platform consumers MUST still read it:
    non-empty on OK means the mail went out without the listed lines. The
    reference host delivers all-or-nothing and always sends it empty (see
    "`itemErrors` today").

  Both lists are defensively copied + frozen on construction (unmodifiable view
  from getters).

- [done] R4. `nx-gs-adapter-api.kafka.commands.mail.ItemDeliveryError` MUST ship
  as a POJO with three fields: `@Nullable Long itemTemplateId`,
  `@Nullable Long count`, `String reason`. Identity fields are best-effort —
  populated only when the host can confidently attribute the failure to a
  specific inbound line. `reason` is the always-present diagnostic; null
  passed to the constructor is normalized to an empty string at construction
  so getters and `equals`/`hashCode`/`toString` agree. The reference host never
  emits it today.

- [done] R5. The host MUST ship `SendMailHandler`
  implementing `CommandHandler<SendMailCommand, SendMailResult>`:
  - Wire-path checks before anything is claimed or written: `charId` non-null,
    `title` non-null and non-empty, every `MailItem` non-null with a non-null
    `itemTemplateId` inside the `int` range that names a known item template and
    a positive `count`, then `charId` in `1..Integer.MAX_VALUE`. Each failure is
    `validationFailed("<title>", "<field>")`, which puts the field under the
    `field` extension key.
  - The send runs on `ctx.io()` through the host's mail service under a durable
    receipt (see "Durable receipt"): recipient lookup, mail preparation, claim,
    then one all-or-nothing delivery. No `ctx.host().sync(...)` hop.
  - Outcome mapping: success → OK with the created mail ids and empty
    `itemErrors`; recipient missing or request rejected by the host's mail
    manager → `NOT_FOUND`; any other failure (including a refused repeat of an
    open receipt) → `INTERNAL_ERROR`. See "Status mapping".
  - On success the handler requests a cascading `character` resync of the
    recipient, so the new attachments reach the platform with it.

- [done] R6. The host MUST register the handler in its commands module behind
  the correlation-id dedup decorator and the offline-trader restore gate (refuses
  with `INVALID_STATE` while traders, or the recipient, are still being restored
  after a server start).

**Should:**

- [done] R7. Exactly-once delivery per `correlationId`, across retries and game-server
  restarts. Two layers: the in-memory `DedupCommandHandler` + `CommandDedupStore` (OK-only,
  10 min TTL, lost on restart) in front of the handler, and a durable host-side receipt
  that survives restarts — contract in "Durable receipt" below. The platform derives a
  stable correlation id per recipient and reuses it on retry, so both layers actually fire.

**Could:**

- [todo] R8. Migration to a `SendMailWithReplyToCommand` variant that carries
  thread-id / reply-to mail id metadata for non-system mail. Not in scope —
  platform only sends system mails through this surface today.

**Non-goals:**

- **Mail from a character.** `SendMailCommand` is system-sender only. The character-sender
  counterpart (items and adena taken from the sender, COD, durable idempotency) is
  [`SendPlayerMailCommand`](send-player-mail.md).
- **Removing legacy RabbitMQ `SendMailRequestV1` cases.** Per per-command cutover
  the legacy switch arms in `RabbitMqTgConsumer` / `RabbitMqWebAdminConsumer`
  remain live until the web side stops emitting on RabbitMQ.
- **Server-side validation of mail-inbox capacity.** The host does not refuse a
  send to a full inbox; it hides the oldest read inbox mail without attachments
  and inserts, as for any other system mail.
- **`reloadInMemoryMail` migration.** That admin command is a different DTO and
  ships in a separate slice.

### Edge cases

- **`charId` not found and platform has stale character state.** Reply
  `NOT_FOUND` → web side decides whether to alert / retry / drop. Same as legacy.
- **Empty `items` + empty `body` + non-blank `title`** — text-only mail with
  no attachments and a header-only body. Allowed; the host creates a single
  mail row with no attachments.
- **An attachment line the host cannot materialize** — an unknown template is
  refused up front as `VALIDATION_FAILED` (`field = items[].itemTemplateId`)
  before anything is claimed. A line that fails later, while attachments are
  staged, aborts the whole request: nothing is delivered, the staged
  attachments are discarded and the reply is `INTERNAL_ERROR`. There is no
  partial delivery.
- **More attachment slots than the host's per-mail cap** — the host splits the
  request into mails titled `"<title> 1/N"` … `"<title> N/N"` and
  `createdMailIds` carries one id per mail, all delivered or none. A
  non-stackable line with `count > 1` takes one slot per unit.
- **Re-delivery of the same correlation id mid-mail-creation** — the second
  attempt finds the open receipt and is refused as outcome-unknown (`INTERNAL_ERROR`);
  once the first attempt completes, a retry replays its stored `createdMailIds`.
  No duplicate mail (R7).

## Open questions

- [resolved: Reply payload typed as `SendMailResult` (not `Void`). The legacy
  `Set<Integer> createdMailIds` is load-bearing — web side needs the ids to
  link back to the originating delivery, especially for audit / refund flows.]
- [resolved: Partial item-failures would ride an OK envelope with `itemErrors`
  (not a separate error code), so the wire keeps that shape. The reference host
  has since become all-or-nothing: a line that cannot be delivered fails the
  whole request, and `itemErrors` is always empty on its replies.]
- [resolved: `itemTemplateId` rename vs legacy `itemId`. The legacy field was
  ambiguous (could be read as object-id) and the new commands surface uses
  `itemObjectId` for object-ids in `DeleteItemCommand`. Renaming makes the
  identity explicit.]
- [assumed: `Long createdMailIds` (not `Integer`). Aligns with the rest of
  the commands api which uses `Long` for primary keys; current host
  `Message::getId` returns `int` so the host widens on the way out.]

## Links

- Character-sender counterpart: [`send-player-mail.md`](send-player-mail.md)
- Sibling feature (commands runtime + dispatch + reply path):
  [`docs/specs/009-commands/spec.md`](spec.md)
- Platform counterpart (send path, ingest, defects, planned outbox/idempotency layers):
  `nx-gameservers/docs/specs/037-mail/sync.md`
- Delivery/idempotency framework this command is expected to adopt (deadline gate, durable receipts,
  safe-set outcome rules): `nx-gameservers/docs/specs/068-critical-commands-framework.md`
- Companion document: see the Technical design section below — wire layout + handler walkthrough

---

## Technical design

### Wire shape

Inbound record on `<tenant>.gs.commands`:

```
key:   longBytesBe(charId)
headers:
  Nx-Server-Id      = raw 16-byte tenant/server uuid (auto-stamped post-/connect)
  Nx-Message-Type   = "SendMailCommand"
  Nx-Correlation-Id = <platform-issued UUID>
value: gson(SendMailCommand JSON)
```

Reply record on `<tenant>.gs.commands.replies`:

```
key:   longBytesBe(corrId.getMostSignificantBits())
headers:
  Nx-Server-Id      = raw 16-byte tenant/server uuid
  Nx-Message-Type   = "SendMailCommandResult"
  Nx-Correlation-Id = <inbound corrId>
value: gson(CommandResult<SendMailResult> JSON)
```

Example value (success, request split into two mails):

```json
{
  "status": "OK",
  "payload": {
    "createdMailIds": [10241, 10242],
    "itemErrors": []
  }
}
```

Example value (recipient not found):

```json
{
  "status": "NOT_FOUND",
  "problem": {
    "title": "charId not found: 42"
  }
}
```

### Field reference

| Field                    | Type          | Required | Notes                                            |
| ------------------------ | ------------- | -------- | ------------------------------------------------ |
| `charId`                 | `Long`        | Yes      | Recipient character pk                           |
| `author`                 | `String?`     | No       | Sender display; null/blank → host system default |
| `title`                  | `String`      | Yes      | Mail subject; non-empty, host rejects blank      |
| `body`                   | `String?`     | No       | Mail body; null → empty                          |
| `items`                  | `[MailItem]?` | No       | Attachments; null/empty → text-only mail         |
| `items[].itemTemplateId` | `Long`        | Yes      | Catalog template id (NOT object-id)              |
| `items[].count`          | `Long`        | Yes      | Stack size, positive                             |
| `adena`                  | `Long?`       | No       | Planned (R1), not on the wire yet                |

Reply payload:

| Field                         | Type                  | Notes                                                         |
| ----------------------------- | --------------------- | ------------------------------------------------------------- |
| `createdMailIds`              | `[Long]`              | One entry per resulting mail (host-side batching)             |
| `itemErrors`                  | `[ItemDeliveryError]` | Non-empty on OK = partial delivery; reference host sends `[]` |
| `itemErrors[].itemTemplateId` | `Long?`               | Optional inbound-line hint; `null` when host can't attribute  |
| `itemErrors[].count`          | `Long?`               | Optional inbound-line hint; `null` when host can't attribute  |
| `itemErrors[].reason`         | `String`              | Free-form diagnostic; null normalized to ""                   |

### Status mapping

| Condition                                                    | Status              | `problem`                                               |
| ------------------------------------------------------------ | ------------------- | ------------------------------------------------------- |
| `charId` null, or outside `1..Integer.MAX_VALUE`             | `VALIDATION_FAILED` | `extensions.field = "charId"`                           |
| `title` null or empty                                        | `VALIDATION_FAILED` | `extensions.field = "title"`                            |
| `items[i]` null                                              | `VALIDATION_FAILED` | `extensions.field = "items[*]"`                         |
| `itemTemplateId` null, above `Integer.MAX_VALUE`, or unknown | `VALIDATION_FAILED` | `extensions.field = "items[].itemTemplateId"`           |
| `count` null or ≤ 0                                          | `VALIDATION_FAILED` | `extensions.field = "items[].count"`                    |
| Traders or the recipient still being restored after a start  | `INVALID_STATE`     | restore-gate refusal                                    |
| Recipient not found                                          | `NOT_FOUND`         | `title = "charId not found: <id>"`                      |
| Host mail manager rejects the request (e.g. blank title)     | `NOT_FOUND`         | `title` = the host's message                            |
| Repeat of a correlation id whose receipt is `OK`             | `OK`                | stored `createdMailIds` replayed, `itemErrors` empty    |
| Repeat of a correlation id whose receipt is still open       | `INTERNAL_ERROR`    | `title` says the outcome is unknown                     |
| Receipt settled by another attempt during this delivery      | `INTERNAL_ERROR`    | outcome unknown; nothing committed, receipt untouched   |
| Lookup, preparation, receipt or delivery failure             | `INTERNAL_ERROR`    | `title` = the host's message                            |
| Exception escaping the IO-pool dispatch                      | `INTERNAL_ERROR`    | `title = "Dispatch failed"`, `extensions.error.class`   |

Validation `field` values are not index-tagged; the host names only the
offending field.

### Handler walkthrough

`SendMailHandler.handle(cmd, ctx)`, behind the dedup decorator and the restore gate:

1. **Wire-validate** on the consumer thread (Gson bypasses the constructors):
   `charId`, `title`, every `MailItem` including a catalog lookup of each
   `itemTemplateId` (so an unknown template fails before anything is claimed or
   staged), then the `charId` range.
2. **Dispatch to `ctx.io()`** and join: the send is blocking JDBC, which
   belongs on the adapter IO pool, not on the consumer thread or the game
   executor. There is no `ctx.host().sync(...)` hop.
3. **Recipient lookup** by `charId`; absent → `NOT_FOUND`.
4. **Prepare** the mail without touching the database: the host's mail
   manager validates title and receiver, expands non-stackable lines into one
   slot per unit, splits the slots across mails over its per-mail attachment
   cap and pre-allocates every mail id.
5. **Claim** the durable receipt with those ids: `OK` replays, an open receipt
   refuses, a new or `FAILED` one proceeds (see "Durable receipt").
6. **Deliver** all-or-nothing: stage the attachments of every mail, then insert
   all mail rows and mark the receipt `OK` in one database transaction. A
   failure before the commit rolls back and discards the staged attachments;
   once that is proven clean the receipt is released as `FAILED`, otherwise
   (a failed commit, a failed rollback) it is held for restart recovery. If the
   receipt was settled by another attempt in the meantime, the batch rolls back
   and the receipt is left as it is.
7. **Post-commit**: register the mails in memory, notify an online recipient
   and publish the mail-sent event. A failure here does not undo the delivery.
8. **Reply** OK with one `createdMailIds` entry per mail and empty
   `itemErrors`, after requesting a cascading `character` resync of the
   recipient.

The handler IS wrapped for correlation-id dedup: the host's commands module registers it
through `onDeduped`, so `DedupCommandHandler` + `CommandDedupStore` (in-memory,
OK-only, 10 min TTL, lost on restart) sit in front of it. The commands consumer
is at-most-once (offsets committed before dispatch), so Kafka redelivery is not
the duplicate source — a platform re-issue is.

**Platform-side idempotency key.** For admin system mail the platform derives one
correlation id per recipient from the required `requestId`, after name resolution and
de-duplication by `charId`: `UUIDv5(namespace = requestId, name = "SEND_MAIL:" + charId)`.
A retry of the same request reuses the same ids, so both the in-memory dedup and the
durable receipt below recognise it.

### Durable receipt (host-side contract)

The in-memory dedup does not survive a restart, so the host keeps a durable receipt per
correlation id in the game database (the generic receipt ledger of
`nx-gameservers/docs/specs/068-critical-commands-framework.md`, L5). For `SendMailCommand`
it MUST behave as follows:

1. **Ids before mutation.** The host pre-allocates every mail id the request will produce
   (one per batch) and commits a `STARTED` receipt carrying those ids before anything is
   written.
2. **Claim arbitration.** A repeat of the correlation id against an `OK` receipt replays the
   stored `createdMailIds`; against an open (`STARTED`) receipt it is refused as
   outcome-unknown (`INTERNAL_ERROR`), never re-run; against a `FAILED` receipt it re-runs,
   reopened by compare-and-set so only one contender wins.
3. **Atomic completion.** The receipt turns `OK` (with the mail ids as the stored result) in
   the **same database transaction** as the mail rows. Delivery is all-or-nothing, so the
   stored result is exactly the claimed ids. A failed completion rolls the mail rows back,
   so "mail persisted, receipt still open" cannot arise. If the commit itself fails, the
   outcome is unknown and the receipt is held as `STARTED` for recovery.
4. **Clean failure.** A delivery that provably rolled back (no mail row, no staged
   attachment left) marks the receipt `FAILED`, so a retry re-runs cleanly. Anything not
   provably undone keeps the receipt open. Every transition out of `STARTED` is a
   compare-and-set, so an attempt never overwrites a receipt another attempt already
   settled; it rolls its own batch back and replies outcome-unknown.
5. **Restart recovery.** On boot, before anything can sweep unowned mail attachments, every
   `STARTED` mail receipt is resolved by looking its claimed ids up in the mail table:
   - **every** id present: the mail went out and only its `OK` is missing (receipts written
     before rule 3). The receipt is promoted to
     `OK` with the claimed ids as the stored result, by compare-and-set on `STARTED`, so a
     retry replays instead of being refused forever;
   - **none** present: provably undelivered. Staged attachments are discarded first, then
     the receipt is marked `FAILED`;
   - **some** present, or ids unreadable: held as `STARTED` with an operator alert; retries
     stay refused until manual reconciliation.
6. **Retention.** Only settled receipts (`OK`, `FAILED`) are pruned after 30 days; an open
   receipt is never pruned, since that would let a retry deliver on top of an unresolved
   attempt.

### `itemErrors` today

The wire still models partial delivery: an OK envelope with a non-empty
`itemErrors` means the mail went out without the listed lines. Platform
consumers MUST keep reading it and MUST iterate every entry: one entry covers
one failed line, and `reason` is a free-form diagnostic, never a value to switch
on. `itemTemplateId` / `count` stay `null` when a host cannot attribute a failure
to an inbound line.

The reference host never produces it. Every OK reply carries `itemErrors: []`,
because delivery is all-or-nothing: an unknown template is refused as
`VALIDATION_FAILED` before the claim, and any other line that cannot be staged
aborts the request with `INTERNAL_ERROR` and nothing delivered. A host that does
deliver partially MUST report every dropped line here.

### Compile-time + runtime contract

- `SendMailCommand implements NxCommand<SendMailResult>` — the marker's type
  parameter binds the reply shape.
- `SendMailHandler implements CommandHandler<SendMailCommand, SendMailResult>` —
  the handler's reply type is forced to match by the SAM's bound
  `C extends NxCommand<R>`.
- Registration `commands.on(SendMailCommand.class, new SendMailHandler(...))` —
  the `<R, C extends NxCommand<R>>` upper bound on `NxCommands.on` rejects any
  handler with a different `R` at compile time.

There is no runtime way for the platform and the handler to disagree about the
reply shape. Wire-shape changes require an api version bump.
