# Adapter-owned schema migrations

> Owner: @n1rmata

## Problem

The adapter keeps its own state in `l2nx_*` tables inside the host's database (today `l2nx_command_receipts`,
next `l2nx_offline_sessions`, see [`038`](038-app-offline-sessions.md)). Today the host creates and alters those
tables itself with hand-written DDL. Every new adapter feature that needs a column or a table therefore needs a
matching host release, and the DDL drifts between hosts and between database engines. The adapter has to own the
schema of the tables it owns, on every engine a host may run (PostgreSQL, MariaDB, MySQL), without bundling a
migration framework into the host.

## Requirements

> Siblings: [`001-adapter-bootstrap`](001-adapter-bootstrap.md) (startup order),
> [`002-adapter-modules`](002-adapter-modules/spec.md) (`ServiceLoader` SPI mechanics),
> [`004-jdbc-connection-source`](004-jdbc-connection-source.md) (the connection the migrator uses).

**Must:**

- [done] R1. `nx-gs-adapter-api` MUST define an SPI `SchemaMigrator` (next to `JdbcConnectionSource`), which
  `nx-gs-adapter-core` discovers through `ServiceLoader` and calls once, before any module starts, so no module ever sees a missing or outdated `l2nx_*` table. Because a host may read
  an adapter-owned table during its own boot before it starts the adapter (crash recovery of command receipts), the
  adapter MUST also expose an explicit early entry point (`NxAdapter.migrateSchema()` or equivalent) that the host
  calls first; the call is idempotent and the regular start then finds the schema current.
- [done] R2. A new module `nx-gs-db-migrations` MUST carry the single Liquibase changelog (XML) that creates and evolves
  every adapter-owned `l2nx_*` table. It MUST use only abstract, engine-neutral changes (`createTable`, `addColumn`,
  `createIndex`, `addUniqueConstraint`) and portable column types (`uuid`, `int`, `bigint`, `varchar`, `clob`,
  `datetime` stored as UTC). No raw SQL, generated columns, partial indexes, triggers or views. One sanctioned
  exception: `0007-command-receipts-utc-datetime`, a `dbms="mariadb,mysql"` raw-SQL repair (see Edge cases).
- [done] R3. The changelog MUST run unchanged on PostgreSQL, MariaDB and MySQL.
- [wip] R4. The changelog MUST adopt an already existing `l2nx_command_receipts` table (including its `steps_done_at`
  column) without recreating it: each change is guarded with `tableExists` / `columnExists` preconditions with
  `onFail=MARK_RAN`. The DDL previously issued by the host's receipt store is removed from the host (host side, open).
- [done] R5. One thin wrapper, `nx-gs-db-migrator-liquibase5` (Java 17+, Liquibase 5.x), MUST implement
  `SchemaMigrator`. Liquibase is `compileOnly`: the host adds `liquibase-core` 5.x itself, and the adapter never bundles
  it. A 4.x wrapper for a host on Java 8/11 is deferred until such a host appears
  ([`TODO §12`](../TODO.md#12-liquibase-4x-wrapper-for-a-java-811-host)). Liquibase 5 Community is FSL-licensed (each
  version reverts to Apache 2.0 after two years); the adapter does not redistribute it. Liquibase 5.0 ships without JDBC
  drivers: the wrapper uses the host's JDBC connection, and MariaDB support is verified by the Testcontainers test
  before the version is pinned.
- [done] R6. The migrator MUST use its own changelog tables `l2nx_databasechangelog` and
  `l2nx_databasechangeloglock`, so it never reads or writes the host's own Liquibase history.
- [done] R7. The migrator MUST run in its own Liquibase `Scope` with every parameter set explicitly, so nothing from the
  host's Liquibase configuration (system properties, environment, defaults of the host's scope) leaks into the adapter
  run. Liquibase telemetry MUST be switched off where the Liquibase version has the option.
- [done] R8. The adapter MUST NOT read the host's changelogs, and the host's own database views (the `v_*` views created
  by the host's sync-view initializer) stay on the host: they are the host's schema, not the adapter's.
- [done] R9. Re-running the migration on an up-to-date database MUST be a no-op.

**Non-goals:**

- Migrating or validating any table the adapter does not own, including the host's game tables and views.
- Down-migrations / rollback scripts. The changelog only moves forward.
- Choosing the Liquibase 5.x minor for the host. The host brings one.

### Edge cases

- **No `SchemaMigrator` implementation on the classpath:** adapter start fails with an explicit message naming the
  wrapper artifact. A silent start would leave modules writing into tables that may not exist.
- **`liquibase-core` missing, or a JVM older than 17:** the wrapper cannot load (`NoClassDefFoundError`,
  `UnsupportedClassVersionError` inside a `ServiceConfigurationError`); start fails with a message naming the missing
  library or the Java 17 requirement. Any `Error` from the migration is recorded as the run-once failure like a checked
  one (except a `VirtualMachineError`, which is rethrown), the adapter goes `FAILED` and app sessions are disabled, so a
  login never waits for an engine that will not boot.
- **Database user without DDL rights:** adapter start fails with an explicit message naming the failing change and the
  required privilege. The failure is not downgraded to a warning, the tables are a hard dependency of the commands.
  The migrator and the adapter's stores use the host's one `JdbcConnectionSource`, so its user needs write and DDL rights
  on the `l2nx_*` tables (and `CREATE` in the schema); least privilege is `SELECT` on the host's tables plus those rights
  on `l2nx_*` only (see [`004`](004-jdbc-connection-source.md) R3).
- **Stale changelog lock** (a previous run died mid-migration): the start fails with the lock holder reported. Releasing
  the lock is an operator action, the adapter does not force-release a lock it did not take.
- **Existing `l2nx_command_receipts` without `steps_done_at`:** the column is added; with the column present the change
  is marked run.
- **Existing `l2nx_command_receipts` with `TIMESTAMP` columns (MariaDB/MySQL):** `0007` converts `created_at`,
  `completed_at` and `steps_done_at` to `DATETIME` under a `+00:00` session, so legacy values become UTC wall time, and
  restores the session zone afterwards (a failed `ALTER` leaves that one pooled connection on `+00:00`; the migration
  failure stops the start anyway). It runs once on every MariaDB/MySQL database: on a fresh `DATETIME` table the
  `ALTER` changes nothing, which is why it carries no precondition (Liquibase 5 disables `sqlCheck` by default).
  PostgreSQL skips it. See [`040`](040-command-receipts.md).
- **Host changelog also touches `l2nx_*` tables:** unsupported. The adapter owns those tables from this spec onward and
  does not look at the host's changelog.

## Technical design

### Overview

`SchemaMigrator` is an SPI in `nx-gs-adapter-api`. The Liquibase 5 wrapper module implements it and runs the
shared changelog from `nx-gs-db-migrations` against the host connection. `nx-gs-adapter-core` calls it once per JVM:
from `NxAdapter.migrateSchema()` when the host calls it early, otherwise from `start()` before the platform connect.

### Structure

- `nx-gs-adapter-api` - the `SchemaMigrator` SPI and the checked `SchemaMigrationException`.
- `nx-gs-adapter-core` - `SchemaMigration` (discovery of exactly one migrator and one `JdbcConnectionSource`, run-once
  latch) and its place in the startup sequence.
- `nx-gs-db-migrations` - resource-only module: the XML changelog. No code, no Liquibase dependency.
- `nx-gs-db-migrator-liquibase5` - Java 17+ wrapper, `compileOnly` Liquibase 5.x (5.0.0 requires Java 17).

### Key components

- **`SchemaMigrator`** - SPI: `void migrate(JdbcConnectionSource)` brings the adapter-owned schema to the current
  version or throws `SchemaMigrationException` with a descriptive message (R1). The explicit start failures of the edge
  cases above are this exception.
- **Changelog** - one file, ordered changesets, abstract changes with precondition guards (R2-R4).
- **Wrapper** - builds the isolated `Scope` (R7), points Liquibase at the adapter's own log/lock tables (R6), runs the
  update and maps Liquibase failures to the explicit messages above. It hands the borrowed connection back with the
  read-only and auto-commit modes it was lent with; a failure while restoring them is attached to the migration failure
  as suppressed, never in its place.
- **Startup** - `NxAdapter.migrateSchema()` is public, static and idempotent: the first call migrates, later calls
  return at once or rethrow the first failure, concurrent callers wait. `start()` of a game-server host runs it on the
  connect thread before `POST /connect`, so commands and modules never see an old schema; a failure puts the adapter
  into `FAILED` and nothing connects. With `l2nx.enabled=false` `start()` does nothing, but an explicit
  `migrateSchema()` still migrates: the tables are host dependencies, not platform ones.

### Decisions

- **Liquibase `compileOnly`, host brings it.** A host ships one fat JAR with exactly one Liquibase; two copies on one
  classpath clash on classes and on `ServiceLoader` registrations. Bundling a version would also dictate the host's
  version. A host without Liquibase adds `liquibase-core` 5.x itself (host documentation only; the adapter docs stay
  generic).
- **Liquibase 5 only.** The adapter targets hosts on Java 17+ for the migrator; the 4.x line (Java 8 bytecode) is a
  deferred backlog item, not built speculatively.
- **Own changelog tables and own `Scope`.** The host's Liquibase run and the adapter's run must not see each other's
  history or configuration.
- **Abstract changes only.** The same changelog serves three engines; engine-specific SQL would need `dbms` branching and
  per-engine tests of every statement. A uniqueness rule that must hold while a row is "active" is expressed with a
  nullable `UNIQUE` column instead of a partial index (all three engines allow many `NULL` in a unique index), see
  [`038`](038-app-offline-sessions.md).
- **Views stay on the host.** Views describe the host's game schema, which the adapter must not know.
- **Checked `SchemaMigrationException`.** The tables are a hard dependency of the commands, so a host calling the
  migration early must decide explicitly whether its boot stops.
- **A failed migration is not retried in the same JVM.** The host's early call and the adapter's own start must agree
  on the outcome; the causes (privileges, a stale lock) need an operator anyway.
- **Login-server hosts skip the migration in `start()`.** The adapter-owned tables back game-server commands and
  sessions; a login server has none and must not need Liquibase on its classpath.
- **Lock wait of one minute, then fail.** A concurrent run of this changelog finishes in seconds, so a lock held longer
  is reported as stale with its holder. An up-to-date schema never takes the lock, so a stale lock only blocks a start
  that has changes pending.
- **Isolation limits.** Every setting is pinned in the adapter's `Scope` (it outranks system properties and the
  environment); catalog and schema are set on the `Database` object. The one setting without a pinned value is
  `liquibase.liquibaseTablespaceName`, whose unset value is `null` and therefore cannot be pinned: a host that sets it
  globally puts the two adapter changelog tables into that tablespace.

### Rejected alternatives

- **A hand-written migrator in the adapter.** Re-implements checksums, locking, ordering and per-engine DDL that
  Liquibase already solves, and every engine quirk becomes the adapter's bug.
- **A shaded (relocated) Liquibase inside the adapter.** Liquibase discovers its extensions and loggers reflectively and
  relocation breaks that; it also doubles the Liquibase footprint in the host JVM and pins the adapter to one version.
- **Liquibase as a direct, regular dependency of `nx-gs-adapter-core`.** Forces a version on the host and puts a second
  Liquibase on the classpath of any host that already has one; core must also stay Java 8 while Liquibase 5 is Java 17.

## Rollout

Additive: add the wrapper, `liquibase-core` 5.x (if the host does not carry it yet) and `nx-gs-db-migrations`, drop the
host's own DDL for `l2nx_*` tables. A host without the wrapper fails at start by design (R1), so the host release that
upgrades the adapter adds it in the same change.

Test plan: the changelog on Testcontainers PostgreSQL, MariaDB and MySQL under the 5.x wrapper (the MariaDB run is the
gate for pinning the version); a second run is a no-op; adoption of a pre-existing `l2nx_command_receipts` with and
without `steps_done_at`; failure modes (no implementation, no DDL rights, stale lock).

## Open questions

- [x] Moving the SQL of `l2nx_command_receipts` out of the host into an adapter-side store: done in
      [`040-command-receipts`](040-command-receipts.md). This spec only moves the table's DDL.

## Links

- Consumer of the migrated schema: [`038-app-offline-sessions`](038-app-offline-sessions.md)
- Receipts: [`040-command-receipts`](040-command-receipts.md), [`009-commands/spec.md`](009-commands/spec.md)
- Counterpart in the host repo: host documentation, not linked here (this repository does not name tenant hosts).
