# Commands — ReadMail

> Owner: @n1rmata
> Status: design, not released. First carried by api `0.105.0`.

## Problem

A player reading mail from the app marks it read in the platform's own view only if the game also knows. Reading
happens in the game client natively and publishes `MailRead`; the app has no client, so it needs a command that makes
the host do the same thing for a receiver that may be online or offline.

Audience: platform mail-flow authors composing the command; host operators wiring the handler.

## Requirements

> Siblings: [`spec.md`](spec.md) (rail), [`accept-mail.md`](accept-mail.md) (recipient-side command this one mirrors),
> [`catalog.md`](catalog.md) (wire contract entry).

**Must:**

- [todo] R1. `nx-gs-adapter-api.kafka.commands.mail.ReadMailCommand` MUST ship as `NxCommand<Void>`, routed by the
  simple name `ReadMailCommand` (replies under `ReadMailResult`, derived from the command name; enum constant name for
  the platform-side audit: `READ_MAIL`).
- [todo] R2. Payload fields:

  | Field    | Type   | Required | Notes                                                                               |
  | -------- | ------ | -------- | ----------------------------------------------------------------------------------- |
  | `charId` | `Long` | yes      | The receiver; a different character on the live mail is `MAIL_NOT_FOUND`            |
  | `mailId` | `Long` | yes      | The host's mail id (an `int` in the core), the value `MailReadEvent.mailId` carries |

  Gson bypasses the constructor, so the handler re-validates: a null field, or a `mailId` outside `int` range, is
  `VALIDATION_FAILED` with a `field` extension.

- [todo] R3. The command moves nothing and charges nothing, so it has no `idempotencyKey`, no `deadline` and is not
  `OwnerVerified`. It is idempotent by nature: a mail that is already read answers `OK` and the host does not move the
  read time.
- [todo] R4. It works for an offline receiver: mail lives in the host's mail storage regardless of presence. The host
  marks it read the same way the native in-game read does.
- [todo] R5. Outcomes:

  | Status              | Reason                                                                                       |
  | ------------------- | -------------------------------------------------------------------------------------------- |
  | `OK`                | The mail is read now, or was already read                                                    |
  | `NOT_FOUND`         | `MAIL_NOT_FOUND` (`WellKnownReadMailReasons`): no such mail, or `charId` is not its receiver |
  | `VALIDATION_FAILED` | Malformed command, carrying a `field` extension                                              |
  | `INTERNAL_ERROR`    | Host-side failure                                                                            |

- [todo] R6. The platform learns the new state only from the `MailRead` event. The host MUST publish it when the mail
  turns read, whichever path caused it (this command or the native read). A repeat read publishes nothing new.

## Notes

- Not found and "belongs to someone else" are one answer on purpose: the command must not tell a caller which mail ids
  exist.
- Deleting, refusing and returning a mail stay in the game client (see the non-goals of
  [`accept-mail.md`](accept-mail.md)).
