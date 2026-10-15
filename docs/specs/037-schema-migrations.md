# Adapter-owned schema migrations

> Owner: @n1rmata

## Problem

The adapter keeps its own state in `l2nx_*` tables inside the host's database (today `l2nx_command_receipts`,
next `l2nx_offline_sessions`, see [`038`](038-app-offline-sessions.md)). Today the host creates and alters those
tables itself with hand-written DDL. Every new adapter feature that needs a column or a table therefore needs a
matching host release, and the DDL drifts between hosts and between database engines. The adapter has to own the
schema of the tables it owns, on every engine a host may run (PostgreSQL, MariaDB, MySQL), without forcing a
migration framework version on the host.

## Requirements

> Siblings: [`001-adapter-bootstrap`](001-adapter-bootstrap.md) (startup order),
> [`002-adapter-modules`](002-adapter-modules/spec.md) (`ServiceLoader` SPI mechanics),
> [`004-jdbc-connection-source`](004-jdbc-connection-source.md) (the connection the migrator uses).

**Must:**

- [todo] R1. `nx-gs-adapter-core` MUST define an SPI `SchemaMigrator`, discovered through `ServiceLoader`, and call it
  once, before any module starts, so no module ever sees a missing or outdated `l2nx_*` table. Because a host may read
  an adapter-owned table during its own boot before it starts the adapter (crash recovery of command receipts), the
  adapter MUST also expose an explicit early entry point (`NxAdapter.migrateSchema()` or equivalent) that the host
  calls first; the call is idempotent and the regular start then finds the schema current.
- [todo] R2. A new module `nx-gs-db-migrations` MUST carry the single Liquibase changelog (XML) that creates and evolves
  every adapter-owned `l2nx_*` table. It MUST use only abstract, engine-neutral changes (`createTable`, `addColumn`,
  `createIndex`, `addUniqueConstraint`) and portable column types (`uuid`, `int`, `bigint`, `varchar`, `clob`,
  `datetime` stored as UTC). No raw SQL, generated columns, partial indexes, triggers or views.
- [todo] R3. The changelog MUST run unchanged on PostgreSQL, MariaDB and MySQL.
- [todo] R4. The changelog MUST adopt an already existing `l2nx_command_receipts` table (including its `steps_done_at`
  column) without recreating it: each change is guarded with `tableExists` / `columnExists` preconditions with
  `onFail=MARK_RAN`. The DDL previously issued by the host's receipt store is removed from the host.
- [todo] R5. Two thin wrappers MUST implement `SchemaMigrator`: `nx-gs-db-migrator-liquibase4` (Java 8 bytecode,
  Liquibase 4.33.0, the last 4.x) and `nx-gs-db-migrator-liquibase5` (Java 17, Liquibase 5.x). Liquibase is
  `compileOnly` in both: the host supplies the Liquibase version it already ships, and the adapter never bundles it.
- [todo] R6. The migrator MUST use its own changelog tables `l2nx_databasechangelog` and
  `l2nx_databasechangeloglock`, so it never reads or writes the host's own Liquibase history.
- [todo] R7. The migrator MUST run in its own Liquibase `Scope` with every parameter set explicitly, so nothing from the
  host's Liquibase configuration (system properties, environment, defaults of the host's scope) leaks into the adapter
  run. Liquibase telemetry MUST be switched off where the Liquibase version has the option.
- [todo] R8. The adapter MUST NOT read the host's changelogs, and the host's own database views (the `v_*` views created
  by the host's sync-view initializer) stay on the host: they are the host's schema, not the adapter's.
- [todo] R9. Re-running the migration on an up-to-date database MUST be a no-op.

**Should:**

- [todo] R10. A database migrated by the 4.x wrapper MUST validate cleanly under the 5.x wrapper (checksums unchanged),
  so a host may upgrade Liquibase without touching the adapter schema.

**Non-goals:**

- Migrating or validating any table the adapter does not own, including the host's game tables and views.
- Down-migrations / rollback scripts. The changelog only moves forward.
- Choosing the Liquibase version for the host. The host brings one; the matching wrapper is selected by the host's
  dependency choice.

### Edge cases

- **No `SchemaMigrator` implementation on the classpath:** adapter start fails with an explicit message naming the two
  wrapper artifacts. A silent start would leave modules writing into tables that may not exist.
- **Database user without DDL rights:** adapter start fails with an explicit message naming the failing change and the
  required privilege. The failure is not downgraded to a warning, the tables are a hard dependency of the commands.
- **Stale changelog lock** (a previous run died mid-migration): the start fails with the lock holder reported. Releasing
  the lock is an operator action, the adapter does not force-release a lock it did not take.
- **Both wrappers on the classpath:** [NEEDS CLARIFICATION: which one wins, or whether start fails]. Expected to be a
  host packaging mistake; the host's fat JAR carries exactly one Liquibase and therefore one wrapper.
- **Existing `l2nx_command_receipts` without `steps_done_at`:** the column is added; with the column present the change
  is marked run.
- **Host changelog also touches `l2nx_*` tables:** unsupported. The adapter owns those tables from this spec onward and
  does not look at the host's changelog.

## Technical design

### Overview

`SchemaMigrator` is an SPI in `nx-gs-adapter-core`. One wrapper module per Liquibase major implements it and runs the
shared changelog from `nx-gs-db-migrations` against the host connection. The adapter calls it once at start, before
modules.

### Structure

- `nx-gs-adapter-core` - the `SchemaMigrator` SPI and its invocation in the startup sequence.
- `nx-gs-db-migrations` - resource-only module: the XML changelog. No code, no Liquibase dependency.
- `nx-gs-db-migrator-liquibase4` - Java 8 wrapper, `compileOnly` Liquibase 4.33.0.
- `nx-gs-db-migrator-liquibase5` - Java 17 wrapper, `compileOnly` Liquibase 5.x (5.0.0 requires Java 17).

### Key components

- **`SchemaMigrator`** - SPI: given the adapter's connection source, bring the adapter-owned schema to the current
  version or throw a descriptive exception (R1). The exact method signature is [NEEDS CLARIFICATION].
- **Changelog** - one file, ordered changesets, abstract changes with precondition guards (R2-R4).
- **Wrapper** - builds the isolated `Scope` (R7), points Liquibase at the adapter's own log/lock tables (R6), runs the
  update and maps Liquibase failures to the explicit messages above.

### Decisions

- **Liquibase `compileOnly`, host brings it.** A host ships one fat JAR with exactly one Liquibase; two copies on one
  classpath clash on classes and on `ServiceLoader` registrations. Bundling a version would also dictate the host's
  version. Hence two wrappers, one per Liquibase major, instead of one shaded implementation.
- **Own changelog tables and own `Scope`.** The host's Liquibase run and the adapter's run must not see each other's
  history or configuration.
- **Abstract changes only.** The same changelog serves three engines; engine-specific SQL would need `dbms` branching and
  per-engine tests of every statement. A uniqueness rule that must hold while a row is "active" is expressed with a
  nullable `UNIQUE` column instead of a partial index (all three engines allow many `NULL` in a unique index), see
  [`038`](038-app-offline-sessions.md).
- **Views stay on the host.** Views describe the host's game schema, which the adapter must not know.

### Rejected alternatives

- **A hand-written migrator in the adapter.** Re-implements checksums, locking, ordering and per-engine DDL that
  Liquibase already solves, and every engine quirk becomes the adapter's bug.
- **A shaded (relocated) Liquibase inside the adapter.** Liquibase discovers its extensions and loggers reflectively and
  relocation breaks that; it also doubles the Liquibase footprint in the host JVM and pins the adapter to one version.
- **Liquibase as a direct, regular dependency of `nx-gs-adapter-core`.** Forces a version on the host and puts a second
  Liquibase on the classpath of any host that already has one; core must also stay Java 8 while Liquibase 5 is Java 17.

## Rollout

Additive for hosts that already carry Liquibase: add the matching wrapper and `nx-gs-db-migrations`, drop the host's own
DDL for `l2nx_*` tables. A host without any wrapper fails at start by design (R1), so the host release that upgrades the
adapter adds the wrapper in the same change.

Test plan: the changelog on Testcontainers PostgreSQL, MariaDB and MySQL, each under both wrappers; a second run is a
no-op; a database migrated under the 4.x wrapper validates under the 5.x wrapper (R10); adoption of a pre-existing
`l2nx_command_receipts` with and without `steps_done_at`; failure modes (no implementation, no DDL rights, stale lock).

## Open questions

- [ ] [NEEDS CLARIFICATION: `SchemaMigrator` method signature and the exception type used for the explicit start failures]
- [ ] [NEEDS CLARIFICATION: behavior when both wrappers are on the classpath]
- [ ] [assumed: Liquibase 4.33.0 is the last 4.x release, as stated in the design decision; to be re-checked before the pin]
- [ ] Moving the SQL of `l2nx_command_receipts` out of the host into an adapter-side store is deferred, see
      [`TODO §8`](../TODO.md#8-move-l2nx_command_receipts-sql-from-the-host-into-an-adapter-store). This spec only moves the
      table's DDL.

## Links

- Consumer of the migrated schema: [`038-app-offline-sessions`](038-app-offline-sessions.md)
- Receipts: [`009-commands/spec.md`](009-commands/spec.md)
- Counterpart in the host repo: host documentation, not linked here (this repository does not name tenant hosts).
