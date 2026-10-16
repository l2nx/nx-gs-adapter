package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.spi.model.OfflineSession;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.core.jdbc.JdbcValues;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Each call hands the connection back with the read-only and auto-commit modes it was lent with: the pool is the host's.
 * {@code uuid} columns are native on PostgreSQL (bound as object), text on MariaDB/MySQL (bound as string);
 * {@code datetime} holds UTC via an explicit calendar so JVM and session zones never leak in.
 */
final class JdbcOfflineSessionStore implements OfflineSessionStore {

    private static final NxLog log = NxLogFactory.getLogger(JdbcOfflineSessionStore.class);

    static final String TABLE = "l2nx_offline_sessions";

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";
    private static final int MYSQL_DUPLICATE_KEY = 1062;

    private static final String COLUMNS =
            "id, idempotency_key, character_id, master_account_id, activity, account_name, hwid, initiator_ip,"
                    + " spot_pool, spot_id, origin_x, origin_y, origin_z, origin_heading, started_at, planned_ms,"
                    + " active_ms, type_state, ended_at, end_reason";

    private final JdbcConnectionSource source;
    private volatile @Nullable Boolean nativeUuid;

    JdbcOfflineSessionStore(JdbcConnectionSource source) {
        this.source = source;
    }

    @Override
    public List<OfflineSession> loadOpen() {
        return call("load open sessions", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE ended_at IS NULL ORDER BY started_at, id")) {
                return readAll(ps);
            }
        });
    }

    @Override
    public int deleteClosedBefore(Instant cutoff) {
        return call("delete closed sessions", c -> {
            try (PreparedStatement ps =
                    c.prepareStatement("DELETE FROM " + TABLE + " WHERE ended_at IS NOT NULL AND ended_at < ?")) {
                JdbcValues.setInstant(ps, 1, cutoff);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<OfflineSession> findByIdempotencyKey(UUID idempotencyKey) {
        return call("find session by idempotency key", c -> findByIdempotencyKey(c, idempotencyKey));
    }

    @Override
    public Optional<OfflineSession> findOpenByCharacterId(int characterId) {
        return call("find open session by character", c -> {
            try (PreparedStatement ps =
                    c.prepareStatement("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE active_character_id = ?")) {
                ps.setInt(1, characterId);
                List<OfflineSession> rows = readAll(ps);
                return rows.isEmpty() ? Optional.<OfflineSession>empty() : Optional.of(rows.get(0));
            }
        });
    }

    @Override
    public InsertResult insert(OfflineSession s) {
        try {
            return call("insert session", c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE
                        + " (id, idempotency_key, character_id, active_character_id, master_account_id, activity,"
                        + " account_name, hwid, initiator_ip, spot_pool, spot_id, active_spot_key, origin_x, origin_y,"
                        + " origin_z, origin_heading, started_at, planned_ms, active_ms, type_state) VALUES (?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    setUuid(ps, 1, s.getId());
                    setUuid(ps, 2, s.getIdempotencyKey());
                    ps.setInt(3, s.getCharacterId());
                    ps.setInt(4, s.getCharacterId());
                    setUuid(ps, 5, s.getMasterAccountId());
                    ps.setString(6, s.getActivity().name());
                    ps.setString(7, s.getAccountName());
                    JdbcValues.setNullableString(ps, 8, s.getHwid());
                    JdbcValues.setNullableString(ps, 9, s.getInitiatorIp());
                    ps.setString(10, s.getSpotPool());
                    ps.setInt(11, s.getSpotId());
                    ps.setString(12, s.spotKey());
                    ps.setInt(13, s.getOriginX());
                    ps.setInt(14, s.getOriginY());
                    ps.setInt(15, s.getOriginZ());
                    ps.setInt(16, s.getOriginHeading());
                    JdbcValues.setInstant(ps, 17, s.getStartedAt());
                    JdbcValues.setNullableLong(ps, 18, s.getPlannedMs());
                    ps.setLong(19, s.getActiveMs());
                    JdbcValues.setNullableString(ps, 20, s.getTypeState());
                    ps.executeUpdate();
                    return InsertResult.INSERTED;
                }
            });
        } catch (UniqueViolation conflict) {
            // the failed statement poisoned its transaction (PostgreSQL), so classify in a fresh one
            return call("classify insert conflict", c -> classifyConflict(c, s));
        }
    }

    @Override
    public void delete(UUID id) {
        run("delete session", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE id = ?")) {
                setUuid(ps, 1, id);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public PlaceResult place(UUID id, String spotPool, int spotId, @Nullable Instant startedAt) {
        try {
            return call("place session", c -> {
                String sql = "UPDATE " + TABLE + " SET spot_id = ?, active_spot_key = ?"
                        + (startedAt != null ? ", started_at = ?" : "")
                        + " WHERE id = ? AND ended_at IS NULL";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int i = 1;
                    ps.setInt(i++, spotId);
                    ps.setString(i++, spotPool + ":" + spotId);
                    if (startedAt != null) {
                        JdbcValues.setInstant(ps, i++, startedAt);
                    }
                    setUuid(ps, i, id);
                    return ps.executeUpdate() > 0 ? PlaceResult.PLACED : PlaceResult.NOT_OPEN;
                }
            });
        } catch (UniqueViolation conflict) {
            return PlaceResult.SPOT_TAKEN;
        }
    }

    @Override
    public void flushActiveMs(Map<UUID, Long> activeMsById) {
        if (activeMsById.isEmpty()) {
            return;
        }
        run("flush active_ms", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + TABLE + " SET active_ms = ? WHERE id = ? AND ended_at IS NULL AND active_ms <= ?")) {
                for (Map.Entry<UUID, Long> e : activeMsById.entrySet()) {
                    ps.setLong(1, e.getValue());
                    setUuid(ps, 2, e.getKey());
                    ps.setLong(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    @Override
    public void close(UUID id, long activeMs, Instant endedAt, LogoutReason reason) {
        run("close session", c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + TABLE
                    + " SET active_ms = ?, ended_at = ?, end_reason = ?, active_character_id = NULL, active_spot_key ="
                    + " NULL WHERE id = ? AND ended_at IS NULL")) {
                ps.setLong(1, activeMs);
                JdbcValues.setInstant(ps, 2, endedAt);
                ps.setString(3, reason.name());
                setUuid(ps, 4, id);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public boolean updateTypeState(UUID id, @Nullable String typeState) {
        return call("update type_state", c -> {
            try (PreparedStatement ps =
                    c.prepareStatement("UPDATE " + TABLE + " SET type_state = ? WHERE id = ? AND ended_at IS NULL")) {
                JdbcValues.setNullableString(ps, 1, typeState);
                setUuid(ps, 2, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private InsertResult classifyConflict(Connection c, OfflineSession s) throws SQLException {
        if (findByIdempotencyKey(c, s.getIdempotencyKey()).isPresent()) {
            return InsertResult.DUPLICATE_IDEMPOTENCY_KEY;
        }
        if (exists(c, "active_character_id = ?", ps -> ps.setInt(1, s.getCharacterId()))) {
            return InsertResult.CHARACTER_ACTIVE;
        }
        // the spot is the only unique left; if its holder closed meanwhile the caller retries
        return InsertResult.SPOT_TAKEN;
    }

    private Optional<OfflineSession> findByIdempotencyKey(Connection c, UUID key) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE idempotency_key = ?")) {
            setUuid(ps, 1, key);
            List<OfflineSession> rows = readAll(ps);
            return rows.isEmpty() ? Optional.<OfflineSession>empty() : Optional.of(rows.get(0));
        }
    }

    private boolean exists(Connection c, String where, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + TABLE + " WHERE " + where)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<OfflineSession> readAll(PreparedStatement ps) throws SQLException {
        List<OfflineSession> result = new ArrayList<OfflineSession>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OfflineSession row = read(rs);
                if (row != null) {
                    result.add(row);
                }
            }
        }
        return result;
    }

    private static @Nullable OfflineSession read(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        ActivityType activity = parse(ActivityType.class, rs.getString("activity"));
        String endReasonRaw = rs.getString("end_reason");
        LogoutReason endReason = endReasonRaw == null ? null : parse(LogoutReason.class, endReasonRaw);
        Instant endedAt = JdbcValues.getInstant(rs, "ended_at");
        if (activity == null || (endedAt != null && endReason == null)) {
            // written by a newer adapter (rollback after an upgrade); skipping keeps the rest usable
            log.error(
                    "Offline session {} skipped: activity '{}' / end_reason '{}' unknown to this adapter version",
                    id,
                    rs.getString("activity"),
                    endReasonRaw);
            return null;
        }
        String master = rs.getString("master_account_id");
        return OfflineSession.builder()
                .id(UUID.fromString(id))
                .idempotencyKey(UUID.fromString(rs.getString("idempotency_key")))
                .characterId(rs.getInt("character_id"))
                .masterAccountId(master == null ? null : UUID.fromString(master))
                .activity(activity)
                .accountName(rs.getString("account_name"))
                .hwid(rs.getString("hwid"))
                .initiatorIp(rs.getString("initiator_ip"))
                .spotPool(rs.getString("spot_pool"))
                .spotId(rs.getInt("spot_id"))
                .originX(rs.getInt("origin_x"))
                .originY(rs.getInt("origin_y"))
                .originZ(rs.getInt("origin_z"))
                .originHeading(rs.getInt("origin_heading"))
                .startedAt(JdbcValues.getInstant(rs, "started_at"))
                .plannedMs(JdbcValues.getNullableLong(rs, "planned_ms"))
                .activeMs(rs.getLong("active_ms"))
                .typeState(rs.getString("type_state"))
                .endedAt(endedAt)
                .endReason(endedAt == null ? null : endReason)
                .build();
    }

    private static <E extends Enum<E>> @Nullable E parse(Class<E> type, @Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private void setUuid(PreparedStatement ps, int index, @Nullable UUID value) throws SQLException {
        boolean pg = isNativeUuid(ps.getConnection());
        if (value == null) {
            ps.setNull(index, pg ? Types.OTHER : Types.VARCHAR);
        } else if (pg) {
            ps.setObject(index, value);
        } else {
            ps.setString(index, value.toString());
        }
    }

    private boolean isNativeUuid(Connection c) throws SQLException {
        Boolean known = nativeUuid;
        if (known == null) {
            String product = c.getMetaData().getDatabaseProductName();
            known = product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
            nativeUuid = known;
        }
        return known;
    }

    private void run(String operation, SqlVoid work) {
        call(operation, c -> {
            work.run(c);
            return Boolean.TRUE;
        });
    }

    private <T> T call(String operation, SqlWork<T> work) {
        Connection c;
        try {
            c = source.getConnection();
        } catch (SQLException e) {
            throw new OfflineSessionStoreException(
                    "Offline sessions: " + operation + " failed to borrow a connection" + " from '" + source.name()
                            + "': " + e.getMessage(),
                    e);
        }
        try {
            boolean lentReadOnly = c.isReadOnly();
            boolean lentAutoCommit = c.getAutoCommit();
            try {
                if (lentReadOnly) {
                    c.setReadOnly(false);
                }
                if (lentAutoCommit) {
                    c.setAutoCommit(false);
                }
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly(c);
                if (isUniqueViolation(e)) {
                    throw new UniqueViolation(e);
                }
                throw new OfflineSessionStoreException(
                        "Offline sessions: " + operation + " failed: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            } finally {
                if (lentAutoCommit) {
                    c.setAutoCommit(true);
                }
                if (lentReadOnly) {
                    c.setReadOnly(true);
                }
            }
        } catch (SQLException e) {
            throw new OfflineSessionStoreException(
                    "Offline sessions: " + operation + " failed to prepare the connection: " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                log.warn("Offline sessions: returning the connection after '{}' failed: {}", operation, e.getMessage());
            }
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException e) {
            log.warn("Offline sessions: rollback failed: {}", e.getMessage());
        }
    }

    /** PostgreSQL: SQLState 23505; MySQL and MariaDB: vendor error 1062 under the shared SQLState 23000. */
    static boolean isUniqueViolation(SQLException e) {
        for (SQLException next = e; next != null; next = next.getNextException()) {
            if (POSTGRES_UNIQUE_VIOLATION.equals(next.getSQLState()) || next.getErrorCode() == MYSQL_DUPLICATE_KEY) {
                return true;
            }
        }
        return false;
    }

    private interface SqlWork<T> {
        T apply(Connection c) throws SQLException;
    }

    private interface SqlVoid {
        void run(Connection c) throws SQLException;
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private static final class UniqueViolation extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UniqueViolation(SQLException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
