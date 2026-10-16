package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.spi.model.receipt.ClaimOutcome;
import app.l2nx.gs.adapter.api.spi.model.receipt.CompletedReceipt;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptRow;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Claim-first exactly-once ledger of money-moving commands, stored in the adapter-owned receipts table: the claim
 * commits before any money or goods move, and when in doubt a command is refused. The key is the command's dedup key
 * (a transport id or an idempotency key). Every status transition is a compare-and-set on the current status.
 *
 * <p>Three kinds of operation:</p>
 * <ul>
 *   <li><b>Caller's transaction</b> (take a {@link Connection}): the SQL runs on the host's connection so the receipt
 *       write commits or rolls back with the host's own game-database writes. The store never commits, rolls back,
 *       closes or reconfigures that connection. A thrown {@link SQLException} is the host's signal to roll back.</li>
 *   <li><b>{@code ...OwnTx}</b>: one write in a transaction of its own on a connection borrowed from the host's
 *       {@code JdbcConnectionSource}; a database failure is logged and reported as {@code false} (a miss).</li>
 *   <li><b>Everything else</b> also borrows its own connection, but throws {@link SQLException} on failure.</li>
 * </ul>
 *
 * <p>Every call needs the adapter schema migrated in this JVM ({@code NxAdapter.migrateSchema()} or the adapter's own
 * start); before that, or after a failed migration, every call throws {@link IllegalStateException}, the caller-
 * transaction ones included, so no money-relevant write is ever silently dropped.</p>
 *
 * <p>Times are taken from the JVM clock and stored as UTC; ages ({@link #startedOlderThan}, {@link #pruneOld}) are
 * measured against the same clock.</p>
 */
public interface NxCommandReceipts {

    /** Age past which {@link #pruneOld} deletes a terminal receipt. */
    Duration RETENTION = Duration.ofDays(30);

    /**
     * Claims {@code key} for {@code command}. A new key is inserted as {@link ReceiptStatus#STARTED} and answers
     * {@link ClaimOutcome.Kind#CLAIMED}. An existing {@code OK} receipt answers {@code REPLAY_OK} with its stored
     * result; an existing open one {@code IN_FLIGHT}. A {@code FAILED} receipt is reopened as {@code STARTED} (charge,
     * result and completion cleared, creation time reset, the claim's {@code mailId} and {@code claimJson} stored; the
     * buyer and seller stay as first claimed); losing that reopen race answers {@code IN_FLIGHT}, as does a duplicate
     * key whose row cannot be read back.
     */
    ClaimOutcome claim(String key, String command, ReceiptClaim claim) throws SQLException;

    /**
     * Non-authoritative peek: a contender can claim right after it, only {@link #claim} decides. {@code null} when there
     * is no receipt or it is {@code FAILED} (the command may run); otherwise as {@link #claim} answers an existing row.
     */
    @Nullable
    ClaimOutcome lookup(String key) throws SQLException;

    /**
     * Unconditionally moves the receipt to {@code CHARGED} with {@code chargedAdena}, in the caller's transaction. Run
     * it in the offline debit's transaction or BEFORE an online debit: a false "charged" is operator noise, a false
     * "not charged" risks a double debit.
     *
     * @throws SQLException also when no receipt exists for {@code key}
     */
    void markCharged(Connection con, String key, long chargedAdena) throws SQLException;

    /** {@link #markCharged} in its own transaction; {@code false} on a missing row or a database failure. */
    boolean markChargedOwnTx(String key, long chargedAdena);

    /** {@code STARTED} to {@code CHARGED}, storing {@code chargedAdena} and replacing the claim JSON; own transaction. */
    boolean markChargedOwnTx(String key, long chargedAdena, String claimJson);

    /** Records a refund: {@code CHARGED} back to {@code STARTED} with {@code chargedAdena=0} and the new claim JSON. */
    boolean markRefundedOwnTx(String key, String claimJson);

    /** Replaces the claim JSON of an open receipt only while it still equals {@code expectedClaimJson}. */
    boolean amendOpenClaimOwnTx(String key, String expectedClaimJson, String claimJson);

    /** Replaces the claim JSON of an {@code OK} receipt only while it still equals {@code expectedClaimJson}. */
    boolean amendCompletedClaimOwnTx(String key, String expectedClaimJson, String claimJson);

    /** Marks the follow-up steps of an {@code OK} receipt done, once; {@code false} when already marked or not OK. */
    boolean markStepsDoneOwnTx(String key);

    /**
     * Unconditionally settles as {@code OK} in the caller's transaction; {@code true} also when no row matched. A
     * database failure is logged and answers {@code false} (replays are then refused until prune); on PostgreSQL it also
     * aborts the caller's transaction, so a caller that must keep its own writes passes a connection of its own.
     */
    boolean markOk(Connection con, String key, long chargedAdena, String resultJson);

    /** {@link #markOk} in its own transaction. */
    boolean markOkOwnTx(String key, long chargedAdena, String resultJson);

    /** {@code STARTED} to {@code OK} with the result, in the caller's transaction; {@code false} on a miss. */
    boolean markOkIfStarted(Connection con, String key, String resultJson) throws SQLException;

    /**
     * Open to {@code OK} with the charge and result, in the caller's transaction.
     *
     * @throws SQLException also when the receipt is missing or already settled, so the caller rolls back
     */
    void markOkIfOpen(Connection con, String key, long chargedAdena, String resultJson) throws SQLException;

    /** {@link #markOkIfOpen(Connection, String, long, String)} that also replaces the claim JSON. */
    void markOkIfOpen(Connection con, String key, long chargedAdena, String resultJson, String claimJson)
            throws SQLException;

    /**
     * Open to {@code FAILED} in the caller's transaction, clearing the mail id and claim JSON so a retry may claim the
     * key again.
     *
     * @throws SQLException also when the receipt is missing or already settled
     */
    void markFailedInTx(Connection con, String key) throws SQLException;

    /**
     * {@link #markFailedInTx} that logs a database failure, the miss included, and answers {@code false} instead of
     * throwing; only the readiness gate's {@link IllegalStateException} escapes.
     */
    boolean markFailed(Connection con, String key);

    /** {@link #markFailed} in its own transaction. */
    boolean markFailedOwnTx(String key);

    /** {@code STARTED} to {@code FAILED} (as {@link #markFailedInTx} clears it); {@code false} on a miss. */
    boolean failStale(String key) throws SQLException;

    /**
     * Inserts an already completed ({@code OK}) receipt for work that happened outside a claim.
     *
     * @throws SQLException also when the key already exists
     */
    void insertCompleted(String key, String command, int mailId, String claimJson) throws SQLException;

    /**
     * Deletes {@code OK} and {@code FAILED} receipts created more than {@link #RETENTION} ago and answers how many.
     * Open receipts are never pruned: a retry would re-run over an unresolved mutation. An {@code OK} receipt of a
     * command in {@code stepTrackedCommands} stays while its steps are open: it may be the only record of a debt.
     */
    int pruneOld(Collection<String> stepTrackedCommands) throws SQLException;

    @Nullable
    ReceiptStatus status(String key) throws SQLException;

    @Nullable
    String claimJson(String key) throws SQLException;

    /** Distinct commands that have at least one open receipt. */
    List<String> nonTerminalCommands() throws SQLException;

    /** Open receipts of {@code command}, oldest first. */
    List<ReceiptRow> nonTerminal(String command) throws SQLException;

    /** {@code STARTED} receipts of {@code command} created more than {@code age} ago, oldest first. */
    List<ReceiptRow> startedOlderThan(String command, Duration age) throws SQLException;

    /** {@code OK} receipts of {@code command} whose steps are not marked done, oldest completion first. */
    List<CompletedReceipt> okWithOpenSteps(String command) throws SQLException;
}
