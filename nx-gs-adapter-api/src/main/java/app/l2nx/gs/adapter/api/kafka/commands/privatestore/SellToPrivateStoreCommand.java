package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.PrivateStoreFee;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Remote sale into another character's open buy-store, the mirror of {@link BuyFromPrivateStoreCommand}.
 * All-or-nothing: every line matches a line of the store by item template and exact price at the requested count, or
 * nothing is charged or moved.
 */
public final class SellToPrivateStoreCommand implements OwnerVerified, NxCommand<SellToPrivateStoreResult> {

    private final int sellerCharId;
    private final int buyerCharId;
    private final List<SellLine> lines;
    private final @Nullable Integer sellerFeePercent;
    private final Instant deadline;
    private final String mailSender;
    private final String mailSubject;
    private final String mailBody;
    private final boolean ownerVerified;
    private final CommandInitiator initiator;

    public SellToPrivateStoreCommand(
            int sellerCharId,
            int buyerCharId,
            List<SellLine> lines,
            @Nullable Integer sellerFeePercent,
            Instant deadline,
            String mailSender,
            String mailSubject,
            String mailBody,
            boolean ownerVerified,
            CommandInitiator initiator) {
        if (sellerCharId <= 0) {
            throw new IllegalArgumentException("sellerCharId must be positive (got " + sellerCharId + ")");
        }
        if (buyerCharId <= 0) {
            throw new IllegalArgumentException("buyerCharId must be positive (got " + buyerCharId + ")");
        }
        if (sellerCharId == buyerCharId) {
            throw new IllegalArgumentException("sellerCharId must differ from buyerCharId (got " + sellerCharId + ")");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines is required and must be non-empty");
        }
        for (SellLine line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("lines must not contain null elements");
            }
        }
        Integer duplicateItemId = SellLine.firstDuplicateItemId(lines);
        if (duplicateItemId != null) {
            throw new IllegalArgumentException("lines must not repeat itemId (duplicate " + duplicateItemId + ")");
        }
        if (sellerFeePercent == null) {
            throw new IllegalArgumentException("sellerFeePercent is required");
        }
        if (sellerFeePercent < 0) {
            throw new IllegalArgumentException("sellerFeePercent must not be negative (got " + sellerFeePercent + ")");
        }
        if (sellerFeePercent > PrivateStoreFee.MAX_SELLER_FEE_PERCENT) {
            throw new IllegalArgumentException("sellerFeePercent must not exceed "
                    + PrivateStoreFee.MAX_SELLER_FEE_PERCENT + " (got " + sellerFeePercent + ")");
        }
        this.sellerCharId = sellerCharId;
        this.buyerCharId = buyerCharId;
        this.lines = PrivateStoreLists.freeze(lines);
        this.sellerFeePercent = sellerFeePercent;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.mailSender = requireText(mailSender, "mailSender");
        this.mailSubject = requireText(mailSubject, "mailSubject");
        this.mailBody = requireText(mailBody, "mailBody");
        this.ownerVerified = ownerVerified;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /** Acts from the app; need not be online, in range or in the store owner's instance, the host loads an offline character. */
    public int getSellerCharId() {
        return sellerCharId;
    }

    /** Owner of the buy-store; must be in the world (online or offline-trading) with a buy-store open. */
    public int getBuyerCharId() {
        return buyerCharId;
    }

    /**
     * {@code itemId} is the instance in the seller's inventory, {@code priceAdena} the buy-store line's unit price the
     * seller saw; the item template is taken from the instance. Fields beyond that are an optimistic lock, not a search
     * filter: a mismatch against the live store fails the whole command.
     */
    public List<SellLine> getLines() {
        return lines;
    }

    /**
     * Whole percent withheld from the proceeds and burned, {@code 0} = no fee, at most
     * {@link PrivateStoreFee#MAX_SELLER_FEE_PERCENT}. Missing (Gson bypasses the constructor), negative or above the cap
     * is {@code VALIDATION_FAILED}, never read as {@code 0}.
     */
    public @Nullable Integer getSellerFeePercent() {
        return sellerFeePercent;
    }

    /** Past this the host refuses with {@code COMMAND_EXPIRED}; guards against stale Kafka backlog (retention ~3h). */
    public Instant getDeadline() {
        return deadline;
    }

    /** Final, already-localized text with no placeholders; no locale travels on the wire. */
    public String getMailSender() {
        return mailSender;
    }

    public String getMailSubject() {
        return mailSubject;
    }

    public String getMailBody() {
        return mailBody;
    }

    @Override
    public boolean isOwnerVerified() {
        return ownerVerified;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .sellerCharId(sellerCharId)
                .buyerCharId(buyerCharId)
                .lines(lines)
                .sellerFeePercent(sellerFeePercent)
                .deadline(deadline)
                .mailSender(mailSender)
                .mailSubject(mailSubject)
                .mailBody(mailBody)
                .ownerVerified(ownerVerified)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellToPrivateStoreCommand)) return false;
        SellToPrivateStoreCommand that = (SellToPrivateStoreCommand) o;
        return sellerCharId == that.sellerCharId
                && buyerCharId == that.buyerCharId
                && Objects.equals(sellerFeePercent, that.sellerFeePercent)
                && Objects.equals(lines, that.lines)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(mailSender, that.mailSender)
                && Objects.equals(mailSubject, that.mailSubject)
                && Objects.equals(mailBody, that.mailBody)
                && ownerVerified == that.ownerVerified
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sellerCharId,
                buyerCharId,
                lines,
                sellerFeePercent,
                deadline,
                mailSender,
                mailSubject,
                mailBody,
                ownerVerified,
                initiator);
    }

    @Override
    public String toString() {
        return "SellToPrivateStoreCommand[sellerCharId=" + sellerCharId
                + ", buyerCharId=" + buyerCharId
                + ", lines=" + lines
                + ", sellerFeePercent=" + sellerFeePercent
                + ", deadline=" + deadline
                + ", mailSender=" + mailSender
                + ", mailSubject=" + mailSubject
                + ", mailBody=" + mailBody
                + ", ownerVerified=" + ownerVerified + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private int sellerCharId;
        private int buyerCharId;
        private List<SellLine> lines;
        private @Nullable Integer sellerFeePercent;
        private Instant deadline;
        private String mailSender;
        private String mailSubject;
        private String mailBody;
        private boolean ownerVerified;
        private CommandInitiator initiator;

        public Builder sellerCharId(int sellerCharId) {
            this.sellerCharId = sellerCharId;
            return this;
        }

        public Builder buyerCharId(int buyerCharId) {
            this.buyerCharId = buyerCharId;
            return this;
        }

        public Builder lines(List<SellLine> lines) {
            this.lines = lines;
            return this;
        }

        public Builder sellerFeePercent(@Nullable Integer sellerFeePercent) {
            this.sellerFeePercent = sellerFeePercent;
            return this;
        }

        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder mailSender(String mailSender) {
            this.mailSender = mailSender;
            return this;
        }

        public Builder mailSubject(String mailSubject) {
            this.mailSubject = mailSubject;
            return this;
        }

        public Builder mailBody(String mailBody) {
            this.mailBody = mailBody;
            return this;
        }

        public Builder ownerVerified(boolean ownerVerified) {
            this.ownerVerified = ownerVerified;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public SellToPrivateStoreCommand build() {
            return new SellToPrivateStoreCommand(
                    sellerCharId,
                    buyerCharId,
                    lines,
                    sellerFeePercent,
                    deadline,
                    mailSender,
                    mailSubject,
                    mailBody,
                    ownerVerified,
                    initiator);
        }
    }
}
