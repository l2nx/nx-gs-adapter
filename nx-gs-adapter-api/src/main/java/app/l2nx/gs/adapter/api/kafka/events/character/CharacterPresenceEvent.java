package app.l2nx.gs.adapter.api.kafka.events.character;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * One event per session start ({@code online=true}) or end, emitted from the standard packet path of a client session
 * or by the app-session engine. Cheat/custom clients bypassing it are covered by the CDC and runtime channels, the
 * other two sources reconciled into {@code gs_characters.online} with timestamp-based last-writer-wins; {@code eventId}
 * is a UUIDv7 so {@code occurredAt} is extracted from its prefix. {@code charId} is also the partition key.
 *
 * <p>{@code source} is required. With {@link SessionSource#APP}, {@code activity} is required, {@code sessionId} is
 * the app session id, {@code ip} is that of the app request and {@code hwid} is the character's snapshot; with
 * {@link SessionSource#CLIENT} {@code activity} must be absent.</p>
 *
 * <p>{@code resumed} is {@code true} only on an {@code online=true} APP event that restores a session after a host
 * restart.</p>
 *
 * <p>{@code logoutReason} is required on every {@code online=false} event and forbidden on {@code online=true}.</p>
 *
 * <p>{@code sessionId}: a start and its matching end carry the SAME id, fresh per session; {@code null} on builds that
 * do not emit it, in which case the platform leaves the session's end time unset. {@code hwid} only on cores with HWID
 * tracking. {@code metadata}: open map, {@code null} when absent; consumers ignore unknown keys.</p>
 *
 * <p>Gson and Jackson may bypass the constructor, so a consumer re-checks what it depends on.</p>
 */
public final class CharacterPresenceEvent {

    private final UUID eventId;
    private final long charId;
    private final boolean online;
    private final SessionSource source;
    private final @Nullable ActivityType activity;
    private final boolean resumed;
    private final @Nullable LogoutReason logoutReason;
    private final @Nullable UUID sessionId;
    private final @Nullable String accountName;
    private final @Nullable String ip;
    private final @Nullable String hwid;
    private final @Nullable Map<String, String> metadata;

    public CharacterPresenceEvent(
            UUID eventId,
            long charId,
            boolean online,
            SessionSource source,
            @Nullable ActivityType activity,
            boolean resumed,
            @Nullable LogoutReason logoutReason,
            @Nullable UUID sessionId,
            @Nullable String accountName,
            @Nullable String ip,
            @Nullable String hwid,
            @Nullable Map<String, String> metadata) {
        this.eventId = Objects.requireNonNull(eventId, "CharacterPresenceEvent.eventId is required");
        this.source = Objects.requireNonNull(source, "CharacterPresenceEvent.source is required");
        if (source == SessionSource.APP && activity == null) {
            throw new IllegalArgumentException("CharacterPresenceEvent.activity is required when source is APP");
        }
        if (source == SessionSource.CLIENT && activity != null) {
            throw new IllegalArgumentException("CharacterPresenceEvent.activity is only allowed when source is APP");
        }
        if (resumed && !(online && source == SessionSource.APP)) {
            throw new IllegalArgumentException(
                    "CharacterPresenceEvent.resumed is only allowed on an online=true event with source APP");
        }
        if (!online && logoutReason == null) {
            throw new IllegalArgumentException("CharacterPresenceEvent.logoutReason is required when online is false");
        }
        if (online && logoutReason != null) {
            throw new IllegalArgumentException(
                    "CharacterPresenceEvent.logoutReason is only allowed when online is false");
        }
        this.charId = charId;
        this.online = online;
        this.activity = activity;
        this.resumed = resumed;
        this.logoutReason = logoutReason;
        this.sessionId = sessionId;
        this.accountName = accountName;
        this.ip = ip;
        this.hwid = hwid;
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getCharId() {
        return charId;
    }

    public boolean isOnline() {
        return online;
    }

    public SessionSource getSource() {
        return source;
    }

    public @Nullable ActivityType getActivity() {
        return activity;
    }

    public boolean isResumed() {
        return resumed;
    }

    public @Nullable LogoutReason getLogoutReason() {
        return logoutReason;
    }

    public @Nullable UUID getSessionId() {
        return sessionId;
    }

    public @Nullable String getAccountName() {
        return accountName;
    }

    public @Nullable String getIp() {
        return ip;
    }

    public @Nullable String getHwid() {
        return hwid;
    }

    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public Builder toBuilder() {
        return new Builder()
                .eventId(eventId)
                .charId(charId)
                .online(online)
                .source(source)
                .activity(activity)
                .resumed(resumed)
                .logoutReason(logoutReason)
                .sessionId(sessionId)
                .accountName(accountName)
                .ip(ip)
                .hwid(hwid)
                .metadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CharacterPresenceEvent)) return false;
        CharacterPresenceEvent that = (CharacterPresenceEvent) o;
        return charId == that.charId
                && online == that.online
                && resumed == that.resumed
                && eventId.equals(that.eventId)
                && source == that.source
                && activity == that.activity
                && logoutReason == that.logoutReason
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(accountName, that.accountName)
                && Objects.equals(ip, that.ip)
                && Objects.equals(hwid, that.hwid)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                eventId,
                charId,
                online,
                source,
                activity,
                resumed,
                logoutReason,
                sessionId,
                accountName,
                ip,
                hwid,
                metadata);
    }

    @Override
    public String toString() {
        return "CharacterPresenceEvent[eventId=" + eventId
                + ", charId=" + charId
                + ", online=" + online
                + ", source=" + source
                + ", activity=" + activity
                + ", resumed=" + resumed
                + ", logoutReason=" + logoutReason
                + ", sessionId=" + sessionId
                + ", accountName=" + accountName
                + ", ip=" + ip
                + ", hwid=" + hwid
                + ", metadata=" + metadata + "]";
    }

    public static final class Builder {
        private @Nullable UUID eventId;
        private long charId;
        private boolean online;
        private @Nullable SessionSource source;
        private @Nullable ActivityType activity;
        private boolean resumed;
        private @Nullable LogoutReason logoutReason;
        private @Nullable UUID sessionId;
        private @Nullable String accountName;
        private @Nullable String ip;
        private @Nullable String hwid;
        private @Nullable Map<String, String> metadata;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder charId(long charId) {
            this.charId = charId;
            return this;
        }

        public Builder online(boolean online) {
            this.online = online;
            return this;
        }

        public Builder source(SessionSource source) {
            this.source = source;
            return this;
        }

        public Builder activity(@Nullable ActivityType activity) {
            this.activity = activity;
            return this;
        }

        public Builder resumed(boolean resumed) {
            this.resumed = resumed;
            return this;
        }

        public Builder logoutReason(@Nullable LogoutReason logoutReason) {
            this.logoutReason = logoutReason;
            return this;
        }

        public Builder sessionId(@Nullable UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder accountName(@Nullable String accountName) {
            this.accountName = accountName;
            return this;
        }

        public Builder ip(@Nullable String ip) {
            this.ip = ip;
            return this;
        }

        public Builder hwid(@Nullable String hwid) {
            this.hwid = hwid;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public CharacterPresenceEvent build() {
            return new CharacterPresenceEvent(
                    eventId,
                    charId,
                    online,
                    source,
                    activity,
                    resumed,
                    logoutReason,
                    sessionId,
                    accountName,
                    ip,
                    hwid,
                    metadata);
        }
    }
}
