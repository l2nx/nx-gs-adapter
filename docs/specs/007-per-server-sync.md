# Per-Server Sync

## Problem

`nx-gs-adapter` publishes sync events to per-tenant Kafka topics
(`<tenantSlug>.gs.sync.db.{character,clan,item}`, `.runtime.character`) shared by
every game server of the tenant. Today the records carry no server identity, so on the
`nx-gameservers` side two `character_id=1` rows from different servers of one tenant
collide on the PK `(tenant_id, id)` and overwrite each other. Server identity has to
travel with every sync event so the consumer can scope rows per server without changing
the `SyncEvent<T>` wire schema.

Audience: adapter-side publishers (`db-sync`, `runtime-sync`, `heartbeat`), the platform
consumer `nx-gameservers`, `nx-tenants` (source of the server catalog), and operators
(running several servers in one tenant without collisions).

## Requirements

**Must:**

- [done] R1. `nx-gs-adapter-api` MUST expose the wire-level header contract in
  a new class `app.l2nx.gs.adapter.api.kafka.NxHeaders`:
  - constant `String SERVER_ID = "Server-Id"` — the header name
  - `static byte[] encodeUuid(UUID)` and `static UUID decodeUuid(byte[])` —
    encoding rule. Value is the raw 16-byte UUID: `mostSigBits` big-endian
    followed by `leastSigBits` big-endian, NOT the 36-char string form.
    Implementation is pure JDK (`java.util.UUID` + `java.nio.ByteBuffer`),
    no Kafka coupling — keeps `nx-gs-adapter-api`'s zero-runtime-deps rule
    (Java 8, jspecify only).
  - SC1. `decodeUuid` rejects any input ≠ 16 bytes with a clear
    `IllegalArgumentException`.

  This class is the single source of truth for both sides — `nx-gs-kafka`
  (adapter-side producer wrapper) and `nx-gameservers` (Spring-Kafka consumer)
  read the constant + helpers from the same artifact, no string duplication
  and no encoding drift. Future Kafka-transport headers (e.g. reply-flow
  correlation ids) land in the same class. The Kafka `NxHeaders`
  (`app.l2nx.gs.adapter.api.kafka.NxHeaders`) is intentionally a different
  type from the HTTP `NxHeaders` in `nx-libs/common` — different transport,
  different serdes, different packages; no compile-time collision.

- [done] R2. `nx-gs-kafka` MUST provide a thin Kafka-binding helper layered
  over `NxHeaders`: a producer-wrapper that stamps `Server-Id` on every outbound
  record via `record.headers().add(name, NxHeaders.encodeUuid(serverId))`, plus an
  optional reader-helper `Headers → @Nullable UUID` for adapter-side tests / future
  reply-flow modules. `nx-gs-kafka` brings no new constants — just `org.apache.kafka.*`
  ↔ `NxHeaders` glue.

- [done] R3. Adapter-side producers MUST stamp `Server-Id` on every sync record
  published to topics under `ctx.syncTopics().db()` and `ctx.syncTopics().runtime()`.
  The serverId source is `ConnectContext.getServerId()`, captured once after
  `/connect` and reused for the lifetime of the adapter process. Sync modules
  (`db-sync`, `runtime-sync`) MUST NOT pass serverId per-call — wiring layer
  injects it transparently.
  - SC2. No `db-sync` / `runtime-sync` code references `serverId` directly when
    publishing — search-grep returns zero hits in their publisher classes.

- [done] R4. Heartbeat publisher MUST also stamp `Server-Id` on every
  `HeartbeatEvent`, even though server identity is already carried in the
  payload. Reason: consistency for any cross-cutting consumer (future
  observability / dead-letter / multi-tenant routing) that wants to read
  identity uniformly across all `nx-gs-adapter`-issued records pre-JSON-parse.

- [done] R5. `nx-gameservers` `SyncEventConsumer` MUST extract `Server-Id`
  from `ConsumerRecord.headers()` via `NxHeaders.decodeUuid(...)` BEFORE
  attempting JSON parse. The extracted `UUID` is then used together with
  `tenantId` (resolved from topic slug) to group the per-poll batch into
  per-`(tenantId, serverId)` chunks dispatched to the matching ingestor.

- [done] R6. `nx-gameservers` MUST `WARN + skip` per-record when:
  - `Server-Id` header is absent or has invalid length
  - serverId is not present in the per-tenant active-servers cache
    (unknown server for this tenant)

  Symmetric with the existing `unknown-tenant` handling — one bad record never
  poisons the rest of the batch; offset commit proceeds for the remaining
  successful records.

- [done] R7. `nx-gameservers` schema MUST use composite primary key
  `(tenant_id, server_id, id)` on parent tables (`characters`, `clans`,
  `items`); child tables (`character_subclasses`, `clan_skills`,
  `item_attributes`) MUST carry `tenant_id` + `server_id` + parent-id and FK
  back to the parent through all three columns with `ON DELETE CASCADE`.
  Implementation MUST be a forward-only Liquibase changeset that drops and
  recreates the affected tables (no backfill, no data migration — current data
  is test-only).
  - SC3. UPSERT idempotency: re-delivery of the same `(tenant_id, server_id, id)`
    event produces the same row state.
  - SC4. DELETE event on a parent cascades to children via the three-column FK.

- [done] R8. `nx-gameservers` `TenantCache` (or its successor) MUST surface
  per-tenant active servers as a snapshot
  `Map<slug, TenantInfo{tenantId, Map<serverId, serverSlug>}>`. Refresh source
  is the extended `/internal/tenants` payload (R9). On cache-miss for a known
  tenant slug + unknown serverId the consumer triggers one single-flight
  refresh before giving up (same pattern as today's tenant-slug miss).

- [done] R9. `nx-tenants` `InternalTenantsApi` MUST return per-tenant active
  servers in the `GET /internal/tenants` payload, in-place — no separate
  endpoint, no versioned path. Shape:
  `List<TenantSummary{id, slug, servers: List<GameServerSummary{id, serverSlug}>}>`.
  Servers MUST be filtered to `active=true`. The endpoint stays JWT-bypass +
  nginx-ACL-gated; no permission checks. Single consumer today is
  `nx-gameservers`.

**Should:**

**Could:**

**Non-goals:**

- Changing the wire schema of `SyncEvent<T>` or of the per-entity DTOs in `nx-gs-adapter-api`
  (`CharacterDbDto`, `ClanDbDto`, …) — the header covers the transport layer.
- A per-server topic split (e.g. `<slug>.<server>.gs.sync.db.character`) — topics stay
  shared per tenant, the server is metadata only.
- Refactoring the multi-tenancy perimeter — `tenantSlug` stays in the topic name and remains
  its source of truth.
- A REST surface `nx-tenants /servers` (CRUD endpoints) — the feature does not touch the
  external player / operator API, only the internal cross-service contract.
- Backwards compatibility / data migration in `nx-gameservers` — the data is test-only,
  drop & recreate is acceptable.
- Additional headers (`Tenant-Slug`, `Server-Slug`) — the tenant slug is derivable from the
  topic, the rest from the cache.

### Edge cases

- `Server-Id` header absent on an inbound record → WARN + skip (R6).
- Header present but its length ≠ 16 bytes → WARN + skip (R6).
- Header valid, but the serverId is not in the cache for a known tenant (server deactivated
  after the refresh / just registered) → single-flight refresh, on a second miss WARN + skip.
- One tenant has ≥ 2 servers both writing the same partition at once (same key = entityId):
  per-server FIFO holds through single-producer ordering, and the two servers write different
  DB rows — no conflict.
- Heartbeat from a server missing from the cache → WARN + skip, symmetric with sync events
  (if the consumer applies the same contract; for `nx-tenants` the heartbeat may stay
  payload-driven, the header being purely for observability).

## Open questions

- [assumed: the platform heartbeat consumer stays payload-driven — the `Server-Id` header on
  the heartbeat record is just-in-case for future cross-cutting consumers; no `nx-tenants`
  logic is built on it within this feature]
- [assumed: the PLAN file lives in `docs/specs/007-per-server-sync/.plan/` as the usual specl
  flow, milestones per repo — adapter-api-contract / kafka-binding / adapter-wiring /
  nx-tenants-api / nx-gameservers-schema-and-consumer]
- "Where does the header constant + encoding live?" → resolved: split between
  `nx-gs-adapter-api.NxHeaders` (constant + pure-JDK encoding helpers,
  R1) and `nx-gs-kafka` (Kafka-Headers binding glue, R2). Both nx-gs-adapter
  and nx-gameservers depend on `nx-gs-adapter-api` directly, no string
  duplication, no encoding drift.

---

## Technical design

`per-server-sync` is a transport layer on Kafka headers that adds the identity marker
`Server-Id` (raw 16-byte UUID) to every adapter sync record and extends the consumer
(nx-gameservers) to per-server-scoped ingestion. The feature spans three repositories:
`nx-gs-adapter` (Kafka facade + producer wiring + sync / heartbeat modules), `nx-gameservers`
(header extraction + schema with a composite PK + tenant cache with the server catalog),
`nx-tenants` (the internal API returns the tenant's active servers). The `SyncEvent<T>` wire
schema in `nx-gs-adapter-api` does not change.

### Structure

- `nx-gs-adapter/nx-gs-adapter-api/src/main/java/app/l2nx/gs/adapter/api/kafka/NxHeaders.java`
  — wire contract: the `SERVER_ID` constant + pure-JDK helpers `encodeUuid(UUID)` /
  `decodeUuid(byte[])`. Single source of truth for the header name and the encoding (16 raw
  bytes), used by both sides through the dependency on `nx-gs-adapter-api`.
- `nx-gs-adapter/nx-gs-kafka/src/main/java/app/l2nx/gs/kafka/producer/`
  — Kafka-binding wrapper over `NxProducer` that stamps the connection-scoped `Server-Id`
  through `NxHeaders.encodeUuid` on every `send(...)`. Optional reader-helper
  `Headers → @Nullable UUID` for adapter-side tests and future reply-flow modules.
- `nx-gs-adapter/nx-gs-adapter-core/src/main/java/app/l2nx/gs/adapter/core/`
  — the wiring point: after a successful `/connect` adapter-core wraps the base `NxProducer`
  in the server-stamping wrapper and hands it to the modules through `ConnectContext` (or
  through a Tier-2 Kafka-publish capability once the SPI has one).
- `nx-gs-adapter/nx-gs-db-sync-core/`, `nx-gs-runtime-sync-core/` — the publisher classes
  switch from the bare `NxProducer` to the server-stamping wrapper; the per-call API does not
  change.
- `nx-gs-adapter/nx-gs-adapter-core/.../heartbeat/HeartbeatService.java`
  — publishes the heartbeat through the same wrapper.
- `nx-gameservers/src/main/java/app/l2nx/gameservers/infra/kafka/SyncEventConsumer.java`
  — pre-parse header extraction; the dispatch group key changes from `tenantId` to
  `(tenantId, serverId)`.
- `nx-gameservers/src/main/java/app/l2nx/gameservers/infra/tenants/TenantCache.java`
  — extended snapshot schema: `Map<slug, TenantInfo>` where `TenantInfo` carries
  `tenantId` and `Map<serverId, serverSlug>`.
- `nx-gameservers/src/main/resources/db/liquibase/v1.0.0_baseline.sql` — the baseline
  changeset is updated in place: 6 tables with the three-column PK `(tenant_id, server_id, id)`
  on parents and FK + cascade on children. The data is testing-only, dev wipes the DB on
  apply.
- `nx-gameservers/src/main/java/app/l2nx/gameservers/ingest/*Ingestor.java`
  — UPSERT / DELETE SQL switches to the three-column PK; the batch dedup key becomes
  `(server_id, pk)`.
- `nx-tenants/src/main/java/app/l2nx/tenants/api/internal/tenant/InternalTenantsApi.java`
  — extends the contract payload.
- `nx-tenants/src/main/java/app/l2nx/tenants/domain/tenant/TenantSummary.java`
  — adds the field `List<GameServerSummary> servers`.
- `nx-tenants/src/main/java/app/l2nx/tenants/infra/postgres/`
  — query JOIN `tenants × game_servers WHERE active=true`.

### Key components

- **`NxHeaders` (Kafka)** (implements R1) — `app.l2nx.gs.adapter.api.kafka.NxHeaders` in
  `nx-gs-adapter-api`. Public constant `SERVER_ID = "Server-Id"` + pure-JDK helpers
  `encodeUuid(UUID): byte[]` and `decodeUuid(byte[]): UUID`. Encoding: 16 raw bytes,
  `mostSigBits` big-endian then `leastSigBits` big-endian. `decodeUuid` throws
  `IllegalArgumentException` on a length ≠ 16. Future Kafka headers (reply-flow correlation,
  observability) are added as constants to the same class.
- **Server-stamping producer wrapper** (implements R2, R3, R4) — a wrapper over `NxProducer`
  in `nx-gs-kafka`; it holds the `serverId` (16 raw bytes, pre-encoded once through
  `NxHeaders.encodeUuid`) in a final field and completes `ProducerRecord.headers()` on every
  `send(...)`. The wrapper is created in `nx-gs-adapter-core` after `/connect` and handed to
  the sync modules and the heartbeat service; the modules never see `serverId` themselves.
- **`SyncEventConsumer` (header extraction)** (implements R5, R6) — before
  `gson.fromJson(...)` it reads `record.headers().lastHeader(NxHeaders.SERVER_ID)` and decodes
  it through `NxHeaders.decodeUuid`. The dispatch group key becomes the pair
  `(tenantId, serverId)`. Missing / invalid header → WARN + skip without counting it in
  byTenant. Unknown serverId for a known tenantId → WARN + skip after a single-flight cache
  refresh.
- **`TenantCache` extended snapshot** (implements R8) — snapshot `Map<slug, TenantInfo>`
  through an `AtomicReference`, single-flight refresh through an `AtomicBoolean`, hydrated
  from the extended `/internal/tenants`. Method
  `resolveServer(tenantId, serverId): @Nullable String slug` for logging.
- **`InternalTenantsApi` extended payload** (implements R9) — the controller returns
  `List<TenantSummary>` where each `TenantSummary` includes `List<GameServerSummary>` with the
  active servers. The repository query does
  `LEFT JOIN game_servers ON game_servers.tenant_id = tenants.id AND game_servers.active = true`
  in one round-trip; the mapper aggregates the rows by `tenant.id`.
- **`nx-gameservers` schema baseline** (implements R7) — `v1.0.0_baseline.sql` updated in
  place: 6 tables with the three-column PK `(tenant_id, server_id, id)` on parents and a
  three-column FK + `ON DELETE CASCADE` on children. No separate migration — the data is
  testing-only, dev recreates the DB on apply.

### Data flows

**Publish (adapter-side):**

1. db-sync / runtime-sync / heartbeat calls `producer.send(topic, key, message)`.
2. The wrapper builds `ProducerRecord(topic, partition=null, key, value)` and adds
   `Server-Id` to `record.headers()`.
3. KafkaProducer serializes the value through `GsonSerializer` and forms the record batch
   (headers fall under the shared batch compression).
4. The broker accepts; per-key partitioning keeps the partition through the hash of the
   key = entityId.

**Consume (nx-gameservers):**

1. `@KafkaListener` receives `List<ConsumerRecord<Long, String>>` per poll.
2. For each record: `tenantId = TenantResolver.resolveOrThrow(topic)`,
   `serverId = NxHeaders.decodeUuid(record.headers().lastHeader(NxHeaders.SERVER_ID).value())`.
3. If serverId == null or the length is invalid → WARN + skip.
4. `serverSlug = tenantCache.resolveServer(tenantId, serverId)`; if null after the
   single-flight refresh → WARN + skip.
5. JSON parse through Gson.
6. Group by `(tenantId, serverId)` → per group
   `ingestor.ingestBatch(tenantId, serverId, events)`.
7. After all groups — `ack.acknowledge()`.

### Data model

- **`nx-gameservers.characters`** (R7) — PK `(tenant_id, server_id, id)`; all non-PK columns
  nullable as today.
- **`nx-gameservers.character_subclasses`** (R7) — PK
  `(tenant_id, server_id, char_id, class)`; FK
  `(tenant_id, server_id, char_id) → characters(tenant_id, server_id, id) ON DELETE CASCADE`.
- **`nx-gameservers.clans`** (R7) — PK `(tenant_id, server_id, id)`.
- **`nx-gameservers.clan_skills`** (R7) — PK + three-column FK on the same pattern.
- **`nx-gameservers.items`** (R7) — PK `(tenant_id, server_id, id)`.
- **`nx-gameservers.item_attributes`** (R7) — PK + three-column FK.

`nx-tenants.game_servers` (existing table) — unchanged; read by a JOIN from
`InternalTenantsController` filtered by `active = true`.

### Integration points

- **`nx-gs-adapter` ↔ `nx-tenants`** — unchanged: the handshake stays
  `/api/tenants/servers/connect`, `ConnectContext.serverId` already carries the UUID.
- **`nx-gs-adapter` (publisher) ↔ Kafka** (R3, R4) — every publish goes through the
  server-stamping wrapper; key / value serdes do not change.
- **`nx-gameservers` ↔ Kafka** (R5, R6) — header extraction before the JSON parse;
  topic-pattern-based discovery of new tenants through the Kafka metadata refresh does not
  change.
- **`nx-gameservers` ↔ `nx-tenants` `/internal/tenants`** (R9) — extended payload, plain HTTP
  behind an nginx ACL (no shared secrets, as today).

### Decisions

- **Decision:** identity travels in the Kafka header `Server-Id`, not in the key and not in
  the payload.
  **Why:** the key is currently `entityId` (`Long`) and gives per-entity FIFO through
  partition affinity. Putting `serverId` into the key (composite or instead) changes
  partitioning for no gain: per-server ordering already comes from single-producer FIFO
  regardless of the partition, while the consistent hash for the same entityId across
  servers breaks. Putting it into the payload means a `SyncEvent<T>` schema bump and touching
  the contract of every entity DTO; a header is purely the transport layer, the
  `nx-gs-adapter-api` contract does not move.
- **Decision:** raw 16-byte UUID, not a string.
  **Why:** 16 bytes vs 36 is a factor of 2.25 on every record. Headers fall under record-batch
  compression, so the absolute saving after gzip / zstd is not dramatic, but raw bytes are
  cheaper both to encode (no string formatting) and to compare (memcmp). The reader / writer
  is trivially small.
- **Decision:** a single header (`Server-Id`), no tenant or server slug headers.
  **Why:** `tenantSlug` is already in the topic name — the single source of truth, a copy
  risks drift; `tenantId` is derivable from the slug through `TenantCache`; `serverSlug` is
  derivable from `serverId` through the extended cache. Headers are worth it when a) the data
  cannot be recovered from the topic / payload, b) the consumer wants a skip path before the
  JSON parse. Only `serverId` meets both criteria.
- **Decision:** header name + encoding in `nx-gs-adapter-api.NxHeaders` (the contract), the
  Kafka-binding wrapper in `nx-gs-kafka`.
  **Why:** both sides (`nx-gs-adapter`, `nx-gameservers`) already depend on
  `nx-gs-adapter-api` through the composite build — the single source of truth for the header
  name and the encoding lives there, with no string duplication and no encoding drift between
  the sides. The pure-JDK helpers (`UUID` + `ByteBuffer`) do not break the zero-runtime-deps
  rule of `nx-gs-adapter-api`. `nx-gs-kafka` stays the adapter's client-side facade — a thin
  wrapper over `org.apache.kafka.common.header.Headers`, not a contract artifact. Future Kafka
  headers (reply-flow correlation, observability) go into the same `NxHeaders` as constants.
- **Decision:** Kafka `NxHeaders` ≠ HTTP `NxHeaders` from `nx-libs/common`.
  **Why:** different transports, different conventions (HTTP — case-insensitive ASCII
  strings, Kafka — bytes-keyed), different packages (`app.l2nx.gs.adapter.api.kafka.NxHeaders`
  vs `app.l2nx.common.http.NxHeaders` or similar) — no collision. Putting everything into one
  class is coupling for no gain, because the serdes differ too (HTTP value = String, Kafka
  value = byte[]).
- **Decision:** the consumer warns and skips on a missing / unknown `Server-Id`, no fail-fast.
  **Why:** symmetric with the unknown-tenant handling — one bad record does not poison the
  rest of the poll. It also eases the adapter rollout in a heterogeneous environment (although
  in this feature the data is test-only, no real backwards-compat concern).
- **Decision:** edit `v1.0.0_baseline.sql` in place, not a separate migration.
  **Why:** the data is testing-only, no real environment has the old changeset checksum
  applied; dev recreates the DB on apply. Cleaner: one baseline without a lingering
  `v1.1.0_per_server_pk` shim that would otherwise stay in the repo forever. The platform rule
  forward-only / no `--rollback` still holds — it is about the operations allowed in new
  migrations, not a ban on editing an unreleased baseline in place.
- **Decision:** the `/internal/tenants` contract changes in place, not through a versioned
  endpoint.
  **Why:** the only consumer is `nx-gameservers` in the same monorepo under a coordinated
  release. A versioned endpoint (`/internal/v2/tenants` or `/internal/tenants/with-servers`)
  would add a code path we would remove right away.

### Extension points

- **Additional Kafka headers** — added as constants to `nx-gs-adapter-api.NxHeaders` (name +
  encoding if custom); Kafka binding, if needed, goes into the helpers of the `nx-gs-kafka`
  producer wrapper (it can be extended with another connection-scoped header, e.g.
  `traceparent` for cross-system tracing, or a per-call header for the reply flow). The sync
  modules are not touched.
- **Per-server scoped subscriptions** — a future consumer can filter the events of one server
  of a tenant on the application side through the same `Server-Id` header (e.g. a separate
  observability pipeline for one "canary" server); the filter goes into `SyncEventConsumer`
  pre-parse without wire-schema changes.
- **Non-sync channels** — any future Kafka channel of the adapter (metrics, audit,
  command replies) gets `Server-Id` automatically through the same wrapper if it uses the base
  `NxProducer` through core.
