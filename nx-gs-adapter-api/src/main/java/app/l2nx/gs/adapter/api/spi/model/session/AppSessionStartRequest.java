package app.l2nx.gs.adapter.api.spi.model.session;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a host command handler asks the app-session engine to start, after applying every rule of its own. The engine
 * checks only the session table's invariants.
 *
 * <p>Values are validated against the table's columns here, so a bad request fails in the handler rather than as an SQL
 * error. Blank optional strings become {@code null}.</p>
 */
public final class AppSessionStartRequest {

    static final int ACCOUNT_NAME_MAX = 45;
    static final int HWID_MAX = 300;
    static final int IP_MAX = 45;
    static final int SPOT_POOL_MAX = 32;

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
    private final @Nullable Long plannedMs;
    private final @Nullable String typeState;

    public AppSessionStartRequest(
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
            @Nullable Long plannedMs,
            @Nullable String typeState) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "AppSessionStartRequest.idempotencyKey");
        this.activity = Objects.requireNonNull(activity, "AppSessionStartRequest.activity");
        if (activity != ActivityType.OFFLINE_FISHING && activity != ActivityType.OFFLINE_PRIVATE_STORE) {
            throw new IllegalArgumentException(
                    "AppSessionStartRequest.activity must be a session type (OFFLINE_FISHING, OFFLINE_PRIVATE_STORE): "
                            + activity);
        }
        this.characterId = characterId;
        this.accountName = required(accountName, "accountName", ACCOUNT_NAME_MAX);
        this.masterAccountId = masterAccountId;
        this.hwid = optional(hwid, "hwid", HWID_MAX);
        this.initiatorIp = optional(initiatorIp, "initiatorIp", IP_MAX);
        this.spotPool = normalizeSpotPool(spotPool);
        this.spotId = requireValidSpotId(spotId);
        if (originHeading < 0 || originHeading > 65535) {
            throw new IllegalArgumentException("AppSessionStartRequest.originHeading must be 0-65535");
        }
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.originHeading = originHeading;
        if (plannedMs != null && plannedMs <= 0) {
            throw new IllegalArgumentException("AppSessionStartRequest.plannedMs must be positive when present");
        }
        this.plannedMs = plannedMs;
        this.typeState = typeState == null || typeState.trim().isEmpty() ? null : typeState;
    }

    /**
     * Trimmed, required, at most {@value #SPOT_POOL_MAX} characters. {@code NxAppSessions.check} validates with this
     * and {@link #requireValidSpotId}, so a check never accepts a spot a start would reject.
     *
     * @throws IllegalArgumentException when the value is blank or too long
     */
    public static String normalizeSpotPool(@Nullable String spotPool) {
        return required(spotPool, "spotPool", SPOT_POOL_MAX);
    }

    /** @throws IllegalArgumentException when {@code spotId} is negative */
    public static int requireValidSpotId(int spotId) {
        if (spotId < 0) {
            throw new IllegalArgumentException("AppSessionStartRequest.spotId must not be negative");
        }
        return spotId;
    }

    private static String required(@Nullable String value, String field, int max) {
        String trimmed = optional(value, field, max);
        if (trimmed == null) {
            throw new IllegalArgumentException("AppSessionStartRequest." + field + " is required");
        }
        return trimmed;
    }

    private static @Nullable String optional(@Nullable String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("AppSessionStartRequest." + field + " exceeds " + max + " characters");
        }
        return trimmed;
    }

    /** A repeated start with the same key returns the same session. */
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

    /** The initiator's user id, used by the host's master-account rules. */
    public @Nullable UUID getMasterAccountId() {
        return masterAccountId;
    }

    public @Nullable String getHwid() {
        return hwid;
    }

    /** Returned in the session's presence events. */
    public @Nullable String getInitiatorIp() {
        return initiatorIp;
    }

    public String getSpotPool() {
        return spotPool;
    }

    public int getSpotId() {
        return spotId;
    }

    /** Already a safe location: the host replaces a dangerous origin (jail, instance) before the start. */
    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public int getOriginHeading() {
        return originHeading;
    }

    /** {@code null} for a session without a time limit. */
    public @Nullable Long getPlannedMs() {
        return plannedMs;
    }

    public @Nullable String getTypeState() {
        return typeState;
    }

    public Builder toBuilder() {
        return new Builder()
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
                .plannedMs(plannedMs)
                .typeState(typeState);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSessionStartRequest)) return false;
        AppSessionStartRequest that = (AppSessionStartRequest) o;
        return characterId == that.characterId
                && spotId == that.spotId
                && originX == that.originX
                && originY == that.originY
                && originZ == that.originZ
                && originHeading == that.originHeading
                && idempotencyKey.equals(that.idempotencyKey)
                && activity == that.activity
                && accountName.equals(that.accountName)
                && Objects.equals(masterAccountId, that.masterAccountId)
                && Objects.equals(hwid, that.hwid)
                && Objects.equals(initiatorIp, that.initiatorIp)
                && spotPool.equals(that.spotPool)
                && Objects.equals(plannedMs, that.plannedMs)
                && Objects.equals(typeState, that.typeState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
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
                plannedMs,
                typeState);
    }

    @Override
    public String toString() {
        return "AppSessionStartRequest[idempotencyKey=" + idempotencyKey
                + ", characterId=" + characterId
                + ", activity=" + activity
                + ", spot=" + spotPool + ":" + spotId
                + ", plannedMs=" + plannedMs + "]";
    }

    public static final class Builder {
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
        private @Nullable Long plannedMs;
        private @Nullable String typeState;

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

        public Builder plannedMs(@Nullable Long plannedMs) {
            this.plannedMs = plannedMs;
            return this;
        }

        public Builder typeState(@Nullable String typeState) {
            this.typeState = typeState;
            return this;
        }

        public AppSessionStartRequest build() {
            return new AppSessionStartRequest(
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
                    plannedMs,
                    typeState);
        }
    }
}
