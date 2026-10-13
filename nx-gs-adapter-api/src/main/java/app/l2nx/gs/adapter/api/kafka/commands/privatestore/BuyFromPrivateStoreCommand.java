package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

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
 * and price, validated against the live trade list first, or nothing is charged or moved.
 */
public final class BuyFromPrivateStoreCommand implements OwnerVerified, NxCommand<BuyFromPrivateStoreResult> {

    /** One mail slot per line; the engine's attachment cap ({@code Config.MAIL_MAX_ATTACHMENTS}) is 36. */
    public static final int MAX_LINES = 36;

    private final int buyerCharId;
    private final int sellerCharId;
    private final List<BuyLine> lines;
    private final Integer buyerFeePercent;
    private final Instant deadline;
    private final String mailSender;
    private final String mailSubject;
    private final String mailBody;
    private final boolean ownerVerified;

    public BuyFromPrivateStoreCommand(
            int buyerCharId,
            int sellerCharId,
            List<BuyLine> lines,
            Integer buyerFeePercent,
            Instant deadline,
            String mailSender,
            String mailSubject,
            String mailBody,
            boolean ownerVerified) {
        if (buyerCharId <= 0) {
            throw new IllegalArgumentException("buyerCharId must be positive (got " + buyerCharId + ")");
        }
        if (sellerCharId <= 0) {
            throw new IllegalArgumentException("sellerCharId must be positive (got " + sellerCharId + ")");
        }
        if (buyerCharId == sellerCharId) {
            throw new IllegalArgumentException("buyerCharId must differ from sellerCharId (got " + buyerCharId + ")");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines is required and must be non-empty");
        }
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException(
                    "lines must not exceed MAX_LINES=" + MAX_LINES + " (got " + lines.size() + ")");
        }
        Set<Integer> seenItemIds = new HashSet<>();
        for (BuyLine line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("lines must not contain null elements");
            }
            if (!seenItemIds.add(line.getItemId())) {
                throw new IllegalArgumentException("lines must not repeat itemId (duplicate " + line.getItemId() + ")");
            }
        }
        PrivateStoreFees.requirePercent(buyerFeePercent, "buyerFeePercent");
        this.buyerCharId = buyerCharId;
        this.sellerCharId = sellerCharId;
        this.lines = PrivateStoreLists.freeze(lines);
        this.buyerFeePercent = buyerFeePercent;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.mailSender = requireText(mailSender, "mailSender");
        this.mailSubject = requireText(mailSubject, "mailSubject");
        this.mailBody = requireText(mailBody, "mailBody");
        this.ownerVerified = ownerVerified;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /** Need not be online, in range or in the seller's instance; the host loads an offline character. */
    public int getBuyerCharId() {
        return buyerCharId;
    }

    /** Must be in the world (online or offline-trading) with a sell-store open. */
    public int getSellerCharId() {
        return sellerCharId;
    }

    public List<BuyLine> getLines() {
        return lines;
    }

    /**
     * Whole percent burned on top of the lot price, {@code 0} = no fee. A missing value (Gson bypasses the constructor) or
     * one outside {@code 0..}{@link PrivateStoreFees#MAX_FEE_PERCENT} is {@code VALIDATION_FAILED}, never clamped or read as {@code 0}.
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

    public Builder toBuilder() {
        return new Builder()
                .buyerCharId(buyerCharId)
                .sellerCharId(sellerCharId)
                .lines(lines)
                .buyerFeePercent(buyerFeePercent)
                .deadline(deadline)
                .mailSender(mailSender)
                .mailSubject(mailSubject)
                .mailBody(mailBody)
                .ownerVerified(ownerVerified);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyFromPrivateStoreCommand)) return false;
        BuyFromPrivateStoreCommand that = (BuyFromPrivateStoreCommand) o;
        return buyerCharId == that.buyerCharId
                && sellerCharId == that.sellerCharId
                && Objects.equals(buyerFeePercent, that.buyerFeePercent)
                && Objects.equals(lines, that.lines)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(mailSender, that.mailSender)
                && Objects.equals(mailSubject, that.mailSubject)
                && Objects.equals(mailBody, that.mailBody)
                && ownerVerified == that.ownerVerified;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                buyerCharId,
                sellerCharId,
                lines,
                buyerFeePercent,
                deadline,
                mailSender,
                mailSubject,
                mailBody,
                ownerVerified);
    }

    @Override
    public String toString() {
        return "BuyFromPrivateStoreCommand[buyerCharId=" + buyerCharId
                + ", sellerCharId=" + sellerCharId
                + ", lines=" + lines
                + ", buyerFeePercent=" + buyerFeePercent
                + ", deadline=" + deadline
                + ", mailSender=" + mailSender
                + ", mailSubject=" + mailSubject
                + ", mailBody=" + mailBody
                + ", ownerVerified=" + ownerVerified + "]";
    }

    public static final class Builder {
        private int buyerCharId;
        private int sellerCharId;
        private List<BuyLine> lines;
        private Integer buyerFeePercent;
        private Instant deadline;
        private String mailSender;
        private String mailSubject;
        private String mailBody;
        private boolean ownerVerified;

        public Builder buyerCharId(int buyerCharId) {
            this.buyerCharId = buyerCharId;
            return this;
        }

        public Builder sellerCharId(int sellerCharId) {
            this.sellerCharId = sellerCharId;
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

        public BuyFromPrivateStoreCommand build() {
            return new BuyFromPrivateStoreCommand(
                    buyerCharId,
                    sellerCharId,
                    lines,
                    buyerFeePercent,
                    deadline,
                    mailSender,
                    mailSubject,
                    mailBody,
                    ownerVerified);
        }
    }
}
