package app.l2nx.gs.adapter.api.spi.model.appsettings;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One immutable, typed snapshot of {@code l2nx-app.properties}. Ranges and cross-key rules are enforced on build, so
 * no out-of-range snapshot exists; the adapter's file validation reads the same constants.
 */
public final class AppRules {

    public static final int PHANTOM_TITLE_MAX_CODE_POINTS = 16;
    public static final Range PHANTOM_TITLE_COLOR_RGB = Range.between(0, 0xFFFFFF);
    public static final Range RECENT_LOGOUT_MINUTES = Range.between(0, 60);
    public static final Range RESTART_WINDOW_MINUTES = Range.between(0, 120);
    public static final Range RETURN_THRESHOLD_MINUTES = Range.atLeast(0);
    public static final Range PRIVATE_STORE_START_COOLDOWN_MINUTES = Range.between(0, 1440);
    public static final Range FISHING_START_COOLDOWN_MINUTES = Range.between(0, 1440);
    /** Both the minimum and the maximum duration; the maximum is also at least the minimum. */
    public static final Range FISHING_DURATION_MINUTES = Range.atLeast(1);

    public static final Range FISHING_FLOAT_DISTANCE = Range.between(90, 250);
    public static final Range RESTORE_BATCH_SIZE = Range.atLeast(1);
    public static final Range RESTORE_BATCH_PAUSE_MILLIS = Range.atLeast(0);
    /** The session-history read relies on it: no rule window may exceed the minimum retention. */
    public static final Range HISTORY_RETENTION_DAYS = Range.atLeast(7);

    public static final Set<ActivityType> SESSION_TYPES =
            Collections.unmodifiableSet(EnumSet.of(ActivityType.OFFLINE_PRIVATE_STORE, ActivityType.OFFLINE_FISHING));

    private static final Pattern SPOT_POOL_NAME = Pattern.compile("[a-z0-9_]{1,32}");

    // after the constants: building it validates against them
    private static final AppRules DEFAULTS = new Builder().build();

    private final @Nullable String phantomTitleText;
    private final int phantomTitleColorRgb;
    private final int recentLogoutMinutes;
    private final int restartWindowMinutes;
    private final int returnThresholdMinutes;
    private final Set<ActivityType> enabledSessionTypes;
    private final int privateStoreStartCooldownMinutes;
    private final int fishingStartCooldownMinutes;
    private final int fishingMinDurationMinutes;
    private final int fishingMaxDurationMinutes;
    private final int fishingFloatDistance;
    private final SpotOrder fishingSpotOrder;
    private final @Nullable String fishingSpotPool;
    private final int restoreBatchSize;
    private final int restoreBatchPauseMillis;
    private final int historyRetentionDays;

    private AppRules(Builder b) {
        validate(b);
        this.phantomTitleText = b.phantomTitleText;
        this.phantomTitleColorRgb = b.phantomTitleColorRgb;
        this.recentLogoutMinutes = b.recentLogoutMinutes;
        this.restartWindowMinutes = b.restartWindowMinutes;
        this.returnThresholdMinutes = b.returnThresholdMinutes;
        this.enabledSessionTypes = b.enabledSessionTypes.isEmpty()
                ? Collections.<ActivityType>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(b.enabledSessionTypes));
        this.privateStoreStartCooldownMinutes = b.privateStoreStartCooldownMinutes;
        this.fishingStartCooldownMinutes = b.fishingStartCooldownMinutes;
        this.fishingMinDurationMinutes = b.fishingMinDurationMinutes;
        this.fishingMaxDurationMinutes = b.fishingMaxDurationMinutes;
        this.fishingFloatDistance = b.fishingFloatDistance;
        this.fishingSpotOrder = Objects.requireNonNull(b.fishingSpotOrder, "AppRules.fishingSpotOrder is required");
        this.fishingSpotPool = b.fishingSpotPool;
        this.restoreBatchSize = b.restoreBatchSize;
        this.restoreBatchPauseMillis = b.restoreBatchPauseMillis;
        this.historyRetentionDays = b.historyRetentionDays;
    }

    private static void validate(Builder b) {
        String title = b.phantomTitleText;
        if (title != null
                && (title.trim().isEmpty()
                        || title.codePointCount(0, title.length()) > PHANTOM_TITLE_MAX_CODE_POINTS)) {
            throw new IllegalArgumentException("AppRules.phantomTitleText must be null or 1-"
                    + PHANTOM_TITLE_MAX_CODE_POINTS + " code points, not blank: '" + title + "'");
        }
        PHANTOM_TITLE_COLOR_RGB.check("phantomTitleColorRgb", b.phantomTitleColorRgb);
        RECENT_LOGOUT_MINUTES.check("recentLogoutMinutes", b.recentLogoutMinutes);
        RESTART_WINDOW_MINUTES.check("restartWindowMinutes", b.restartWindowMinutes);
        RETURN_THRESHOLD_MINUTES.check("returnThresholdMinutes", b.returnThresholdMinutes);
        PRIVATE_STORE_START_COOLDOWN_MINUTES.check(
                "privateStoreStartCooldownMinutes", b.privateStoreStartCooldownMinutes);
        FISHING_START_COOLDOWN_MINUTES.check("fishingStartCooldownMinutes", b.fishingStartCooldownMinutes);
        FISHING_DURATION_MINUTES.check("fishingMinDurationMinutes", b.fishingMinDurationMinutes);
        FISHING_DURATION_MINUTES.check("fishingMaxDurationMinutes", b.fishingMaxDurationMinutes);
        if (!fishingDurationsOrdered(b.fishingMinDurationMinutes, b.fishingMaxDurationMinutes)) {
            throw new IllegalArgumentException("AppRules.fishingMaxDurationMinutes " + b.fishingMaxDurationMinutes
                    + " is less than fishingMinDurationMinutes " + b.fishingMinDurationMinutes);
        }
        FISHING_FLOAT_DISTANCE.check("fishingFloatDistance", b.fishingFloatDistance);
        if (b.fishingSpotPool != null && !isSpotPoolName(b.fishingSpotPool)) {
            throw new IllegalArgumentException("AppRules.fishingSpotPool must match " + SPOT_POOL_NAME.pattern() + ": '"
                    + b.fishingSpotPool + "'");
        }
        if (b.fishingSpotPool == null) {
            for (ActivityType activity : b.enabledSessionTypes) {
                if (needsSpotPool(activity)) {
                    throw new IllegalArgumentException(
                            "AppRules.fishingSpotPool is required while " + activity + " is enabled");
                }
            }
        }
        RESTORE_BATCH_SIZE.check("restoreBatchSize", b.restoreBatchSize);
        RESTORE_BATCH_PAUSE_MILLIS.check("restoreBatchPauseMillis", b.restoreBatchPauseMillis);
        HISTORY_RETENTION_DAYS.check("historyRetentionDays", b.historyRetentionDays);
    }

    /** {@code true} for a session type that may be enabled only with {@link #fishingSpotPool()} set. */
    public static boolean needsSpotPool(ActivityType activity) {
        return activity == ActivityType.OFFLINE_FISHING;
    }

    /** The cross-key rule of the fishing duration: the maximum is at least the minimum. */
    public static boolean fishingDurationsOrdered(int minMinutes, int maxMinutes) {
        return maxMinutes >= minMinutes;
    }

    /** 1-32 characters of {@code a-z}, {@code 0-9} and {@code _}. */
    public static boolean isSpotPoolName(String name) {
        return SPOT_POOL_NAME.matcher(name).matches();
    }

    /** The values of a server without a file, or with a file that never passed validation. */
    public static AppRules defaults() {
        return DEFAULTS;
    }

    /** Display-only title over a placed character; {@code null}: no override, the character shows its own title. */
    public @Nullable String phantomTitleText() {
        return phantomTitleText;
    }

    /** RGB of {@link #phantomTitleText()}, {@code 0xRRGGBB}; used only when the text is set. */
    public int phantomTitleColorRgb() {
        return phantomTitleColorRgb;
    }

    /** No app start within this many minutes after the character left the game with a client; {@code 0}: no rule. */
    public int recentLogoutMinutes() {
        return recentLogoutMinutes;
    }

    /** No app start when a scheduled restart is closer than this many minutes. */
    public int restartWindowMinutes() {
        return restartWindowMinutes;
    }

    /**
     * A session ending with less time in the world than this returns the character to its origin. The engine fixes the
     * value per session at its start, so a change governs new sessions only.
     */
    public int returnThresholdMinutes() {
        return returnThresholdMinutes;
    }

    /** {@code false} also for an activity that is not an app session type. A disabled type lets running sessions finish. */
    public boolean sessionTypeEnabled(ActivityType activity) {
        return enabledSessionTypes.contains(activity);
    }

    /**
     * No private-store start (sell or buy) of a character within this many minutes after the real start of that
     * character's previous app private-store session; {@code 0}: no rule. Stopping a store is never limited.
     */
    public int privateStoreStartCooldownMinutes() {
        return privateStoreStartCooldownMinutes;
    }

    /**
     * No fishing start for any character of a master account within this many minutes after the real start of that
     * master account's previous app fishing session.
     */
    public int fishingStartCooldownMinutes() {
        return fishingStartCooldownMinutes;
    }

    public int fishingMinDurationMinutes() {
        return fishingMinDurationMinutes;
    }

    /** At least {@link #fishingMinDurationMinutes()}. */
    public int fishingMaxDurationMinutes() {
        return fishingMaxDurationMinutes;
    }

    /** Distance in game units from the fisher to the float along the spot's heading. */
    public int fishingFloatDistance() {
        return fishingFloatDistance;
    }

    public SpotOrder fishingSpotOrder() {
        return fishingSpotOrder;
    }

    /**
     * Name of the spot pool new fishing sessions use; the pool's spots are host data. Never {@code null} while fishing
     * is enabled; a restored session keeps the pool stored with it.
     */
    public @Nullable String fishingSpotPool() {
        return fishingSpotPool;
    }

    /** Sessions restored per batch after a restart; the engine caps it at its share of the IO pool. */
    public int restoreBatchSize() {
        return restoreBatchSize;
    }

    public int restoreBatchPauseMillis() {
        return restoreBatchPauseMillis;
    }

    /** Closed sessions older than this are deleted from the adapter's table at server start. */
    public int historyRetentionDays() {
        return historyRetentionDays;
    }

    public Builder toBuilder() {
        Builder b = new Builder()
                .phantomTitleText(phantomTitleText)
                .phantomTitleColorRgb(phantomTitleColorRgb)
                .recentLogoutMinutes(recentLogoutMinutes)
                .restartWindowMinutes(restartWindowMinutes)
                .returnThresholdMinutes(returnThresholdMinutes)
                .privateStoreStartCooldownMinutes(privateStoreStartCooldownMinutes)
                .fishingStartCooldownMinutes(fishingStartCooldownMinutes)
                .fishingMinDurationMinutes(fishingMinDurationMinutes)
                .fishingMaxDurationMinutes(fishingMaxDurationMinutes)
                .fishingFloatDistance(fishingFloatDistance)
                .fishingSpotOrder(fishingSpotOrder)
                .fishingSpotPool(fishingSpotPool)
                .restoreBatchSize(restoreBatchSize)
                .restoreBatchPauseMillis(restoreBatchPauseMillis)
                .historyRetentionDays(historyRetentionDays);
        b.enabledSessionTypes.addAll(enabledSessionTypes);
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppRules)) return false;
        AppRules that = (AppRules) o;
        return phantomTitleColorRgb == that.phantomTitleColorRgb
                && recentLogoutMinutes == that.recentLogoutMinutes
                && restartWindowMinutes == that.restartWindowMinutes
                && returnThresholdMinutes == that.returnThresholdMinutes
                && privateStoreStartCooldownMinutes == that.privateStoreStartCooldownMinutes
                && fishingStartCooldownMinutes == that.fishingStartCooldownMinutes
                && fishingMinDurationMinutes == that.fishingMinDurationMinutes
                && fishingMaxDurationMinutes == that.fishingMaxDurationMinutes
                && fishingFloatDistance == that.fishingFloatDistance
                && restoreBatchSize == that.restoreBatchSize
                && restoreBatchPauseMillis == that.restoreBatchPauseMillis
                && historyRetentionDays == that.historyRetentionDays
                && Objects.equals(phantomTitleText, that.phantomTitleText)
                && enabledSessionTypes.equals(that.enabledSessionTypes)
                && fishingSpotOrder == that.fishingSpotOrder
                && Objects.equals(fishingSpotPool, that.fishingSpotPool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                phantomTitleText,
                phantomTitleColorRgb,
                recentLogoutMinutes,
                restartWindowMinutes,
                returnThresholdMinutes,
                enabledSessionTypes,
                privateStoreStartCooldownMinutes,
                fishingStartCooldownMinutes,
                fishingMinDurationMinutes,
                fishingMaxDurationMinutes,
                fishingFloatDistance,
                fishingSpotOrder,
                fishingSpotPool,
                restoreBatchSize,
                restoreBatchPauseMillis,
                historyRetentionDays);
    }

    @Override
    public String toString() {
        return "AppRules[phantomTitleText=" + phantomTitleText
                + ", phantomTitleColorRgb=" + String.format("%06X", phantomTitleColorRgb)
                + ", recentLogoutMinutes=" + recentLogoutMinutes
                + ", restartWindowMinutes=" + restartWindowMinutes
                + ", returnThresholdMinutes=" + returnThresholdMinutes
                + ", enabledSessionTypes=" + enabledSessionTypes
                + ", privateStoreStartCooldownMinutes=" + privateStoreStartCooldownMinutes
                + ", fishingStartCooldownMinutes=" + fishingStartCooldownMinutes
                + ", fishingMinDurationMinutes=" + fishingMinDurationMinutes
                + ", fishingMaxDurationMinutes=" + fishingMaxDurationMinutes
                + ", fishingFloatDistance=" + fishingFloatDistance
                + ", fishingSpotOrder=" + fishingSpotOrder
                + ", fishingSpotPool=" + fishingSpotPool
                + ", restoreBatchSize=" + restoreBatchSize
                + ", restoreBatchPauseMillis=" + restoreBatchPauseMillis
                + ", historyRetentionDays=" + historyRetentionDays + "]";
    }

    /** The field initializers are the default of every key. */
    public static final class Builder {
        private @Nullable String phantomTitleText;
        private int phantomTitleColorRgb = 0xFFFF00;
        private int recentLogoutMinutes = 2;
        private int restartWindowMinutes = 15;
        private int returnThresholdMinutes = 30;
        private final Set<ActivityType> enabledSessionTypes = EnumSet.noneOf(ActivityType.class);
        private int privateStoreStartCooldownMinutes = 5;
        private int fishingStartCooldownMinutes = 15;
        private int fishingMinDurationMinutes = 30;
        private int fishingMaxDurationMinutes = 1440;
        private int fishingFloatDistance = 170;
        private SpotOrder fishingSpotOrder = SpotOrder.RANDOM;
        private @Nullable String fishingSpotPool;
        private int restoreBatchSize = 10;
        private int restoreBatchPauseMillis = 1000;
        private int historyRetentionDays = 90;

        private Builder() {}

        public Builder phantomTitleText(@Nullable String phantomTitleText) {
            this.phantomTitleText = phantomTitleText;
            return this;
        }

        public Builder phantomTitleColorRgb(int phantomTitleColorRgb) {
            this.phantomTitleColorRgb = phantomTitleColorRgb;
            return this;
        }

        public Builder recentLogoutMinutes(int recentLogoutMinutes) {
            this.recentLogoutMinutes = recentLogoutMinutes;
            return this;
        }

        public Builder restartWindowMinutes(int restartWindowMinutes) {
            this.restartWindowMinutes = restartWindowMinutes;
            return this;
        }

        public Builder returnThresholdMinutes(int returnThresholdMinutes) {
            this.returnThresholdMinutes = returnThresholdMinutes;
            return this;
        }

        /**
         * @throws IllegalArgumentException the activity is not in {@link #SESSION_TYPES}: the settings file has no
         *     {@code app.<type>.enabled} key for it
         */
        public Builder sessionTypeEnabled(ActivityType activity, boolean enabled) {
            Objects.requireNonNull(activity, "activity");
            if (!SESSION_TYPES.contains(activity)) {
                throw new IllegalArgumentException("AppRules.sessionTypeEnabled: " + activity
                        + " has no app.<type>.enabled key, expected one of " + SESSION_TYPES);
            }
            if (enabled) {
                enabledSessionTypes.add(activity);
            } else {
                enabledSessionTypes.remove(activity);
            }
            return this;
        }

        public Builder privateStoreStartCooldownMinutes(int privateStoreStartCooldownMinutes) {
            this.privateStoreStartCooldownMinutes = privateStoreStartCooldownMinutes;
            return this;
        }

        public Builder fishingStartCooldownMinutes(int fishingStartCooldownMinutes) {
            this.fishingStartCooldownMinutes = fishingStartCooldownMinutes;
            return this;
        }

        public Builder fishingMinDurationMinutes(int fishingMinDurationMinutes) {
            this.fishingMinDurationMinutes = fishingMinDurationMinutes;
            return this;
        }

        public Builder fishingMaxDurationMinutes(int fishingMaxDurationMinutes) {
            this.fishingMaxDurationMinutes = fishingMaxDurationMinutes;
            return this;
        }

        public Builder fishingFloatDistance(int fishingFloatDistance) {
            this.fishingFloatDistance = fishingFloatDistance;
            return this;
        }

        public Builder fishingSpotOrder(SpotOrder fishingSpotOrder) {
            this.fishingSpotOrder = fishingSpotOrder;
            return this;
        }

        public Builder fishingSpotPool(@Nullable String fishingSpotPool) {
            this.fishingSpotPool = fishingSpotPool;
            return this;
        }

        public Builder restoreBatchSize(int restoreBatchSize) {
            this.restoreBatchSize = restoreBatchSize;
            return this;
        }

        public Builder restoreBatchPauseMillis(int restoreBatchPauseMillis) {
            this.restoreBatchPauseMillis = restoreBatchPauseMillis;
            return this;
        }

        public Builder historyRetentionDays(int historyRetentionDays) {
            this.historyRetentionDays = historyRetentionDays;
            return this;
        }

        /** @throws IllegalArgumentException a value outside its range or a broken cross-key rule */
        public AppRules build() {
            return new AppRules(this);
        }
    }

    /** An inclusive range of an int key; {@link #max()} {@link Integer#MAX_VALUE} means no upper bound. */
    public static final class Range {
        private final int min;
        private final int max;

        private Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        static Range between(int min, int max) {
            return new Range(min, max);
        }

        static Range atLeast(int min) {
            return new Range(min, Integer.MAX_VALUE);
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public boolean bounded() {
            return max != Integer.MAX_VALUE;
        }

        public boolean contains(int value) {
            return value >= min && value <= max;
        }

        @Override
        public String toString() {
            return bounded() ? min + ".." + max : ">= " + min;
        }

        void check(String property, int value) {
            if (!contains(value)) {
                throw new IllegalArgumentException("AppRules." + property + " out of range " + this + ": " + value);
            }
        }
    }
}
