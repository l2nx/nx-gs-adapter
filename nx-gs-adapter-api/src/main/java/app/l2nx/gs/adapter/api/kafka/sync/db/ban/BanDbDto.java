package app.l2nx.gs.adapter.api.kafka.sync.db.ban;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanTypes;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Wire DTO of one persisted ban row of any origin ({@link BanCommand} or in-game), payload of
 * {@code SyncEvent<BanDbDto>}. Only {@code id} is required.
 */
public final class BanDbDto {

    private final long id;
    private final @Nullable String subjectType;
    private final @Nullable String subjectValue;
    private final @Nullable String subjectName;
    private final @Nullable String banType;
    private final @Nullable Instant expiresAt;
    private final @Nullable String publicReason;
    private final @Nullable String issuedBy;

    public BanDbDto(
            long id,
            @Nullable String subjectType,
            @Nullable String subjectValue,
            @Nullable String subjectName,
            @Nullable String banType,
            @Nullable Instant expiresAt,
            @Nullable String publicReason,
            @Nullable String issuedBy) {
        this.id = id;
        this.subjectType = subjectType;
        this.subjectValue = subjectValue;
        this.subjectName = subjectName;
        this.banType = banType;
        this.expiresAt = expiresAt;
        this.publicReason = publicReason;
        this.issuedBy = issuedBy;
    }

    public long getId() {
        return id;
    }

    /** A {@link WellKnownBanSubjectTypes} value. */
    public @Nullable String getSubjectType() {
        return subjectType;
    }

    public @Nullable String getSubjectValue() {
        return subjectValue;
    }

    public @Nullable String getSubjectName() {
        return subjectName;
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
        return publicReason;
    }

    public @Nullable String getIssuedBy() {
        return issuedBy;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .subjectType(subjectType)
                .subjectValue(subjectValue)
                .subjectName(subjectName)
                .banType(banType)
                .expiresAt(expiresAt)
                .publicReason(publicReason)
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
                && Objects.equals(subjectType, that.subjectType)
                && Objects.equals(subjectValue, that.subjectValue)
                && Objects.equals(subjectName, that.subjectName)
                && Objects.equals(banType, that.banType)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(publicReason, that.publicReason)
                && Objects.equals(issuedBy, that.issuedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subjectType, subjectValue, subjectName, banType, expiresAt, publicReason, issuedBy);
    }

    @Override
    public String toString() {
        return "BanDbDto[id=" + id
                + ", subjectType=" + subjectType
                + ", subjectValue=" + subjectValue
                + ", subjectName=" + subjectName
                + ", banType=" + banType
                + ", expiresAt=" + expiresAt
                + ", publicReason=" + publicReason
                + ", issuedBy=" + issuedBy + "]";
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

        public Builder issuedBy(@Nullable String issuedBy) {
            this.issuedBy = issuedBy;
            return this;
        }

        public BanDbDto build() {
            return new BanDbDto(id, subjectType, subjectValue, subjectName, banType, expiresAt, publicReason, issuedBy);
        }
    }
}
