package app.l2nx.gs.adapter.core.receipts;

import app.l2nx.gs.adapter.api.spi.capability.NxCommandReceipts;
import app.l2nx.gs.adapter.api.spi.model.receipt.ClaimOutcome;
import app.l2nx.gs.adapter.api.spi.model.receipt.CompletedReceipt;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptRow;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptStatus;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.core.jdbc.JdbcValues;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Ages are cutoffs from the injected clock bound as parameters, so no engine-specific date arithmetic is needed. A
 * borrowed connection goes back with the read-only and auto-commit modes it was lent with: the pool is the host's.
 */
public final class JdbcCommandReceipts implements NxCommandReceipts {

    private static final NxLog log = NxLogFactory.getLogger(JdbcCommandReceipts.class);

    static final String TABLE = "l2nx_command_receipts";

    private static final String STARTED = ReceiptStatus.STARTED.name();
    private static final String CHARGED = ReceiptStatus.CHARGED.name();
    private static final String OK = ReceiptStatus.OK.name();
    private static final String FAILED = ReceiptStatus.FAILED.name();

    private static final String OPEN = "status IN ('" + STARTED + "', '" + CHARGED + "')";
    private static final String ROW_COLUMNS =
            "correlation_id, command, status, buyer_char_id, seller_char_id, charged_adena, mail_id, claim_json";

    private static final String SETTLE_OK = "UPDATE " + TABLE + " SET status='" + OK
            + "', charged_adena=?, result_json=?, completed_at=? WHERE correlation_id=? AND " + OPEN;
    private static final String SETTLE_OK_WITH_CLAIM = "UPDATE " + TABLE + " SET status='" + OK
            + "', charged_adena=?, result_json=?, claim_json=?, completed_at=? WHERE correlation_id=? AND " + OPEN;
    private static final String RELEASE = "status='" + FAILED + "', mail_id=NULL, claim_json=NULL, completed_at=?";

    private final Supplier<JdbcConnectionSource> sources;
    private final Clock clock;

    /**
     * @param sources throws {@link IllegalStateException} while the schema is not current; caller-transaction
     *     operations call it only as their readiness gate
     */
    public JdbcCommandReceipts(Supplier<JdbcConnectionSource> sources, Clock clock) {
        this.sources = sources;
        this.clock = clock;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection con) throws SQLException;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static final class StoredRow {
        final String status;
        final @Nullable String resultJson;

        StoredRow(String status, @Nullable String resultJson) {
            this.status = status;
            this.resultJson = resultJson;
        }
    }

    @Override
    public ClaimOutcome claim(String key, String command, ReceiptClaim claim) throws SQLException {
        JdbcConnectionSource source = sources.get();
        Instant now = clock.instant();
        try {
            inOwnTx(source, con -> {
                try (PreparedStatement ins = con.prepareStatement("INSERT INTO " + TABLE
                        + " (correlation_id, command, buyer_char_id, seller_char_id, status, mail_id, claim_json,"
                        + " created_at) VALUES (?, ?, ?, ?, '" + STARTED + "', ?, ?, ?)")) {
                    ins.setString(1, key);
                    ins.setString(2, command);
                    JdbcValues.setNullableInt(ins, 3, claim.getBuyerCharacterId());
                    JdbcValues.setNullableInt(ins, 4, claim.getSellerCharacterId());
                    JdbcValues.setNullableInt(ins, 5, claim.getMailId());
                    JdbcValues.setNullableString(ins, 6, claim.getClaimJson());
                    JdbcValues.setInstant(ins, 7, now);
                    ins.executeUpdate();
                    return Boolean.TRUE;
                }
            });
            return ClaimOutcome.claimed();
        } catch (SQLException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
        }
        // on PostgreSQL the failed insert aborted its transaction, so the row is read in a fresh one
        return inOwnTx(source, con -> {
            StoredRow existing = readRow(con, key);
            if (existing == null) {
                // duplicate key yet no row: refuse rather than guess
                return ClaimOutcome.inFlight();
            }
            if (!FAILED.equals(existing.status)) {
                return outcomeForExisting(existing.status, existing.resultJson);
            }
            boolean reopened = cas(
                    con,
                    "UPDATE " + TABLE + " SET status='" + STARTED + "', mail_id=?, claim_json=?, charged_adena=NULL,"
                            + " result_json=NULL, completed_at=NULL, created_at=? WHERE correlation_id=? AND status='"
                            + FAILED + "'",
                    ps -> {
                        JdbcValues.setNullableInt(ps, 1, claim.getMailId());
                        JdbcValues.setNullableString(ps, 2, claim.getClaimJson());
                        JdbcValues.setInstant(ps, 3, now);
                        ps.setString(4, key);
                    });
            return reopened ? ClaimOutcome.claimed() : ClaimOutcome.inFlight();
        });
    }

    @Override
    public @Nullable ClaimOutcome lookup(String key) throws SQLException {
        StoredRow existing = inOwnTx(sources.get(), con -> readRow(con, key));
        return existing == null ? null : outcomeForLookup(existing.status, existing.resultJson);
    }

    static @Nullable ClaimOutcome outcomeForLookup(@Nullable String status, @Nullable String resultJson) {
        return FAILED.equals(status) ? null : outcomeForExisting(status, resultJson);
    }

    /** Anything but a readable OK, an unknown stored value included, is refused as in flight. */
    static ClaimOutcome outcomeForExisting(@Nullable String status, @Nullable String resultJson) {
        return OK.equals(status) ? ClaimOutcome.replayOk(resultJson) : ClaimOutcome.inFlight();
    }

    @Override
    public void markCharged(Connection con, String key, long chargedAdena) throws SQLException {
        sources.get();
        casOrThrow(
                con,
                "UPDATE " + TABLE + " SET status='" + CHARGED + "', charged_adena=? WHERE correlation_id=?",
                "receipt row missing for key " + key,
                ps -> {
                    ps.setLong(1, chargedAdena);
                    ps.setString(2, key);
                });
    }

    @Override
    public boolean markChargedOwnTx(String key, long chargedAdena) {
        return ownTx("mark receipt CHARGED", key, con -> {
            markCharged(con, key, chargedAdena);
            return true;
        });
    }

    @Override
    public boolean markChargedOwnTx(String key, long chargedAdena, String claimJson) {
        return ownTx(
                "mark receipt CHARGED",
                key,
                con -> cas(
                        con,
                        "UPDATE " + TABLE + " SET status='" + CHARGED
                                + "', charged_adena=?, claim_json=? WHERE correlation_id=? AND status='" + STARTED
                                + "'",
                        ps -> {
                            ps.setLong(1, chargedAdena);
                            ps.setString(2, claimJson);
                            ps.setString(3, key);
                        }));
    }

    @Override
    public boolean markRefundedOwnTx(String key, String claimJson) {
        return ownTx(
                "record the refund on receipt",
                key,
                con -> cas(
                        con,
                        "UPDATE " + TABLE + " SET status='" + STARTED
                                + "', charged_adena=0, claim_json=? WHERE correlation_id=? AND status='" + CHARGED
                                + "'",
                        ps -> {
                            ps.setString(1, claimJson);
                            ps.setString(2, key);
                        }));
    }

    @Override
    public boolean amendOpenClaimOwnTx(String key, String expectedClaimJson, String claimJson) {
        return ownTx("amend the receipt claim", key, con -> amendClaim(con, OPEN, key, expectedClaimJson, claimJson));
    }

    @Override
    public boolean amendCompletedClaimOwnTx(String key, String expectedClaimJson, String claimJson) {
        return ownTx(
                "amend the completed receipt claim",
                key,
                con -> amendClaim(con, "status='" + OK + "'", key, expectedClaimJson, claimJson));
    }

    @Override
    public boolean markStepsDoneOwnTx(String key) {
        Instant now = clock.instant();
        return ownTx(
                "mark the receipt steps done",
                key,
                con -> cas(
                        con,
                        "UPDATE " + TABLE + " SET steps_done_at=? WHERE correlation_id=? AND status='" + OK
                                + "' AND steps_done_at IS NULL",
                        ps -> {
                            JdbcValues.setInstant(ps, 1, now);
                            ps.setString(2, key);
                        }));
    }

    @Override
    public boolean markOk(Connection con, String key, long chargedAdena, String resultJson) {
        sources.get();
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET status='" + OK
                + "', charged_adena=?, result_json=?, completed_at=? WHERE correlation_id=?")) {
            ps.setLong(1, chargedAdena);
            ps.setString(2, resultJson);
            JdbcValues.setInstant(ps, 3, clock.instant());
            ps.setString(4, key);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.warn("could not mark receipt OK (key={}); replays will be refused as unknown until prune", key, e);
            return false;
        }
    }

    @Override
    public boolean markOkOwnTx(String key, long chargedAdena, String resultJson) {
        return ownTx("mark receipt OK", key, con -> markOk(con, key, chargedAdena, resultJson));
    }

    @Override
    public boolean markOkIfStarted(Connection con, String key, String resultJson) throws SQLException {
        sources.get();
        Instant now = clock.instant();
        return cas(
                con,
                "UPDATE " + TABLE + " SET status='" + OK + "', result_json=?, completed_at=?"
                        + " WHERE correlation_id=? AND status='" + STARTED + "'",
                ps -> {
                    ps.setString(1, resultJson);
                    JdbcValues.setInstant(ps, 2, now);
                    ps.setString(3, key);
                });
    }

    @Override
    public void markOkIfOpen(Connection con, String key, long chargedAdena, String resultJson) throws SQLException {
        sources.get();
        Instant now = clock.instant();
        casOrThrow(con, SETTLE_OK, notOpen(key), ps -> {
            ps.setLong(1, chargedAdena);
            ps.setString(2, resultJson);
            JdbcValues.setInstant(ps, 3, now);
            ps.setString(4, key);
        });
    }

    @Override
    public void markOkIfOpen(Connection con, String key, long chargedAdena, String resultJson, String claimJson)
            throws SQLException {
        sources.get();
        Instant now = clock.instant();
        casOrThrow(con, SETTLE_OK_WITH_CLAIM, notOpen(key), ps -> {
            ps.setLong(1, chargedAdena);
            ps.setString(2, resultJson);
            ps.setString(3, claimJson);
            JdbcValues.setInstant(ps, 4, now);
            ps.setString(5, key);
        });
    }

    @Override
    public void markFailedInTx(Connection con, String key) throws SQLException {
        sources.get();
        Instant now = clock.instant();
        casOrThrow(
                con,
                "UPDATE " + TABLE + " SET " + RELEASE + " WHERE correlation_id=? AND " + OPEN,
                notOpen(key),
                ps -> {
                    JdbcValues.setInstant(ps, 1, now);
                    ps.setString(2, key);
                });
    }

    @Override
    public boolean markFailed(Connection con, String key) {
        try {
            markFailedInTx(con, key);
            return true;
        } catch (SQLException e) {
            log.warn("could not mark receipt FAILED (key={}); retries will be refused as unknown until prune", key, e);
            return false;
        }
    }

    @Override
    public boolean markFailedOwnTx(String key) {
        return ownTx("mark receipt FAILED", key, con -> markFailed(con, key));
    }

    @Override
    public boolean failStale(String key) throws SQLException {
        Instant now = clock.instant();
        return inOwnTx(
                sources.get(),
                con -> cas(
                        con,
                        "UPDATE " + TABLE + " SET " + RELEASE + " WHERE correlation_id=? AND status='" + STARTED + "'",
                        ps -> {
                            JdbcValues.setInstant(ps, 1, now);
                            ps.setString(2, key);
                        }));
    }

    @Override
    public void insertCompleted(String key, String command, int mailId, String claimJson) throws SQLException {
        Instant now = clock.instant();
        inOwnTx(sources.get(), con -> {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO " + TABLE
                    + " (correlation_id, command, status, mail_id, claim_json, created_at, completed_at)"
                    + " VALUES (?, ?, '" + OK + "', ?, ?, ?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, command);
                ps.setInt(3, mailId);
                ps.setString(4, claimJson);
                JdbcValues.setInstant(ps, 5, now);
                JdbcValues.setInstant(ps, 6, now);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public int pruneOld(Collection<String> stepTrackedCommands) throws SQLException {
        List<String> stepTracked = new ArrayList<>(stepTrackedCommands);
        String keepOpenSteps = stepTracked.isEmpty()
                ? ""
                : " AND NOT (status='" + OK + "' AND steps_done_at IS NULL AND command IN ("
                        + String.join(", ", Collections.nCopies(stepTracked.size(), "?")) + "))";
        Instant cutoff = clock.instant().minus(RETENTION);
        int pruned = inOwnTx(sources.get(), con -> {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + TABLE + " WHERE created_at < ? AND"
                    + " status IN ('" + OK + "', '" + FAILED + "')" + keepOpenSteps)) {
                JdbcValues.setInstant(ps, 1, cutoff);
                int index = 2;
                for (String command : stepTracked) {
                    ps.setString(index++, command);
                }
                return ps.executeUpdate();
            }
        });
        if (pruned > 0) {
            log.info("pruned {} command receipt(s) older than {} days", pruned, RETENTION.toDays());
        }
        return pruned;
    }

    @Override
    public @Nullable ReceiptStatus status(String key) throws SQLException {
        String status = single("SELECT status FROM " + TABLE + " WHERE correlation_id=?", key);
        return status == null ? null : statusOf(status);
    }

    @Override
    public @Nullable String claimJson(String key) throws SQLException {
        return single("SELECT claim_json FROM " + TABLE + " WHERE correlation_id=?", key);
    }

    @Override
    public List<String> nonTerminalCommands() throws SQLException {
        return query("SELECT DISTINCT command FROM " + TABLE + " WHERE " + OPEN, ps -> {}, rs -> rs.getString(1));
    }

    @Override
    public List<ReceiptRow> nonTerminal(String command) throws SQLException {
        return query(
                "SELECT " + ROW_COLUMNS + " FROM " + TABLE + " WHERE command=? AND " + OPEN
                        + " ORDER BY created_at, correlation_id",
                ps -> ps.setString(1, command),
                JdbcCommandReceipts::receiptRow);
    }

    @Override
    public List<ReceiptRow> startedOlderThan(String command, Duration age) throws SQLException {
        Instant cutoff = clock.instant().minus(age);
        return query(
                "SELECT " + ROW_COLUMNS + " FROM " + TABLE + " WHERE command=? AND status='" + STARTED
                        + "' AND created_at < ? ORDER BY created_at, correlation_id",
                ps -> {
                    ps.setString(1, command);
                    JdbcValues.setInstant(ps, 2, cutoff);
                },
                JdbcCommandReceipts::receiptRow);
    }

    @Override
    public List<CompletedReceipt> okWithOpenSteps(String command) throws SQLException {
        return query(
                "SELECT correlation_id, claim_json, completed_at FROM " + TABLE + " WHERE command=? AND status='" + OK
                        + "' AND steps_done_at IS NULL ORDER BY completed_at, correlation_id",
                ps -> ps.setString(1, command),
                rs -> {
                    Instant completedAt = JdbcValues.getInstant(rs, "completed_at");
                    return CompletedReceipt.builder()
                            .key(rs.getString(1))
                            .claimJson(rs.getString(2))
                            .completedAt(completedAt != null ? completedAt : Instant.EPOCH)
                            .build();
                });
    }

    private static boolean amendClaim(
            Connection con, String statusGuard, String key, String expectedClaimJson, String claimJson)
            throws SQLException {
        return cas(
                con,
                "UPDATE " + TABLE + " SET claim_json=? WHERE correlation_id=? AND claim_json=? AND " + statusGuard,
                ps -> {
                    ps.setString(1, claimJson);
                    ps.setString(2, key);
                    ps.setString(3, expectedClaimJson);
                });
    }

    private static boolean cas(Connection con, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate() == 1;
        }
    }

    private static void casOrThrow(Connection con, String sql, String missed, Binder binder) throws SQLException {
        if (!cas(con, sql, binder)) {
            throw new SQLException(missed);
        }
    }

    private static String notOpen(String key) {
        return "receipt row missing or already settled for key " + key;
    }

    private boolean ownTx(String action, String key, Work<Boolean> work) {
        JdbcConnectionSource source = sources.get();
        try {
            return inOwnTx(source, work);
        } catch (SQLException e) {
            log.warn("could not {} (key={})", action, key, e);
            return false;
        }
    }

    private @Nullable String single(String sql, String key) throws SQLException {
        List<String> values = query(sql, ps -> ps.setString(1, key), rs -> rs.getString(1));
        return values.isEmpty() ? null : values.get(0);
    }

    private <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) throws SQLException {
        return inOwnTx(sources.get(), con -> {
            List<T> rows = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                binder.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(mapper.map(rs));
                    }
                }
            }
            return rows;
        });
    }

    /**
     * Once the commit has landed, the outcome is decided: failing to restore the lent modes or to return the
     * connection is logged, never reported as a failed write. On a failure those secondary errors ride as suppressed.
     */
    private static <T> T inOwnTx(JdbcConnectionSource source, Work<T> work) throws SQLException {
        Connection con = source.getConnection();
        boolean lentReadOnly = false;
        boolean lentAutoCommit = false;
        T result;
        try {
            lentReadOnly = con.isReadOnly();
            lentAutoCommit = con.getAutoCommit();
            if (lentReadOnly) {
                con.setReadOnly(false);
            }
            if (lentAutoCommit) {
                con.setAutoCommit(false);
            }
            result = work.run(con);
            con.commit();
        } catch (SQLException | RuntimeException e) {
            rollbackQuietly(con, e);
            Exception secondary = restoreAndClose(con, lentReadOnly, lentAutoCommit);
            if (secondary != null) {
                e.addSuppressed(secondary);
            }
            throw e;
        }
        Exception secondary = restoreAndClose(con, lentReadOnly, lentAutoCommit);
        if (secondary != null) {
            log.warn("receipt write committed, but handing the connection back failed: {}", secondary.getMessage());
        }
        return result;
    }

    private static @Nullable Exception restoreAndClose(Connection con, boolean lentReadOnly, boolean lentAutoCommit) {
        Exception failure = null;
        try {
            if (lentAutoCommit) {
                con.setAutoCommit(true);
            }
            if (lentReadOnly) {
                con.setReadOnly(true);
            }
        } catch (SQLException | RuntimeException e) {
            failure = e;
        }
        try {
            con.close();
        } catch (SQLException | RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        return failure;
    }

    private static void rollbackQuietly(Connection con, Exception cause) {
        try {
            con.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }

    /**
     * Any integrity violation counts as the duplicate it nearly always is; the follow-up read refuses when no row is
     * there. MySQL and MariaDB raise the JDBC subclass, PostgreSQL only SQLState class {@code 23}.
     */
    static boolean isIntegrityViolation(SQLException e) {
        for (SQLException next = e; next != null; next = next.getNextException()) {
            String state = next.getSQLState();
            if (next instanceof SQLIntegrityConstraintViolationException || (state != null && state.startsWith("23"))) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable StoredRow readRow(Connection con, String key) throws SQLException {
        try (PreparedStatement ps =
                con.prepareStatement("SELECT status, result_json FROM " + TABLE + " WHERE correlation_id=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new StoredRow(rs.getString(1), rs.getString(2)) : null;
            }
        }
    }

    private static ReceiptRow receiptRow(ResultSet rs) throws SQLException {
        return ReceiptRow.builder()
                .key(rs.getString("correlation_id"))
                .command(rs.getString("command"))
                .status(statusOf(rs.getString("status")))
                .buyerCharacterId(JdbcValues.getNullableInt(rs, "buyer_char_id"))
                .sellerCharacterId(JdbcValues.getNullableInt(rs, "seller_char_id"))
                .chargedAdena(JdbcValues.getNullableLong(rs, "charged_adena"))
                .mailId(JdbcValues.getNullableInt(rs, "mail_id"))
                .claimJson(rs.getString("claim_json"))
                .build();
    }

    /** Only this store writes the column; an unknown value is corruption, failed rather than guessed. */
    private static ReceiptStatus statusOf(String stored) throws SQLException {
        try {
            return ReceiptStatus.valueOf(stored);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SQLException("unknown receipt status '" + stored + "' in " + TABLE, e);
        }
    }
}
