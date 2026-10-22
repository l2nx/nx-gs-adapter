package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.WantedLine;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Buy store ("wanted" list): the owner buys the templates of {@link #getLines()} from any seller. Adena is not reserved
 * at start; the host checks it at start and on every fill. A repeated {@code itemTemplateId} is
 * {@code DUPLICATE_ITEM_TEMPLATE_ID}; a total of {@code count * priceAdena} plus fee beyond the host's adena limit is
 * {@code ADENA_OVERFLOW}.
 */
public final class StartPrivateStoreBuyCommand extends StartPrivateStoreCommand {

    private final List<WantedLine> lines;
    private final @Nullable Integer buyerFeePercent;

    public StartPrivateStoreBuyCommand(
            int charId,
            @Nullable String title,
            List<WantedLine> lines,
            @Nullable Integer buyerFeePercent,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline,
            UUID idempotencyKey,
            @Nullable String initiatorIp) {
        super(charId, title, offlineAllowed, ownerVerified, deadline, idempotencyKey, initiatorIp);
        if (buyerFeePercent != null && buyerFeePercent < 0) {
            throw new IllegalArgumentException("buyerFeePercent must not be negative (got " + buyerFeePercent + ")");
        }
        this.lines = PrivateStoreLists.requireDistinctWantedLines(lines);
        this.buyerFeePercent = buyerFeePercent;
    }

    public List<WantedLine> getLines() {
        return lines;
    }

    /**
     * Whole percent the owner pays on top of every fill, rounded up and burned; the seller gets the listed price.
     * {@code null} or {@code 0} = no fee; negative is {@code VALIDATION_FAILED}.
     */
    public @Nullable Integer getBuyerFeePercent() {
        return buyerFeePercent;
    }

    public Builder toBuilder() {
        return new Builder()
                .charId(getCharId())
                .title(getTitle())
                .lines(getLines())
                .buyerFeePercent(getBuyerFeePercent())
                .offlineAllowed(getOfflineAllowed())
                .ownerVerified(isOwnerVerified())
                .deadline(getDeadline())
                .idempotencyKey(getIdempotencyKey())
                .initiatorIp(getInitiatorIp());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    String storeFieldsToString() {
        return "lines=" + lines + ", buyerFeePercent=" + buyerFeePercent;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        StartPrivateStoreBuyCommand that = (StartPrivateStoreBuyCommand) o;
        return Objects.equals(lines, that.lines) && Objects.equals(buyerFeePercent, that.buyerFeePercent);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hash(lines, buyerFeePercent);
    }

    public static final class Builder {
        private int charId;
        private @Nullable String title;
        private @Nullable List<WantedLine> lines;
        private @Nullable Integer buyerFeePercent;
        private @Nullable Boolean offlineAllowed;
        private boolean ownerVerified;
        private @Nullable Instant deadline;
        private @Nullable UUID idempotencyKey;
        private @Nullable String initiatorIp;

        public Builder charId(int charId) {
            this.charId = charId;
            return this;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder lines(List<WantedLine> lines) {
            this.lines = lines;
            return this;
        }

        public Builder buyerFeePercent(@Nullable Integer buyerFeePercent) {
            this.buyerFeePercent = buyerFeePercent;
            return this;
        }

        public Builder offlineAllowed(@Nullable Boolean offlineAllowed) {
            this.offlineAllowed = offlineAllowed;
            return this;
        }

        public Builder ownerVerified(boolean ownerVerified) {
            this.ownerVerified = ownerVerified;
            return this;
        }

        public Builder deadline(@Nullable Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder initiatorIp(@Nullable String initiatorIp) {
            this.initiatorIp = initiatorIp;
            return this;
        }

        public StartPrivateStoreBuyCommand build() {
            return new StartPrivateStoreBuyCommand(
                    charId,
                    title,
                    lines,
                    buyerFeePercent,
                    offlineAllowed,
                    ownerVerified,
                    deadline,
                    idempotencyKey,
                    initiatorIp);
        }
    }
}
