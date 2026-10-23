package app.l2nx.gs.adapter.api.spi.model;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable view of one app session as stored in {@code l2nx_offline_sessions}. Times are UTC with second precision;
 * {@code activeMs} is the time in the world as of the moment the view was taken, restart downtime excluded.
 */
public final class OfflineSession {

    private final UUID id;
    private final UUID idempotencyKey;
    private final int characterId;
    private final ActivityType activity;
    private final String accountName;
    private final @Nullable UUID masterAccountId;
    private final @Nullable String hwid;
    private final @Nullable String initiatorIp;
    private final String spotPool;
    private final int spotId;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int originHeading;
    private final Instant startedAt;
    private final @Nullable Long plannedMs;
    private final long activeMs;
    private final @Nullable Long returnThresholdMs;
    private final @Nullable String typeState;
    private final @Nullable Instant endedAt;
    private final @Nullable LogoutReason endReason;

    public OfflineSession(
            UUID id,
            UUID idempotencyKey,
            int characterId,
            ActivityType activity,
            String accountName,
            @Nullable UUID masterAccountId,
            @Nullable String hwid,
            @Nullable String initiatorIp,
            String spotPool,
            int spotId,
            int originX,
            int originY,
            int originZ,
            int originHeading,
            Instant startedAt,
            @Nullable Long plannedMs,
            long activeMs,
            @Nullable Long returnThresholdMs,
            @Nullable String typeState,
            @Nullable Instant endedAt,
            @Nullable LogoutReason endReason) {
        this.id = Objects.requireNonNull(id, "OfflineSession.id is required");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "OfflineSession.idempotencyKey is required");
        this.characterId = characterId;
        this.activity = Objects.requireNonNull(activity, "OfflineSession.activity is required");
        this.accountName = Objects.requireNonNull(accountName, "OfflineSession.accountName is required");
        this.masterAccountId = masterAccountId;
        this.hwid = hwid;
        this.initiatorIp = initiatorIp;
        this.spotPool = Objects.requireNonNull(spotPool, "OfflineSession.spotPool is required");
        this.spotId = spotId;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.originHeading = originHeading;
        this.startedAt = Objects.requireNonNull(startedAt, "OfflineSession.startedAt is required");
        this.plannedMs = plannedMs;
        this.activeMs = activeMs;
        this.returnThresholdMs = returnThresholdMs;
        this.typeState = typeState;
        if ((endedAt == null) != (endReason == null)) {
            throw new IllegalArgumentException("OfflineSession.endedAt and endReason are set together");
        }
        this.endedAt = endedAt;
        this.endReason = endReason;
    }

    /** UUIDv7; also the {@code sessionId} of the session's presence events. */
    public UUID getId() {
        return id;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getCharacterId() {
        return characterId;
    }

    public ActivityType getActivity() {
        return activity;
    }

    public String getAccountName() {
        return accountName;
    }

    public @Nullable UUID getMasterAccountId() {
        return masterAccountId;
    }

    /** Snapshot of the character's hwid at the start; {@code null} when the host has none. */
    public @Nullable String getHwid() {
        return hwid;
    }

    /** IP of the app request that started the session; {@code null} when unknown. */
    public @Nullable String getInitiatorIp() {
        return initiatorIp;
    }

    public String getSpotPool() {
        return spotPool;
    }

    /** After a restore it may differ from the spot of the start. */
    public int getSpotId() {
        return spotId;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    /** Facing direction at the origin, 0-65535. */
    public int getOriginHeading() {
        return originHeading;
    }

    /** When the character stood on the spot at the start; the host's cooldown counts from it. */
    public Instant getStartedAt() {
        return startedAt;
    }

    /** Planned time in the world; {@code null} = no time limit (a private store runs until stopped or sold out). */
    public @Nullable Long getPlannedMs() {
        return plannedMs;
    }

    public long getActiveMs() {
        return activeMs;
    }

    /**
     * Fixed at start: ending with {@link #getActiveMs()} below it returns the character to its origin. {@code null}
     * only for a session stored before the adapter kept it; the end then uses the current app settings value.
     */
    public @Nullable Long getReturnThresholdMs() {
        return returnThresholdMs;
    }

    /** Per-type JSON owned by the host. */
    public @Nullable String getTypeState() {
        return typeState;
    }

    public @Nullable Instant getEndedAt() {
        return endedAt;
    }

    /** Set exactly when {@link #getEndedAt()} is. */
    public @Nullable LogoutReason getEndReason() {
        return endReason;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    /** The key that holds one character per spot while the session is open. */
    public String spotKey() {
        return spotPool + ":" + spotId;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .idempotencyKey(idempotencyKey)
                .characterId(characterId)
                .activity(activity)
                .accountName(accountName)
                .masterAccountId(masterAccountId)
                .hwid(hwid)
                .initiatorIp(initiatorIp)
                .spotPool(spotPool)
                .spotId(spotId)
                .originX(originX)
                .originY(originY)
                .originZ(originZ)
                .originHeading(originHeading)
                .startedAt(startedAt)
                .plannedMs(plannedMs)
                .activeMs(activeMs)
                .returnThresholdMs(returnThresholdMs)
                .typeState(typeState)
                .endedAt(endedAt)
                .endReason(endReason);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfflineSession)) return false;
        OfflineSession that = (OfflineSession) o;
        return characterId == that.characterId
                && spotId == that.spotId
                && originX == that.originX
                && originY == that.originY
                && originZ == that.originZ
                && originHeading == that.originHeading
                && activeMs == that.activeMs
                && id.equals(that.id)
                && idempotencyKey.equals(that.idempotencyKey)
                && activity == that.activity
                && accountName.equals(that.accountName)
                && Objects.equals(masterAccountId, that.masterAccountId)
                && Objects.equals(hwid, that.hwid)
                && Objects.equals(initiatorIp, that.initiatorIp)
                && spotPool.equals(that.spotPool)
                && startedAt.equals(that.startedAt)
                && Objects.equals(plannedMs, that.plannedMs)
                && Objects.equals(returnThresholdMs, that.returnThresholdMs)
                && Objects.equals(typeState, that.typeState)
                && Objects.equals(endedAt, that.endedAt)
                && endReason == that.endReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                idempotencyKey,
                characterId,
                activity,
                accountName,
                masterAccountId,
                hwid,
                initiatorIp,
                spotPool,
                spotId,
                originX,
                originY,
                originZ,
                originHeading,
                startedAt,
                plannedMs,
                activeMs,
                returnThresholdMs,
                typeState,
                endedAt,
                endReason);
    }

    @Override
    public String toString() {
        return "OfflineSession[id=" + id
                + ", characterId=" + characterId
                + ", activity=" + activity
                + ", spot=" + spotKey()
                + ", startedAt=" + startedAt
                + ", plannedMs=" + plannedMs
                + ", activeMs=" + activeMs
                + ", endedAt=" + endedAt
                + ", endReason=" + endReason + "]";
    }

    public static final class Builder {
        private @Nullable UUID id;
        private @Nullable UUID idempotencyKey;
        private int characterId;
        private @Nullable ActivityType activity;
        private @Nullable String accountName;
        private @Nullable UUID masterAccountId;
        private @Nullable String hwid;
        private @Nullable String initiatorIp;
        private @Nullable String spotPool;
        private int spotId;
        private int originX;
        private int originY;
        private int originZ;
        private int originHeading;
        private @Nullable Instant startedAt;
        private @Nullable Long plannedMs;
        private long activeMs;
        private @Nullable Long returnThresholdMs;
        private @Nullable String typeState;
        private @Nullable Instant endedAt;
        private @Nullable LogoutReason endReason;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder characterId(int characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder activity(ActivityType activity) {
            this.activity = activity;
            return this;
        }

        public Builder accountName(String accountName) {
            this.accountName = accountName;
            return this;
        }

        public Builder masterAccountId(@Nullable UUID masterAccountId) {
            this.masterAccountId = masterAccountId;
            return this;
        }

        public Builder hwid(@Nullable String hwid) {
            this.hwid = hwid;
            return this;
        }

        public Builder initiatorIp(@Nullable String initiatorIp) {
            this.initiatorIp = initiatorIp;
            return this;
        }

        public Builder spotPool(String spotPool) {
            this.spotPool = spotPool;
            return this;
        }

        public Builder spotId(int spotId) {
            this.spotId = spotId;
            return this;
        }

        public Builder originX(int originX) {
            this.originX = originX;
            return this;
        }

        public Builder originY(int originY) {
            this.originY = originY;
            return this;
        }

        public Builder originZ(int originZ) {
            this.originZ = originZ;
            return this;
        }

        public Builder originHeading(int originHeading) {
            this.originHeading = originHeading;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder plannedMs(@Nullable Long plannedMs) {
            this.plannedMs = plannedMs;
            return this;
        }

        public Builder activeMs(long activeMs) {
            this.activeMs = activeMs;
            return this;
        }

        public Builder returnThresholdMs(@Nullable Long returnThresholdMs) {
            this.returnThresholdMs = returnThresholdMs;
            return this;
        }

        public Builder typeState(@Nullable String typeState) {
            this.typeState = typeState;
            return this;
        }

        public Builder endedAt(@Nullable Instant endedAt) {
            this.endedAt = endedAt;
            return this;
        }

        public Builder endReason(@Nullable LogoutReason endReason) {
            this.endReason = endReason;
            return this;
        }

        public OfflineSession build() {
            return new OfflineSession(
                    id,
                    idempotencyKey,
                    characterId,
                    activity,
                    accountName,
                    masterAccountId,
                    hwid,
                    initiatorIp,
                    spotPool,
                    spotId,
                    originX,
                    originY,
                    originZ,
                    originHeading,
                    startedAt,
                    plannedMs,
                    activeMs,
                    returnThresholdMs,
                    typeState,
                    endedAt,
                    endReason);
        }
    }
}
