package app.l2nx.gs.adapter.api.kafka.commands.ban;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanTypes;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Applies a ban; an active ban of the same subject and {@code banType} is extended, never shortened. Invariant:
 * {@code permanent == (expiresAt == null)}.
 * The host MUST disconnect the players connected with the {@code IP} or {@code HWID} of a {@code GAME_LOGIN} ban.
 * The deprecated {@code HARD} subject type fans out into character, account, IP and HWID bans; its value is the char id.
 * The wire carries both {@code subject*} and {@code target*}; a reader takes the {@code subject*} pair when
 * {@code subjectType} is set, otherwise {@code target*}. The same for {@code publicReason} and {@code reason}.
 */
public final class BanCommand implements NxCommand<BanResult> {

    private final @Nullable String subjectType;
    private final @Nullable String subjectValue;
    // TODO: drop target* once the adapter of every active game server reads subject*.
    private final @Nullable String targetType;
    private final @Nullable String targetValue;
    private final String banType;
    private final boolean permanent;
    private final @Nullable Instant expiresAt;
    private final @Nullable String publicReason;
    // TODO: drop reason together with target*.
    private final @Nullable String reason;
    private final @Nullable String issuedBy;
    private final CommandInitiator initiator;

    public BanCommand(
            @Nullable String subjectType,
            @Nullable String subjectValue,
            @Nullable String targetType,
            @Nullable String targetValue,
            String banType,
            boolean permanent,
            @Nullable Instant expiresAt,
            @Nullable String publicReason,
            @Nullable String reason,
            @Nullable String issuedBy,
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
        if (permanent && expiresAt != null) {
            throw new IllegalArgumentException("expiresAt must be null for a permanent ban");
        }
        if (!permanent && expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required for a non-permanent ban");
        }
        this.subjectType = type;
        this.subjectValue = value;
        this.targetType = type;
        this.targetValue = value;
        this.banType = banType;
        this.permanent = permanent;
        this.expiresAt = expiresAt;
        this.publicReason = publicReason != null ? publicReason : reason;
        this.reason = this.publicReason;
        this.issuedBy = issuedBy;
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

    public boolean isPermanent() {
        return permanent;
    }

    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** Shown to the player and stored on the ban row. */
    public @Nullable String getPublicReason() {
        return publicReason != null ? publicReason : reason;
    }

    /** @deprecated the former name of {@link #getPublicReason()}. */
    @Deprecated
    public @Nullable String getReason() {
        return getPublicReason();
    }

    /** In-game display name stored on the ban row; not attribution, see {@link #getInitiator()}. */
    public @Nullable String getIssuedBy() {
        return issuedBy;
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
                .permanent(permanent)
                .expiresAt(expiresAt)
                .publicReason(getPublicReason())
                .issuedBy(issuedBy)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanCommand)) return false;
        BanCommand that = (BanCommand) o;
        return permanent == that.permanent
                && Objects.equals(getSubjectType(), that.getSubjectType())
                && Objects.equals(getSubjectValue(), that.getSubjectValue())
                && Objects.equals(banType, that.banType)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(getPublicReason(), that.getPublicReason())
                && Objects.equals(issuedBy, that.issuedBy)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getSubjectType(),
                getSubjectValue(),
                banType,
                permanent,
                expiresAt,
                getPublicReason(),
                issuedBy,
                initiator);
    }

    @Override
    public String toString() {
        return "BanCommand[subjectType=" + getSubjectType()
                + ", subjectValue=" + getSubjectValue()
                + ", banType=" + banType
                + ", permanent=" + permanent
                + ", expiresAt=" + expiresAt
                + ", publicReason=" + getPublicReason()
                + ", issuedBy=" + issuedBy + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private String subjectType;
        private String subjectValue;
        private String banType;
        private boolean permanent;
        private @Nullable Instant expiresAt;
        private @Nullable String publicReason;
        private @Nullable String issuedBy;
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

        public Builder permanent(boolean permanent) {
            this.permanent = permanent;
            return this;
        }

        public Builder expiresAt(@Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder publicReason(@Nullable String publicReason) {
            this.publicReason = publicReason;
            return this;
        }

        /** @deprecated the former name of {@link #publicReason(String)}. */
        @Deprecated
        public Builder reason(@Nullable String reason) {
            return publicReason(reason);
        }

        public Builder issuedBy(@Nullable String issuedBy) {
            this.issuedBy = issuedBy;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public BanCommand build() {
            return new BanCommand(
                    subjectType,
                    subjectValue,
                    subjectType,
                    subjectValue,
                    banType,
                    permanent,
                    expiresAt,
                    publicReason,
                    publicReason,
                    issuedBy,
                    initiator);
        }
    }
}
