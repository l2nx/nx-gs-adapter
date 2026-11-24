package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanTypes;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lifts a ban applied by {@link BanCommand}. Clearing a ban that is not present is a no-op success (empty
 * {@code unbannedBans}), not an error.
 * The deprecated {@code HARD} subject type clears every concrete dimension for the subject.
 * The wire carries both {@code subject*} and {@code target*}; a reader takes the {@code subject*} pair when
 * {@code subjectType} is set, otherwise {@code target*}.
 */
public final class UnbanCommand implements NxCommand<UnbanResult> {

    private final @Nullable String subjectType;
    private final @Nullable String subjectValue;
    // TODO: drop target* once the adapter of every active game server reads subject*.
    private final @Nullable String targetType;
    private final @Nullable String targetValue;
    private final String banType;
    private final CommandInitiator initiator;

    public UnbanCommand(
            @Nullable String subjectType,
            @Nullable String subjectValue,
            @Nullable String targetType,
            @Nullable String targetValue,
            String banType,
            CommandInitiator initiator) {
        String type = subjectType != null ? subjectType : targetType;
        String value = subjectType != null ? subjectValue : targetValue;
        if (type == null) {
            throw new IllegalArgumentException("subjectType is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("subjectValue is required");
        }
        if (banType == null) {
            throw new IllegalArgumentException("banType is required");
        }
        this.subjectType = type;
        this.subjectValue = value;
        this.targetType = type;
        this.targetValue = value;
        this.banType = banType;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    /** A {@link WellKnownBanSubjectTypes} value. */
    public String getSubjectType() {
        return subjectType != null ? subjectType : targetType;
    }

    public String getSubjectValue() {
        return subjectType != null ? subjectValue : targetValue;
    }

    /** @deprecated the former name of {@link #getSubjectType()}. */
    @Deprecated
    public String getTargetType() {
        return getSubjectType();
    }

    /** @deprecated the former name of {@link #getSubjectValue()}. */
    @Deprecated
    public String getTargetValue() {
        return getSubjectValue();
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
                .subjectType(getSubjectType())
                .subjectValue(getSubjectValue())
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
        return Objects.equals(getSubjectType(), that.getSubjectType())
                && Objects.equals(getSubjectValue(), that.getSubjectValue())
                && Objects.equals(banType, that.banType)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSubjectType(), getSubjectValue(), banType, initiator);
    }

    @Override
    public String toString() {
        return "UnbanCommand[subjectType=" + getSubjectType() + ", subjectValue=" + getSubjectValue() + ", banType="
                + banType + ", initiator=" + initiator + "]";
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

        /** @deprecated the former name of {@link #subjectType(String)}. */
        @Deprecated
        public Builder targetType(String targetType) {
            return subjectType(targetType);
        }

        /** @deprecated the former name of {@link #subjectValue(String)}. */
        @Deprecated
        public Builder targetValue(String targetValue) {
            return subjectValue(targetValue);
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
            return new UnbanCommand(subjectType, subjectValue, subjectType, subjectValue, banType, initiator);
        }
    }
}
