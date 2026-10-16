package app.l2nx.gs.adapter.api.spi;

import app.l2nx.gs.adapter.api.spi.capability.NxCommandReceipts;
import app.l2nx.gs.adapter.api.spi.model.receipt.ClaimOutcome;
import app.l2nx.gs.adapter.api.spi.model.receipt.CompletedReceipt;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptClaim;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptRow;
import app.l2nx.gs.adapter.api.spi.model.receipt.ReceiptStatus;
import java.sql.Connection;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

/** Fail-fast fallback: receipts guard money, so a context wired without the store refuses every call. */
final class NoOpCommandReceipts implements NxCommandReceipts {

    static final NoOpCommandReceipts INSTANCE = new NoOpCommandReceipts();

    private NoOpCommandReceipts() {}

    private static IllegalStateException unwired() {
        return new IllegalStateException("command receipts are not wired in this context; use the adapter runtime's"
                + " NxAdapter.commandReceipts()");
    }

    @Override
    public ClaimOutcome claim(String key, String command, ReceiptClaim claim) {
        throw unwired();
    }

    @Override
    public ClaimOutcome lookup(String key) {
        throw unwired();
    }

    @Override
    public void markCharged(Connection con, String key, long chargedAdena) {
        throw unwired();
    }

    @Override
    public boolean markChargedOwnTx(String key, long chargedAdena) {
        throw unwired();
    }

    @Override
    public boolean markChargedOwnTx(String key, long chargedAdena, String claimJson) {
        throw unwired();
    }

    @Override
    public boolean markRefundedOwnTx(String key, String claimJson) {
        throw unwired();
    }

    @Override
    public boolean amendOpenClaimOwnTx(String key, String expectedClaimJson, String claimJson) {
        throw unwired();
    }

    @Override
    public boolean amendCompletedClaimOwnTx(String key, String expectedClaimJson, String claimJson) {
        throw unwired();
    }

    @Override
    public boolean markStepsDoneOwnTx(String key) {
        throw unwired();
    }

    @Override
    public boolean markOk(Connection con, String key, long chargedAdena, String resultJson) {
        throw unwired();
    }

    @Override
    public boolean markOkOwnTx(String key, long chargedAdena, String resultJson) {
        throw unwired();
    }

    @Override
    public boolean markOkIfStarted(Connection con, String key, String resultJson) {
        throw unwired();
    }

    @Override
    public void markOkIfOpen(Connection con, String key, long chargedAdena, String resultJson) {
        throw unwired();
    }

    @Override
    public void markOkIfOpen(Connection con, String key, long chargedAdena, String resultJson, String claimJson) {
        throw unwired();
    }

    @Override
    public void markFailedInTx(Connection con, String key) {
        throw unwired();
    }

    @Override
    public boolean markFailed(Connection con, String key) {
        throw unwired();
    }

    @Override
    public boolean markFailedOwnTx(String key) {
        throw unwired();
    }

    @Override
    public boolean failStale(String key) {
        throw unwired();
    }

    @Override
    public void insertCompleted(String key, String command, int mailId, String claimJson) {
        throw unwired();
    }

    @Override
    public int pruneOld(Collection<String> stepTrackedCommands) {
        throw unwired();
    }

    @Override
    public ReceiptStatus status(String key) {
        throw unwired();
    }

    @Override
    public String claimJson(String key) {
        throw unwired();
    }

    @Override
    public List<String> nonTerminalCommands() {
        throw unwired();
    }

    @Override
    public List<ReceiptRow> nonTerminal(String command) {
        throw unwired();
    }

    @Override
    public List<ReceiptRow> startedOlderThan(String command, Duration age) {
        throw unwired();
    }

    @Override
    public List<CompletedReceipt> okWithOpenSteps(String command) {
        throw unwired();
    }
}
