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
 */
public final class BanCommand implements NxCommand<BanResult> {

    private final String subjectType;
    private final String subjectValue;
    private final String banType;
    private final boolean permanent;
    private final @Nullable Instant expiresAt;
    private final @Nullable String publicReason;
    private final @Nullable String issuedBy;
    private final CommandInitiator initiator;

    public BanCommand(
            String subjectType,
            String subjectValue,
            String banType,
            boolean permanent,
            @Nullable Instant expiresAt,
            @Nullable String publicReason,
            @Nullable String issuedBy,
            CommandInitiator initiator) {
        if (subjectType == null) {
            throw new IllegalArgumentException("subjectType is required");
        }
        if (subjectValue == null) {
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
        this.subjectType = subjectType;
        this.subjectValue = subjectValue;
        this.banType = banType;
        this.permanent = permanent;
        this.expiresAt = expiresAt;
        this.publicReason = publicReason;
        this.issuedBy = issuedBy;
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

    public boolean isPermanent() {
        return permanent;
    }

    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** Shown to the player and stored on the ban row. */
    public @Nullable String getPublicReason() {
        return publicReason;
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
                .subjectType(subjectType)
                .subjectValue(subjectValue)
                .banType(banType)
                .permanent(permanent)
                .expiresAt(expiresAt)
                .publicReason(publicReason)
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
                && Objects.equals(subjectType, that.subjectType)
                && Objects.equals(subjectValue, that.subjectValue)
                && Objects.equals(banType, that.banType)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(publicReason, that.publicReason)
                && Objects.equals(issuedBy, that.issuedBy)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                subjectType, subjectValue, banType, permanent, expiresAt, publicReason, issuedBy, initiator);
    }

    @Override
    public String toString() {
        return "BanCommand[subjectType=" + subjectType
                + ", subjectValue=" + subjectValue
                + ", banType=" + banType
                + ", permanent=" + permanent
                + ", expiresAt=" + expiresAt
                + ", publicReason=" + publicReason
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
                    subjectType, subjectValue, banType, permanent, expiresAt, publicReason, issuedBy, initiator);
        }
    }
}
