package app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One usage row of a character: what an activity consumed in the current period and what it may still consume.
 * Identified by {@code (type, key)}; {@code subtype} does not change the identity. Blank optional strings become
 * {@code null}.
 */
public final class ActivityUsage {

    private final ActivityType type;
    private final @Nullable String key;
    private final @Nullable String subtype;
    private final @Nullable Instant activeSince;
    private final UsageUnit unit;
    private final long used;
    private final @Nullable Long remaining;
    private final @Nullable Long limit;
    private final @Nullable String resetSchedule;
    private final List<UsageStep> steps;
    private final @Nullable Map<String, String> metadata;
    private final Instant updatedAt;

    public ActivityUsage(
            ActivityType type,
            @Nullable String key,
            @Nullable String subtype,
            @Nullable Instant activeSince,
            UsageUnit unit,
            long used,
            @Nullable Long remaining,
            @Nullable Long limit,
            @Nullable String resetSchedule,
            @Nullable List<UsageStep> steps,
            @Nullable Map<String, String> metadata,
            Instant updatedAt) {
        this.type = Objects.requireNonNull(type, "ActivityUsage.type is required");
        this.unit = Objects.requireNonNull(unit, "ActivityUsage.unit is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "ActivityUsage.updatedAt is required");
        if (used < 0) {
            throw new IllegalArgumentException("ActivityUsage.used must not be negative");
        }
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("ActivityUsage.limit must not be negative");
        }
        if (remaining != null && remaining < 0) {
            throw new IllegalArgumentException("ActivityUsage.remaining must not be negative");
        }
        if (limit == null && remaining != null) {
            throw new IllegalArgumentException("ActivityUsage.remaining must be null when limit is null");
        }
        this.key = blankToNull(key);
        this.subtype = blankToNull(subtype);
        this.activeSince = activeSince;
        this.used = used;
        this.remaining = remaining;
        this.limit = limit;
        this.resetSchedule = blankToNull(resetSchedule);
        this.steps = steps == null
                ? Collections.<UsageStep>emptyList()
                : Collections.unmodifiableList(new ArrayList<UsageStep>(steps));
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public ActivityType getType() {
        return type;
    }

    /** Refinement inside the type, e.g. an instance template id; {@code null} when the type has no sub-identity. */
    public @Nullable String getKey() {
        return key;
    }

    public @Nullable String getSubtype() {
        return subtype;
    }

    /** {@code null} = the activity is not running now; otherwise the moment the current run started. */
    public @Nullable Instant getActiveSince() {
        return activeSince;
    }

    public UsageUnit getUnit() {
        return unit;
    }

    public long getUsed() {
        return used;
    }

    /** {@code limit - used} floored at zero; {@code null} exactly when {@link #getLimit() limit} is {@code null}. */
    public @Nullable Long getRemaining() {
        return remaining;
    }

    /** Ceiling per period in {@link #getUnit() unit}; {@code null} = unlimited. */
    public @Nullable Long getLimit() {
        return limit;
    }

    /**
     * RFC 5545 period reset in UTC: {@code DTSTART:<UTC>Z} then {@code RRULE:...}, no {@code TZID}; {@code null} =
     * never resets. The host republishes the rule when its clock offset changes.
     */
    public @Nullable String getResetSchedule() {
        return resetSchedule;
    }

    /** Tiers in ascending {@code after} order; empty = no tiers. */
    public List<UsageStep> getSteps() {
        return steps == null ? Collections.<UsageStep>emptyList() : steps;
    }

    /** Open map for extras no canonical field covers; consumers ignore unknown keys. */
    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Builder toBuilder() {
        return new Builder()
                .type(type)
                .key(key)
                .subtype(subtype)
                .activeSince(activeSince)
                .unit(unit)
                .used(used)
                .remaining(remaining)
                .limit(limit)
                .resetSchedule(resetSchedule)
                .steps(steps)
                .metadata(metadata)
                .updatedAt(updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivityUsage)) return false;
        ActivityUsage that = (ActivityUsage) o;
        return used == that.used
                && type == that.type
                && unit == that.unit
                && Objects.equals(key, that.key)
                && Objects.equals(subtype, that.subtype)
                && Objects.equals(activeSince, that.activeSince)
                && Objects.equals(remaining, that.remaining)
                && Objects.equals(limit, that.limit)
                && Objects.equals(resetSchedule, that.resetSchedule)
                && Objects.equals(steps, that.steps)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                type,
                key,
                subtype,
                activeSince,
                unit,
                used,
                remaining,
                limit,
                resetSchedule,
                steps,
                metadata,
                updatedAt);
    }

    @Override
    public String toString() {
        return "ActivityUsage[type=" + type
                + ", key=" + key
                + ", subtype=" + subtype
                + ", activeSince=" + activeSince
                + ", unit=" + unit
                + ", used=" + used
                + ", remaining=" + remaining
                + ", limit=" + limit
                + ", resetSchedule=" + resetSchedule
                + ", steps=" + steps
                + ", metadata=" + metadata
                + ", updatedAt=" + updatedAt + "]";
    }

    public static final class Builder {
        private @Nullable ActivityType type;
        private @Nullable String key;
        private @Nullable String subtype;
        private @Nullable Instant activeSince;
        private @Nullable UsageUnit unit;
        private long used;
        private @Nullable Long remaining;
        private @Nullable Long limit;
        private @Nullable String resetSchedule;
        private @Nullable List<UsageStep> steps;
        private @Nullable Map<String, String> metadata;
        private @Nullable Instant updatedAt;

        public Builder type(ActivityType type) {
            this.type = type;
            return this;
        }

        public Builder key(@Nullable String key) {
            this.key = key;
            return this;
        }

        public Builder subtype(@Nullable String subtype) {
            this.subtype = subtype;
            return this;
        }

        public Builder activeSince(@Nullable Instant activeSince) {
            this.activeSince = activeSince;
            return this;
        }

        public Builder unit(UsageUnit unit) {
            this.unit = unit;
            return this;
        }

        public Builder used(long used) {
            this.used = used;
            return this;
        }

        public Builder remaining(@Nullable Long remaining) {
            this.remaining = remaining;
            return this;
        }

        public Builder limit(@Nullable Long limit) {
            this.limit = limit;
            return this;
        }

        public Builder resetSchedule(@Nullable String resetSchedule) {
            this.resetSchedule = resetSchedule;
            return this;
        }

        public Builder steps(@Nullable List<UsageStep> steps) {
            this.steps = steps;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ActivityUsage build() {
            return new ActivityUsage(
                    type,
                    key,
                    subtype,
                    activeSince,
                    unit,
                    used,
                    remaining,
                    limit,
                    resetSchedule,
                    steps,
                    metadata,
                    updatedAt);
        }
    }
}
