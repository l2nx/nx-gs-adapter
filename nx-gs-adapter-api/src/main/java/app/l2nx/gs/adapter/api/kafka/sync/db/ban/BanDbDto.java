package app.l2nx.gs.adapter.api.kafka.sync.db.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanTypes;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Wire DTO of one persisted ban row of any origin ({@link BanCommand} or in-game), payload of
 * {@code SyncEvent<BanDbDto>}. Only {@code id} is required; {@code subjectType} is never {@code HARD}.
 * The wire carries both {@code subject*} and {@code target*}; a reader takes the {@code subject*} group when
 * {@code subjectType} is set or no {@code target*} field is, otherwise {@code target*}, so a value nulled on purpose
 * never comes back from the other. {@code publicReason} and {@code reason} travel the same way, {@code publicReason}
 * first.
 */
public final class BanDbDto {

    private final long id;
    private final @Nullable String subjectType;
    private final @Nullable String subjectValue;
    private final @Nullable String subjectName;
    // TODO: drop target* once the adapter of every active game server reads subject*.
    private final @Nullable String targetType;
    private final @Nullable String targetValue;
    private final @Nullable String targetName;
    private final @Nullable String banType;
    private final @Nullable Instant expiresAt;
    private final @Nullable String publicReason;
    // TODO: drop reason together with target*.
    private final @Nullable String reason;
    private final @Nullable String issuedBy;

    public BanDbDto(
            long id,
            @Nullable String subjectType,
            @Nullable String subjectValue,
            @Nullable String subjectName,
            @Nullable String targetType,
            @Nullable String targetValue,
            @Nullable String targetName,
            @Nullable String banType,
            @Nullable Instant expiresAt,
            @Nullable String publicReason,
            @Nullable String reason,
            @Nullable String issuedBy) {
        boolean fromSubject = readsSubject(subjectType, targetType, targetValue, targetName);
        this.id = id;
        this.subjectType = fromSubject ? subjectType : targetType;
        this.subjectValue = fromSubject ? subjectValue : targetValue;
        this.subjectName = fromSubject ? subjectName : targetName;
        this.targetType = this.subjectType;
        this.targetValue = this.subjectValue;
        this.targetName = this.subjectName;
        this.banType = banType;
        this.expiresAt = expiresAt;
        this.publicReason = publicReason != null ? publicReason : reason;
        this.reason = this.publicReason;
        this.issuedBy = issuedBy;
    }

    public long getId() {
        return id;
    }

    /** A {@link WellKnownBanSubjectTypes} value. */
    public @Nullable String getSubjectType() {
        return readsSubject() ? subjectType : targetType;
    }

    public @Nullable String getSubjectValue() {
        return readsSubject() ? subjectValue : targetValue;
    }

    public @Nullable String getSubjectName() {
        return readsSubject() ? subjectName : targetName;
    }

    /** @deprecated the former name of {@link #getSubjectType()}. */
    @Deprecated
    public @Nullable String getTargetType() {
        return getSubjectType();
    }

    /** @deprecated the former name of {@link #getSubjectValue()}. */
    @Deprecated
    public @Nullable String getTargetValue() {
        return getSubjectValue();
    }

    /** @deprecated the former name of {@link #getSubjectName()}. */
    @Deprecated
    public @Nullable String getTargetName() {
        return getSubjectName();
    }

    /** A {@link WellKnownBanTypes} value. */
    public @Nullable String getBanType() {
        return banType;
    }

    /** Null means permanent. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** Shown to the player. */
    public @Nullable String getPublicReason() {
        return publicReason != null ? publicReason : reason;
    }

    /** @deprecated the former name of {@link #getPublicReason()}. */
    @Deprecated
    public @Nullable String getReason() {
        return getPublicReason();
    }

    public @Nullable String getIssuedBy() {
        return issuedBy;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .subjectType(getSubjectType())
                .subjectValue(getSubjectValue())
                .subjectName(getSubjectName())
                .banType(banType)
                .expiresAt(expiresAt)
                .publicReason(getPublicReason())
                .issuedBy(issuedBy);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanDbDto)) return false;
        BanDbDto that = (BanDbDto) o;
        return id == that.id
                && Objects.equals(getSubjectType(), that.getSubjectType())
                && Objects.equals(getSubjectValue(), that.getSubjectValue())
                && Objects.equals(getSubjectName(), that.getSubjectName())
                && Objects.equals(banType, that.banType)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(getPublicReason(), that.getPublicReason())
                && Objects.equals(issuedBy, that.issuedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                getSubjectType(),
                getSubjectValue(),
                getSubjectName(),
                banType,
                expiresAt,
                getPublicReason(),
                issuedBy);
    }

    @Override
    public String toString() {
        return "BanDbDto[id=" + id
                + ", subjectType=" + getSubjectType()
                + ", subjectValue=" + getSubjectValue()
                + ", subjectName=" + getSubjectName()
                + ", banType=" + banType
                + ", expiresAt=" + expiresAt
                + ", publicReason=" + getPublicReason()
                + ", issuedBy=" + issuedBy + "]";
    }

    private boolean readsSubject() {
        return readsSubject(subjectType, targetType, targetValue, targetName);
    }

    private static boolean readsSubject(
            @Nullable String subjectType,
            @Nullable String targetType,
            @Nullable String targetValue,
            @Nullable String targetName) {
        return subjectType != null || (targetType == null && targetValue == null && targetName == null);
    }

    public static final class Builder {
        private long id;
        private @Nullable String subjectType;
        private @Nullable String subjectValue;
        private @Nullable String subjectName;
        private @Nullable String banType;
        private @Nullable Instant expiresAt;
        private @Nullable String publicReason;
        private @Nullable String issuedBy;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder subjectType(@Nullable String subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        public Builder subjectValue(@Nullable String subjectValue) {
            this.subjectValue = subjectValue;
            return this;
        }

        public Builder subjectName(@Nullable String subjectName) {
            this.subjectName = subjectName;
            return this;
        }

        /** @deprecated the former name of {@link #subjectType(String)}. */
        @Deprecated
        public Builder targetType(@Nullable String targetType) {
            return subjectType(targetType);
        }

        /** @deprecated the former name of {@link #subjectValue(String)}. */
        @Deprecated
        public Builder targetValue(@Nullable String targetValue) {
            return subjectValue(targetValue);
        }

        /** @deprecated the former name of {@link #subjectName(String)}. */
        @Deprecated
        public Builder targetName(@Nullable String targetName) {
            return subjectName(targetName);
        }

        public Builder banType(@Nullable String banType) {
            this.banType = banType;
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

        public BanDbDto build() {
            return new BanDbDto(
                    id,
                    subjectType,
                    subjectValue,
                    subjectName,
                    subjectType,
                    subjectValue,
                    subjectName,
                    banType,
                    expiresAt,
                    publicReason,
                    publicReason,
                    issuedBy);
        }
    }
}
