# nx-gs-adapter

L2NX game-server adapter — multimodule library that embeds into the JVM process of an L2J / Lucera /
Essence game server core and connects it to the L2NX platform.

Integration guide, configuration reference and changelog: **https://l2nx.dev/adapter/overview/**.

## Project

- **Organization:** l2nx
- **Group:** `app.l2nx`
- **Java:** 8 (source + target); `nx-gs-db-migrator-liquibase5` alone is Java 17 (Liquibase 5)
- **Build:** Gradle (Kotlin DSL), multimodule, per-module versioning

## Modules

| Module               | Artifact                      | Published            | Purpose                                                                            |
|----------------------|-------------------------------|----------------------|------------------------------------------------------------------------------------|
| `nx-gs-adapter-api`  | `app.l2nx:nx-gs-adapter-api`  | yes                  | Wire contracts (REST + Kafka DTOs) + Tier-1/Tier-3 SPI shared with the platform    |
| `nx-gs-commons`      | `app.l2nx:nx-gs-commons`      | yes                  | Shared utilities for adapter modules and host providers                            |
| `nx-gs-kafka`        | `app.l2nx:nx-gs-kafka`        | yes                  | Lightweight Kafka client facade for Java 8+ host JVMs                              |
| `nx-gs-adapter-core` | `app.l2nx:nx-gs-adapter-core` | yes                  | Adapter runtime — connect, heartbeat, ServiceLoader-based module discovery, app-session engine |
| `nx-gs-db-sync-core` | `app.l2nx:nx-gs-db-sync-core` | yes                  | DB-sync `AdapterModule` — CRC32 CDC engine, Tier-2 SPI host, per-entity stats      |
| `nx-gs-runtime-sync-core` | `app.l2nx:nx-gs-runtime-sync-core` | yes         | Runtime-sync `AdapterModule` — in-memory snapshot + diff of live game state        |
| `nx-gs-gd-sync-core` | `app.l2nx:nx-gs-gd-sync-core` | yes                  | Game-data sync `AdapterModule` — static catalog snapshots                          |
| `nx-gs-db-migrations` | `app.l2nx:nx-gs-db-migrations` | yes                 | Liquibase changelog of the adapter-owned `l2nx_*` tables (resources only)          |
| `nx-gs-db-migrator-liquibase5` | `app.l2nx:nx-gs-db-migrator-liquibase5` | yes | `SchemaMigrator` on Liquibase 5.x (Java 17+); the host brings `liquibase-core` |
| `nx-gs-log`          | —                             | no (shadow-included) | Internal logging facade — bundled into `nx-gs-kafka` and `nx-gs-adapter-core` jars |

Future modules:

- `nx-gs-db-l2j` — DB sync for vanilla L2J schema
- `nx-gs-db-lucera` — DB sync for vanilla Lucera schema
- `nx-gs-dp-l2j` — Datapack sync for vanilla L2J
- `nx-gs-dp-lucera` — Datapack sync for vanilla Lucera

Per-client overrides (private repos) extend vanilla via the template-method pattern (e.g.
`nx-gs-db-<client>`).

## Distribution & licensing

Open-core model — Apache 2.0, published to Maven Central:

- `nx-gs-adapter-api` (contracts + SPI)
- `nx-gs-kafka` (Kafka facade)
- `nx-gs-adapter-core` (runtime)
- `nx-gs-commons` (shared utilities)
- `nx-gs-db-sync-core` (DB-sync engine + Tier-2 SPI host)
- `nx-gs-runtime-sync-core`, `nx-gs-gd-sync-core` (runtime-state and game-data sync modules)
- `nx-gs-db-migrations`, `nx-gs-db-migrator-liquibase5` (schema of the adapter-owned `l2nx_*` tables; Liquibase
  itself is not redistributed)
- `nx-gs-db-l2j`, `nx-gs-db-lucera`, `nx-gs-dp-l2j`, `nx-gs-dp-lucera` (vanilla sync
  modules — community schemas, future)

Operators can audit every artifact that loads into their host JVM and drop in via Maven
Central without a private-repo handshake.

Per-client modules (`nx-gs-db-l2j-<client>`, `nx-gs-dp-<core>-<client>`) that encode a
client-proprietary DB schema or datapack layout are shipped privately to that client
only — never published to Maven Central — to avoid leaking their schema internals.
They extend the vanilla module via the template-method pattern.

## Versioning

Per-module independent versioning via slash-namespaced git tags:

- `api/vX.Y.Z` → release `nx-gs-adapter-api`
- `commons/vX.Y.Z` → release `nx-gs-commons`
- `kafka/vX.Y.Z` → release `nx-gs-kafka`
- `core/vX.Y.Z` → release `nx-gs-adapter-core`
- `db-sync/vX.Y.Z` → release `nx-gs-db-sync-core`
- `runtime-sync/vX.Y.Z` → release `nx-gs-runtime-sync-core`
- `gd-sync/vX.Y.Z` → release `nx-gs-gd-sync-core`
- `db-migrations/vX.Y.Z` → release `nx-gs-db-migrations`
- `db-migrator-liquibase5/vX.Y.Z` → release `nx-gs-db-migrator-liquibase5`
- `db-l2j/v…`, `dp-l2j/v…`, `db-lucera/v…`, `dp-lucera/v…` (future)

A successful release also publishes its GitHub release notes to https://l2nx.dev/adapter/changelog/.

Each module's `build.gradle.kts` declares
`version = findProperty("${project.name}.version") as String? ?: "<base>"`.
CI parses the tag prefix and passes `-P<module>.version=X.Y.Z` to that subproject only —
other modules stay at their fallback `<base>` version and are NOT republished by the same tag.

`nx-gs-log` is internal-only (shadow-included into consuming jars) and has no tag namespace.

## Quick start

Drop the fat JAR into the game-server classpath and configure the adapter via a properties file.
The resolution chain (file-first, sysprop-fallback per key):

1. **`-Dl2nx.config-file=<path>`** — explicit path (absolute or relative to the JVM working
   directory). If set but unreadable, the adapter fails loud with `IllegalStateException` so
   operator typos do not silently fall through.
2. **`l2nx.properties` next to the JVM working directory** — implicit fallback when
   `-Dl2nx.config-file` is unset. Missing file is OK — the adapter then relies on JVM system
   properties.
3. **`-Dl2nx.<key>=<value>`** — per-key override consulted only when the file does not provide
   the key.

Environment variables are not supported.

A starter file with placeholder values lives at the repo root: [`l2nx.properties`](./l2nx.properties) —
copy it next to your game-server working directory (or to any path you point `-Dl2nx.config-file`
at) and fill in the real `gs-key` + `platform-url`.

Required: `l2nx.gs-key`, `l2nx.platform-url`. Optional: `l2nx.enabled` (default `false` — must be
`true` for the adapter to actually run; otherwise it transitions to `DISABLED` at startup).

**Database:** there are no `l2nx.db.*` keys. The adapter reads the game database only through the
`JdbcConnectionSource` the host registers via `META-INF/services`, reusing the host's pool; DB credentials never
reach the adapter config or the platform. A bundled-pool fallback configured by `l2nx.db.*` is planned, not
built (spec 004, R6).

**Adapter-owned tables (game-server hosts):** the adapter keeps its state in `l2nx_*` tables of the host
database and migrates them itself (spec 037). Put `nx-gs-db-migrator-liquibase5` and `org.liquibase:liquibase-core`
5.x on the classpath and register a `JdbcConnectionSource`; without them a game-server adapter goes `FAILED` at
start. A host that reads an adapter-owned table during its own boot calls `NxAdapter.migrateSchema()` first.

**Every key** (CDC engine, runtime sync, game data sync, events, commands, Kafka producer, IO pool) with its default
and meaning: **https://l2nx.dev/adapter/configuration/**. The starter `l2nx.properties` carries the game-server tuning keys with their defaults.

```bash
# Option A: l2nx.properties in the JVM working directory (implicit fallback)
java -cp "l2j-server.jar:nx-gs-adapter-core-all.jar" org.l2j.server.GameServer
# where ./l2nx.properties (relative to where you launch java) contains:
#   l2nx.gs-key=nx_sk_...
#   l2nx.platform-url=https://acme.api.l2nx.app
#   l2nx.enabled=true

# Option B: explicit path (recommended for prod — keeps the secret outside the binary)
java -Dl2nx.config-file=/etc/l2nx/adapter.properties \
     -cp "l2j-server.jar:nx-gs-adapter-core-all.jar" org.l2j.server.GameServer
```

See [`docs/`](./docs/) for architectural detail.

## Commands

```bash
./gradlew build               # compile + test
./gradlew :nx-gs-adapter-core:shadowJar    # build the fat JAR
./gradlew publishToMavenLocal # publish all modules to ~/.m2
```
