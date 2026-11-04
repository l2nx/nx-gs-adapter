package app.l2nx.gs.adapter.api.kafka.commands.session;

import app.l2nx.gs.adapter.api.kafka.commands.session.model.AlreadyActiveScope;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Answer of every {@code Check<Type>Command}: would the matching start, with the same inputs, start now. A refusal is
 * a successful check ({@code CommandResult.ok} with {@code allowed = false}), carrying what the refused start would carry
 * under the keys of {@link AppSessionRefusal}. Gson bypasses the constructor, so a consumer re-validates.
 */
public final class AppSessionCheckResult {

    private final boolean allowed;
    private final @Nullable AppSessionRefusalReason reason;
    private final @Nullable AlreadyActiveScope scope;
    private final @Nullable Instant retryAt;
    private final @Nullable Long effectiveDurationMs;
    private final @Nullable Integer requiredSlots;
    private final @Nullable Integer freeSlots;
    private final @Nullable Long requiredWeight;
    private final @Nullable Long freeWeight;

    public AppSessionCheckResult(
            boolean allowed,
            @Nullable AppSessionRefusalReason reason,
            @Nullable AlreadyActiveScope scope,
            @Nullable Instant retryAt,
            @Nullable Long effectiveDurationMs,
            @Nullable Integer requiredSlots,
            @Nullable Integer freeSlots,
            @Nullable Long requiredWeight,
            @Nullable Long freeWeight) {
        if (allowed == (reason != null)) {
            throw new IllegalArgumentException("AppSessionCheckResult.reason is required exactly when not allowed");
        }
        if ((reason == AppSessionRefusalReason.ALREADY_ACTIVE) != (scope != null)) {
            throw new IllegalArgumentException("AppSessionCheckResult.scope is required exactly with ALREADY_ACTIVE");
        }
        if (requiresRetryAt(reason) && retryAt == null) {
            throw new IllegalArgumentException(
                    "AppSessionCheckResult.retryAt is required with RECENTLY_LOGGED_OUT and COOLDOWN");
        }
        if (retryAt != null && !requiresRetryAt(reason) && reason != AppSessionRefusalReason.DAILY_LIMIT) {
            throw new IllegalArgumentException(
                    "AppSessionCheckResult.retryAt is allowed only with RECENTLY_LOGGED_OUT, COOLDOWN and DAILY_LIMIT");
        }
        if (effectiveDurationMs != null && (!allowed || effectiveDurationMs <= 0)) {
            throw new IllegalArgumentException(
                    "AppSessionCheckResult.effectiveDurationMs must be positive and only on an allowed check");
        }
        boolean inventoryFull = reason == AppSessionRefusalReason.INVENTORY_FULL;
        if (inventoryFull != (requiredSlots != null) || inventoryFull != (freeSlots != null)) {
            throw new IllegalArgumentException(
                    "AppSessionCheckResult.requiredSlots and freeSlots are required exactly with INVENTORY_FULL");
        }
        boolean overweight = reason == AppSessionRefusalReason.OVERWEIGHT;
        if (overweight != (requiredWeight != null) || overweight != (freeWeight != null)) {
            throw new IllegalArgumentException(
                    "AppSessionCheckResult.requiredWeight and freeWeight are required exactly with OVERWEIGHT");
        }
        if ((requiredSlots != null && requiredSlots <= 0) || (requiredWeight != null && requiredWeight <= 0L)) {
            throw new IllegalArgumentException(
                    "AppSessionCheckResult.requiredSlots and requiredWeight must be positive");
        }
        if ((freeSlots != null && freeSlots < 0) || (freeWeight != null && freeWeight < 0L)) {
            throw new IllegalArgumentException("AppSessionCheckResult.freeSlots and freeWeight must not be negative");
        }
        this.allowed = allowed;
        this.reason = reason;
        this.scope = scope;
        this.retryAt = retryAt;
        this.effectiveDurationMs = effectiveDurationMs;
        this.requiredSlots = requiredSlots;
        this.freeSlots = freeSlots;
        this.requiredWeight = requiredWeight;
        this.freeWeight = freeWeight;
    }

    private static boolean requiresRetryAt(@Nullable AppSessionRefusalReason reason) {
        return reason == AppSessionRefusalReason.RECENTLY_LOGGED_OUT || reason == AppSessionRefusalReason.COOLDOWN;
    }

    public static AppSessionCheckResult allowed(@Nullable Long effectiveDurationMs) {
        return new AppSessionCheckResult(true, null, null, null, effectiveDurationMs, null, null, null, null);
    }

    public static AppSessionCheckResult refused(AppSessionRefusalReason reason) {
        return new AppSessionCheckResult(
                false, Objects.requireNonNull(reason, "reason"), null, null, null, null, null, null, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /** The first refusal in the order the start would hit it. */
    public @Nullable AppSessionRefusalReason getReason() {
        return reason;
    }

    public @Nullable AlreadyActiveScope getScope() {
        return scope;
    }

    /**
     * Set for {@code RECENTLY_LOGGED_OUT} and {@code COOLDOWN}, and for {@code DAILY_LIMIT} when the host knows when the
     * daily allowance resets: the instant the start would carry.
     */
    public @Nullable Instant getRetryAt() {
        return retryAt;
    }

    /** What a start with these inputs would run now (the daily stop applied); {@code null} without a duration. */
    public @Nullable Long getEffectiveDurationMs() {
        return effectiveDurationMs;
    }

    public @Nullable Integer getRequiredSlots() {
        return requiredSlots;
    }

    /** Set with {@code INVENTORY_FULL}, as the start carries it; never below {@code 0}. */
    public @Nullable Integer getFreeSlots() {
        return freeSlots;
    }

    public @Nullable Long getRequiredWeight() {
        return requiredWeight;
    }

    /** Set with {@code OVERWEIGHT}, as the start carries it; never below {@code 0}. */
    public @Nullable Long getFreeWeight() {
        return freeWeight;
    }

    public Builder toBuilder() {
        return new Builder()
                .allowed(allowed)
                .reason(reason)
                .scope(scope)
                .retryAt(retryAt)
                .effectiveDurationMs(effectiveDurationMs)
                .requiredSlots(requiredSlots)
                .freeSlots(freeSlots)
                .requiredWeight(requiredWeight)
                .freeWeight(freeWeight);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSessionCheckResult)) return false;
        AppSessionCheckResult that = (AppSessionCheckResult) o;
        return allowed == that.allowed
                && reason == that.reason
                && scope == that.scope
                && Objects.equals(retryAt, that.retryAt)
                && Objects.equals(effectiveDurationMs, that.effectiveDurationMs)
                && Objects.equals(requiredSlots, that.requiredSlots)
                && Objects.equals(freeSlots, that.freeSlots)
                && Objects.equals(requiredWeight, that.requiredWeight)
                && Objects.equals(freeWeight, that.freeWeight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                allowed,
                reason,
                scope,
                retryAt,
                effectiveDurationMs,
                requiredSlots,
                freeSlots,
                requiredWeight,
                freeWeight);
    }

    @Override
    public String toString() {
        return "AppSessionCheckResult[allowed=" + allowed
                + ", reason=" + reason
                + ", scope=" + scope
                + ", retryAt=" + retryAt
                + ", effectiveDurationMs=" + effectiveDurationMs
                + ", requiredSlots=" + requiredSlots
                + ", freeSlots=" + freeSlots
                + ", requiredWeight=" + requiredWeight
                + ", freeWeight=" + freeWeight + "]";
    }

    public static final class Builder {
        private boolean allowed;
        private @Nullable AppSessionRefusalReason reason;
        private @Nullable AlreadyActiveScope scope;
        private @Nullable Instant retryAt;
        private @Nullable Long effectiveDurationMs;
        private @Nullable Integer requiredSlots;
        private @Nullable Integer freeSlots;
        private @Nullable Long requiredWeight;
        private @Nullable Long freeWeight;

        public Builder allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }

        public Builder reason(@Nullable AppSessionRefusalReason reason) {
            this.reason = reason;
            return this;
        }

        public Builder scope(@Nullable AlreadyActiveScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder retryAt(@Nullable Instant retryAt) {
            this.retryAt = retryAt;
            return this;
        }

        public Builder effectiveDurationMs(@Nullable Long effectiveDurationMs) {
            this.effectiveDurationMs = effectiveDurationMs;
            return this;
        }

        public Builder requiredSlots(@Nullable Integer requiredSlots) {
            this.requiredSlots = requiredSlots;
            return this;
        }

        public Builder freeSlots(@Nullable Integer freeSlots) {
            this.freeSlots = freeSlots;
            return this;
        }

        public Builder requiredWeight(@Nullable Long requiredWeight) {
            this.requiredWeight = requiredWeight;
            return this;
        }

        public Builder freeWeight(@Nullable Long freeWeight) {
            this.freeWeight = freeWeight;
            return this;
        }

        public AppSessionCheckResult build() {
            return new AppSessionCheckResult(
                    allowed,
                    reason,
                    scope,
                    retryAt,
                    effectiveDurationMs,
                    requiredSlots,
                    freeSlots,
                    requiredWeight,
                    freeWeight);
        }
    }
}
