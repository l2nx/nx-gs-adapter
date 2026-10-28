package app.l2nx.gs.adapter.api.kafka.sync.settings.app;

import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * App-session settings of one server, payload of {@link SettingsSyncEvent} with entity name {@value #ENTITY_NAME}.
 * The host decides every rule; this snapshot only lets the platform show them.
 */
public final class AppSettings {

    public static final String ENTITY_NAME = "appsettings";

    private final List<AppSessionTypeSettings> sessionTypes;
    private final int recentlyLeftGameMinutes;
    private final int fishingStartCooldownMinutes;
    private final int privateStoreStartCooldownMinutes;
    private final int returnThresholdMinutes;
    private final int restartWindowMinutes;
    private final long fishingMinDurationMs;
    private final long fishingMaxDurationMs;
    private final @Nullable ReviveSettings revive;
    private final @Nullable ChatSettings chat;
    private final Instant updatedAt;

    public AppSettings(
            @Nullable List<AppSessionTypeSettings> sessionTypes,
            int recentlyLeftGameMinutes,
            int fishingStartCooldownMinutes,
            int privateStoreStartCooldownMinutes,
            int returnThresholdMinutes,
            int restartWindowMinutes,
            long fishingMinDurationMs,
            long fishingMaxDurationMs,
            @Nullable ReviveSettings revive,
            @Nullable ChatSettings chat,
            Instant updatedAt) {
        if (recentlyLeftGameMinutes < 0
                || fishingStartCooldownMinutes < 0
                || privateStoreStartCooldownMinutes < 0
                || returnThresholdMinutes < 0
                || restartWindowMinutes < 0) {
            throw new IllegalArgumentException("AppSettings minute values must not be negative");
        }
        if (fishingMinDurationMs < 0 || fishingMaxDurationMs < fishingMinDurationMs) {
            throw new IllegalArgumentException(
                    "AppSettings fishing durations must satisfy 0 <= fishingMinDurationMs <= fishingMaxDurationMs");
        }
        this.sessionTypes = sessionTypes == null
                ? Collections.<AppSessionTypeSettings>emptyList()
                : Collections.unmodifiableList(new ArrayList<AppSessionTypeSettings>(sessionTypes));
        this.recentlyLeftGameMinutes = recentlyLeftGameMinutes;
        this.fishingStartCooldownMinutes = fishingStartCooldownMinutes;
        this.privateStoreStartCooldownMinutes = privateStoreStartCooldownMinutes;
        this.returnThresholdMinutes = returnThresholdMinutes;
        this.restartWindowMinutes = restartWindowMinutes;
        this.fishingMinDurationMs = fishingMinDurationMs;
        this.fishingMaxDurationMs = fishingMaxDurationMs;
        this.revive = revive;
        this.chat = chat;
        this.updatedAt = Objects.requireNonNull(updatedAt, "AppSettings.updatedAt is required");
    }

    /** One entry per session type the host knows; a type that is absent is not available on this server. */
    public List<AppSessionTypeSettings> getSessionTypes() {
        return sessionTypes == null ? Collections.<AppSessionTypeSettings>emptyList() : sessionTypes;
    }

    /** A start is refused for this long after the character left with a client. */
    public int getRecentlyLeftGameMinutes() {
        return recentlyLeftGameMinutes;
    }

    /** Minimum time between the real starts of two app fishing sessions of one master account. */
    public int getFishingStartCooldownMinutes() {
        return fishingStartCooldownMinutes;
    }

    /** Minimum time between the real starts of two app private-store sessions of one character; {@code 0}: no rule. */
    public int getPrivateStoreStartCooldownMinutes() {
        return privateStoreStartCooldownMinutes;
    }

    /** A session active for less than this when it ended returns the character to where it started. */
    public int getReturnThresholdMinutes() {
        return returnThresholdMinutes;
    }

    /** A start is refused when a scheduled restart is closer than this. */
    public int getRestartWindowMinutes() {
        return restartWindowMinutes;
    }

    public long getFishingMinDurationMs() {
        return fishingMinDurationMs;
    }

    public long getFishingMaxDurationMs() {
        return fishingMaxDurationMs;
    }

    /**
     * {@code null}: the host runs an adapter older than this group and knows no revive command; the platform offers no
     * revive for the server.
     */
    public @Nullable ReviveSettings getRevive() {
        return revive;
    }

    /**
     * {@code null}: the host runs an adapter older than this group, which knows no chat settings; such a host
     * publishes game chat and accepts sends from the app on its built-in channels.
     */
    public @Nullable ChatSettings getChat() {
        return chat;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Builder toBuilder() {
        return new Builder()
                .sessionTypes(sessionTypes)
                .recentlyLeftGameMinutes(recentlyLeftGameMinutes)
                .fishingStartCooldownMinutes(fishingStartCooldownMinutes)
                .privateStoreStartCooldownMinutes(privateStoreStartCooldownMinutes)
                .returnThresholdMinutes(returnThresholdMinutes)
                .restartWindowMinutes(restartWindowMinutes)
                .fishingMinDurationMs(fishingMinDurationMs)
                .fishingMaxDurationMs(fishingMaxDurationMs)
                .revive(revive)
                .chat(chat)
                .updatedAt(updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSettings)) return false;
        AppSettings that = (AppSettings) o;
        return recentlyLeftGameMinutes == that.recentlyLeftGameMinutes
                && fishingStartCooldownMinutes == that.fishingStartCooldownMinutes
                && privateStoreStartCooldownMinutes == that.privateStoreStartCooldownMinutes
                && returnThresholdMinutes == that.returnThresholdMinutes
                && restartWindowMinutes == that.restartWindowMinutes
                && fishingMinDurationMs == that.fishingMinDurationMs
                && fishingMaxDurationMs == that.fishingMaxDurationMs
                && Objects.equals(sessionTypes, that.sessionTypes)
                && Objects.equals(revive, that.revive)
                && Objects.equals(chat, that.chat)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sessionTypes,
                recentlyLeftGameMinutes,
                fishingStartCooldownMinutes,
                privateStoreStartCooldownMinutes,
                returnThresholdMinutes,
                restartWindowMinutes,
                fishingMinDurationMs,
                fishingMaxDurationMs,
                revive,
                chat,
                updatedAt);
    }

    @Override
    public String toString() {
        return "AppSettings[sessionTypes=" + sessionTypes
                + ", recentlyLeftGameMinutes=" + recentlyLeftGameMinutes
                + ", fishingStartCooldownMinutes=" + fishingStartCooldownMinutes
                + ", privateStoreStartCooldownMinutes=" + privateStoreStartCooldownMinutes
                + ", returnThresholdMinutes=" + returnThresholdMinutes
                + ", restartWindowMinutes=" + restartWindowMinutes
                + ", fishingMinDurationMs=" + fishingMinDurationMs
                + ", fishingMaxDurationMs=" + fishingMaxDurationMs
                + ", revive=" + revive
                + ", chat=" + chat
                + ", updatedAt=" + updatedAt + "]";
    }

    public static final class Builder {
        private @Nullable List<AppSessionTypeSettings> sessionTypes;
        private int recentlyLeftGameMinutes;
        private int fishingStartCooldownMinutes;
        private int privateStoreStartCooldownMinutes;
        private int returnThresholdMinutes;
        private int restartWindowMinutes;
        private long fishingMinDurationMs;
        private long fishingMaxDurationMs;
        private @Nullable ReviveSettings revive;
        private @Nullable ChatSettings chat;
        private @Nullable Instant updatedAt;

        public Builder sessionTypes(@Nullable List<AppSessionTypeSettings> sessionTypes) {
            this.sessionTypes = sessionTypes;
            return this;
        }

        public Builder recentlyLeftGameMinutes(int recentlyLeftGameMinutes) {
            this.recentlyLeftGameMinutes = recentlyLeftGameMinutes;
            return this;
        }

        public Builder fishingStartCooldownMinutes(int fishingStartCooldownMinutes) {
            this.fishingStartCooldownMinutes = fishingStartCooldownMinutes;
            return this;
        }

        public Builder privateStoreStartCooldownMinutes(int privateStoreStartCooldownMinutes) {
            this.privateStoreStartCooldownMinutes = privateStoreStartCooldownMinutes;
            return this;
        }

        public Builder returnThresholdMinutes(int returnThresholdMinutes) {
            this.returnThresholdMinutes = returnThresholdMinutes;
            return this;
        }

        public Builder restartWindowMinutes(int restartWindowMinutes) {
            this.restartWindowMinutes = restartWindowMinutes;
            return this;
        }

        public Builder fishingMinDurationMs(long fishingMinDurationMs) {
            this.fishingMinDurationMs = fishingMinDurationMs;
            return this;
        }

        public Builder fishingMaxDurationMs(long fishingMaxDurationMs) {
            this.fishingMaxDurationMs = fishingMaxDurationMs;
            return this;
        }

        public Builder revive(@Nullable ReviveSettings revive) {
            this.revive = revive;
            return this;
        }

        public Builder chat(@Nullable ChatSettings chat) {
            this.chat = chat;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public AppSettings build() {
            return new AppSettings(
                    sessionTypes,
                    recentlyLeftGameMinutes,
                    fishingStartCooldownMinutes,
                    privateStoreStartCooldownMinutes,
                    returnThresholdMinutes,
                    restartWindowMinutes,
                    fishingMinDurationMs,
                    fishingMaxDurationMs,
                    revive,
                    chat,
                    updatedAt);
        }
    }
}
