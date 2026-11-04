package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.BuyLine;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Remote "buy now" from another character's open sell-store. All-or-nothing: every line at exactly the requested count
 * and price, or nothing is charged or moved. More lines than the host's mail attachment limit: {@code TOO_MANY_LINES}.
 */
public final class BuyFromPrivateStoreCommand implements OwnerVerified, NxCommand<BuyFromPrivateStoreResult> {

    private final int buyerCharacterId;
    private final int sellerCharacterId;
    private final List<BuyLine> lines;
    private final Integer buyerFeePercent;
    private final Instant deadline;
    private final String mailSender;
    private final String mailSubject;
    private final String mailBody;
    private final boolean ownerVerified;
    private final CommandInitiator initiator;

    public BuyFromPrivateStoreCommand(
            int buyerCharacterId,
            int sellerCharacterId,
            List<BuyLine> lines,
            Integer buyerFeePercent,
            Instant deadline,
            String mailSender,
            String mailSubject,
            String mailBody,
            boolean ownerVerified,
            CommandInitiator initiator) {
        if (buyerCharacterId <= 0) {
            throw new IllegalArgumentException("buyerCharacterId must be positive (got " + buyerCharacterId + ")");
        }
        if (sellerCharacterId <= 0) {
            throw new IllegalArgumentException("sellerCharacterId must be positive (got " + sellerCharacterId + ")");
        }
        if (buyerCharacterId == sellerCharacterId) {
            throw new IllegalArgumentException("buyerCharacterId must differ from sellerCharacterId (got " + buyerCharacterId + ")");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines is required and must be non-empty");
        }
        Set<Long> seenItemIds = new HashSet<>();
        for (BuyLine line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("lines must not contain null elements");
            }
            long itemId = line.getItem().getItemId();
            if (!seenItemIds.add(itemId)) {
                throw new IllegalArgumentException("lines must not repeat itemId (duplicate " + itemId + ")");
            }
        }
        if (buyerFeePercent == null) {
            throw new IllegalArgumentException("buyerFeePercent is required");
        }
        if (buyerFeePercent < 0) {
            throw new IllegalArgumentException("buyerFeePercent must not be negative (got " + buyerFeePercent + ")");
        }
        this.buyerCharacterId = buyerCharacterId;
        this.sellerCharacterId = sellerCharacterId;
        this.lines = PrivateStoreLists.freeze(lines);
        this.buyerFeePercent = buyerFeePercent;
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

    /** Need not be online, in range or in the seller's instance; the host loads an offline character. */
    public int getBuyerCharacterId() {
        return buyerCharacterId;
    }

    /** Must be in the world (online or offline-trading) with a sell-store open. */
    public int getSellerCharacterId() {
        return sellerCharacterId;
    }

    public List<BuyLine> getLines() {
        return lines;
    }

    /**
     * Whole percent burned on top of the lot price, {@code 0} = no fee. Missing (Gson bypasses the constructor) or
     * negative is {@code VALIDATION_FAILED}, never read as {@code 0}.
     */
    public Integer getBuyerFeePercent() {
        return buyerFeePercent;
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
                .buyerCharacterId(buyerCharacterId)
                .sellerCharacterId(sellerCharacterId)
                .lines(lines)
                .buyerFeePercent(buyerFeePercent)
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
        if (!(o instanceof BuyFromPrivateStoreCommand)) return false;
        BuyFromPrivateStoreCommand that = (BuyFromPrivateStoreCommand) o;
        return buyerCharacterId == that.buyerCharacterId
                && sellerCharacterId == that.sellerCharacterId
                && Objects.equals(buyerFeePercent, that.buyerFeePercent)
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
                buyerCharacterId,
                sellerCharacterId,
                lines,
                buyerFeePercent,
                deadline,
                mailSender,
                mailSubject,
                mailBody,
                ownerVerified,
                initiator);
    }

    @Override
    public String toString() {
        return "BuyFromPrivateStoreCommand[buyerCharacterId=" + buyerCharacterId
                + ", sellerCharacterId=" + sellerCharacterId
                + ", lines=" + lines
                + ", buyerFeePercent=" + buyerFeePercent
                + ", deadline=" + deadline
                + ", mailSender=" + mailSender
                + ", mailSubject=" + mailSubject
                + ", mailBody=" + mailBody
                + ", ownerVerified=" + ownerVerified + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private int buyerCharacterId;
        private int sellerCharacterId;
        private List<BuyLine> lines;
        private Integer buyerFeePercent;
        private Instant deadline;
        private String mailSender;
        private String mailSubject;
        private String mailBody;
        private boolean ownerVerified;
        private CommandInitiator initiator;

        public Builder buyerCharacterId(int buyerCharacterId) {
            this.buyerCharacterId = buyerCharacterId;
            return this;
        }

        public Builder sellerCharacterId(int sellerCharacterId) {
            this.sellerCharacterId = sellerCharacterId;
            return this;
        }

        public Builder lines(List<BuyLine> lines) {
            this.lines = lines;
            return this;
        }

        public Builder buyerFeePercent(Integer buyerFeePercent) {
            this.buyerFeePercent = buyerFeePercent;
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

        public BuyFromPrivateStoreCommand build() {
            return new BuyFromPrivateStoreCommand(
                    buyerCharacterId,
                    sellerCharacterId,
                    lines,
                    buyerFeePercent,
                    deadline,
                    mailSender,
                    mailSubject,
                    mailBody,
                    ownerVerified,
                    initiator);
        }
    }
}
