package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanTypes;
import java.util.Objects;

/**
 * Lifts a ban applied by {@link BanCommand}. Clearing a ban that is not present is a no-op success (empty
 * {@code unbannedBans}), not an error.
 */
public final class UnbanCommand implements NxCommand<UnbanResult> {

    private final String subjectType;
    private final String subjectValue;
    private final String banType;
    private final CommandInitiator initiator;

    public UnbanCommand(String subjectType, String subjectValue, String banType, CommandInitiator initiator) {
        if (subjectType == null) {
            throw new IllegalArgumentException("subjectType is required");
        }
        if (subjectValue == null) {
            throw new IllegalArgumentException("subjectValue is required");
        }
        if (banType == null) {
            throw new IllegalArgumentException("banType is required");
        }
        this.subjectType = subjectType;
        this.subjectValue = subjectValue;
        this.banType = banType;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    /** A {@link WellKnownBanSubjectTypes} value. */
    public String getSubjectType() {
        return subjectType;
    }

    public String getSubjectValue() {
        return subjectValue;
    }

    /** A {@link WellKnownBanTypes} value. */
    public String getBanType() {
        return banType;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .subjectType(subjectType)
                .subjectValue(subjectValue)
                .banType(banType)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnbanCommand)) return false;
        UnbanCommand that = (UnbanCommand) o;
        return Objects.equals(subjectType, that.subjectType)
                && Objects.equals(subjectValue, that.subjectValue)
                && Objects.equals(banType, that.banType)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectType, subjectValue, banType, initiator);
    }

    @Override
    public String toString() {
        return "UnbanCommand[subjectType=" + subjectType + ", subjectValue=" + subjectValue + ", banType=" + banType
                + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private String subjectType;
        private String subjectValue;
        private String banType;
        private CommandInitiator initiator;

        public Builder subjectType(String subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        public Builder subjectValue(String subjectValue) {
            this.subjectValue = subjectValue;
            return this;
        }

        public Builder banType(String banType) {
            this.banType = banType;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public UnbanCommand build() {
            return new UnbanCommand(subjectType, subjectValue, banType, initiator);
        }
    }
}
