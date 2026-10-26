package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.PrivateStoreFee;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public abstract class StartPrivateStoreSellingCommand extends StartPrivateStoreCommand {

    private final List<SellLine> lines;
    private final @Nullable Integer sellerFeePercent;

    StartPrivateStoreSellingCommand(
            int charId,
            @Nullable String title,
            List<SellLine> lines,
            @Nullable Integer sellerFeePercent,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline,
            UUID idempotencyKey,
            CommandInitiator initiator) {
        super(charId, title, offlineAllowed, ownerVerified, deadline, idempotencyKey, initiator);
        if (sellerFeePercent != null
                && (sellerFeePercent < 0 || sellerFeePercent > PrivateStoreFee.MAX_SELLER_FEE_PERCENT)) {
            throw new IllegalArgumentException("sellerFeePercent must be within 0.."
                    + PrivateStoreFee.MAX_SELLER_FEE_PERCENT + " (got " + sellerFeePercent + ")");
        }
        this.lines = PrivateStoreLists.requireDistinctLines(lines);
        this.sellerFeePercent = sellerFeePercent;
    }

    public List<SellLine> getLines() {
        return lines;
    }

    /**
     * Whole percent withheld from proceeds and burned on every sale, rounded up; {@code null} or {@code 0} = no fee.
     * The host rejects a negative percent or one above 100 as {@code VALIDATION_FAILED}.
     */
    public @Nullable Integer getSellerFeePercent() {
        return sellerFeePercent;
    }

    @Override
    String storeFieldsToString() {
        return "lines=" + lines + ", sellerFeePercent=" + sellerFeePercent;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        StartPrivateStoreSellingCommand that = (StartPrivateStoreSellingCommand) o;
        return Objects.equals(lines, that.lines) && Objects.equals(sellerFeePercent, that.sellerFeePercent);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hash(lines, sellerFeePercent);
    }
}
