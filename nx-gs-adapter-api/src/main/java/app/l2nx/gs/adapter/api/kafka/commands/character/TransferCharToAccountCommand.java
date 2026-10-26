package app.l2nx.gs.adapter.api.kafka.commands.character;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Moves a character to a different login account. Only the {@code account_name} pointer is rewritten; the character
 * itself (charId, items, clan, progression) stays intact.
 *
 * <p>Reply: {@code CommandResult<Void>}. Errors: {@code NOT_FOUND}, {@code INVALID_STATE} (cannot be rebound now,
 * e.g. jailed, in olympiad or siege; host policy defines the set), {@code FORBIDDEN} (e.g. banned character),
 * {@code VALIDATION_FAILED} (Gson leaves missing wire fields {@code null}, so the handler must null-check),
 * {@code UNAVAILABLE} (transient persistence failure; retry may succeed).</p>
 *
 * <p>If the character is logged in, the handler should force a logout before the rebind so the client never sees
 * inconsistent account state mid-session. Routed by {@code charId}.</p>
 *
 * <p>Delivery is at-most-once (see {@link app.l2nx.gs.adapter.api.spi.capability.CommandHandler}), but a caller may
 * re-issue after a reply timeout when the transfer already landed; if {@code account_name} already matches
 * {@code accountTo}, the handler should reply no-op success instead of re-running the UPDATE.</p>
 */
public final class TransferCharToAccountCommand implements NxCommand<TransferCharToAccountResult> {

    private final Long charId;
    private final String accountTo;
    private final CommandInitiator initiator;

    public TransferCharToAccountCommand(Long charId, String accountTo, CommandInitiator initiator) {
        if (charId == null) {
            throw new IllegalArgumentException("charId is required");
        }
        if (accountTo == null) {
            throw new IllegalArgumentException("accountTo is required");
        }
        this.charId = charId;
        this.accountTo = accountTo;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharId() {
        return charId;
    }

    /** Target login-account name; handler may reply {@code NOT_FOUND} if it does not exist, or defer to the next login depending on host policy. */
    public String getAccountTo() {
        return accountTo;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder().charId(charId).accountTo(accountTo).initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferCharToAccountCommand)) return false;
        TransferCharToAccountCommand that = (TransferCharToAccountCommand) o;
        return Objects.equals(charId, that.charId)
                && Objects.equals(accountTo, that.accountTo)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(charId, accountTo, initiator);
    }

    @Override
    public String toString() {
        return "TransferCharToAccountCommand[charId=" + charId + ", accountTo=" + accountTo + ", initiator=" + initiator
                + "]";
    }

    public static final class Builder {
        private Long charId;
        private String accountTo;
        private CommandInitiator initiator;

        public Builder charId(Long charId) {
            this.charId = charId;
            return this;
        }

        public Builder accountTo(String accountTo) {
            this.accountTo = accountTo;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public TransferCharToAccountCommand build() {
            return new TransferCharToAccountCommand(charId, accountTo, initiator);
        }
    }
}
