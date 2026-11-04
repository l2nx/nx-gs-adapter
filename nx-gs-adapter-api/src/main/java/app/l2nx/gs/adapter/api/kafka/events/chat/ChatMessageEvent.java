package app.l2nx.gs.adapter.api.kafka.events.chat;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code eventId} is the dedup key under at-least-once delivery; {@code characterId} {@code 0} means the platform spoke.
 * {@code clanId} / {@code allianceId} are the speaker's at speaking time; without a legal sender they carry the
 * addressee's (on {@code CLAN} its clan and alliance, on {@code ALLIANCE} only the alliance).
 */
public final class ChatMessageEvent {

    private final UUID eventId;
    private final long characterId;
    private final @Nullable String characterName;
    private final String channel;
    private final String text;
    private final @Nullable Long targetCharacterId;
    private final @Nullable String targetCharacterName;
    private final @Nullable Map<String, String> metadata;
    private final @Nullable String senderDisplayName;
    private final @Nullable List<Long> recipientCharacterIds;
    private final @Nullable List<ItemSnapshot> items;
    private final @Nullable Long clanId;
    private final @Nullable Long allianceId;

    public ChatMessageEvent(
            UUID eventId,
            long characterId,
            @Nullable String characterName,
            String channel,
            String text,
            @Nullable Long targetCharacterId,
            @Nullable String targetCharacterName,
            @Nullable Map<String, String> metadata,
            @Nullable String senderDisplayName,
            @Nullable List<Long> recipientCharacterIds,
            @Nullable List<ItemSnapshot> items,
            @Nullable Long clanId,
            @Nullable Long allianceId) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.characterId = characterId;
        this.characterName = characterName;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.text = Objects.requireNonNull(text, "text");
        this.targetCharacterId = targetCharacterId;
        this.targetCharacterName = targetCharacterName;
        this.metadata =
                metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
        this.senderDisplayName = senderDisplayName;
        this.recipientCharacterIds = recipientCharacterIds == null
                ? null
                : Collections.unmodifiableList(new ArrayList<Long>(recipientCharacterIds));
        this.items = items == null ? null : Collections.unmodifiableList(new ArrayList<ItemSnapshot>(items));
        this.clanId = clanId;
        this.allianceId = allianceId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getCharacterId() {
        return characterId;
    }

    public @Nullable String getCharacterName() {
        return characterName;
    }

    /**
     * A {@link ChatChannel} name, or {@link ChatChannel#unknownToken(int) UNKNOWN_<int>} for a build's channel no
     * constant names; read it with {@link ChatChannel#fromToken(String)}, which is {@code null} for the latter.
     */
    public String getChannel() {
        return channel;
    }

    /** Game-native, with {@code ...ID=<objectId>...} item tokens. */
    public String getText() {
        return text;
    }

    public @Nullable Long getTargetCharacterId() {
        return targetCharacterId;
    }

    public @Nullable String getTargetCharacterName() {
        return targetCharacterName;
    }

    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public @Nullable String getSenderDisplayName() {
        return senderDisplayName;
    }

    /** Receivers plus the speaker; only for {@code GENERAL}, {@code PARTY} and a regional {@code SHOUT}. */
    public @Nullable List<Long> getRecipientCharacterIds() {
        return recipientCharacterIds;
    }

    public @Nullable List<ItemSnapshot> getItems() {
        return items;
    }

    public @Nullable Long getClanId() {
        return clanId;
    }

    public @Nullable Long getAllianceId() {
        return allianceId;
    }

    public Builder toBuilder() {
        return new Builder()
                .eventId(eventId)
                .characterId(characterId)
                .characterName(characterName)
                .channel(channel)
                .text(text)
                .targetCharacterId(targetCharacterId)
                .targetCharacterName(targetCharacterName)
                .metadata(metadata)
                .senderDisplayName(senderDisplayName)
                .recipientCharacterIds(recipientCharacterIds)
                .items(items)
                .clanId(clanId)
                .allianceId(allianceId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessageEvent)) return false;
        ChatMessageEvent that = (ChatMessageEvent) o;
        return characterId == that.characterId
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(characterName, that.characterName)
                && Objects.equals(channel, that.channel)
                && Objects.equals(text, that.text)
                && Objects.equals(targetCharacterId, that.targetCharacterId)
                && Objects.equals(targetCharacterName, that.targetCharacterName)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(senderDisplayName, that.senderDisplayName)
                && Objects.equals(recipientCharacterIds, that.recipientCharacterIds)
                && Objects.equals(items, that.items)
                && Objects.equals(clanId, that.clanId)
                && Objects.equals(allianceId, that.allianceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                eventId,
                characterId,
                characterName,
                channel,
                text,
                targetCharacterId,
                targetCharacterName,
                metadata,
                senderDisplayName,
                recipientCharacterIds,
                items,
                clanId,
                allianceId);
    }

    @Override
    public String toString() {
        return "ChatMessageEvent[eventId=" + eventId
                + ", characterId=" + characterId
                + ", characterName=" + characterName
                + ", channel=" + channel
                + ", text=" + text
                + ", targetCharacterId=" + targetCharacterId
                + ", targetCharacterName=" + targetCharacterName
                + ", metadata=" + metadata
                + ", senderDisplayName=" + senderDisplayName
                + ", recipientCharacterIds=" + recipientCharacterIds
                + ", items=" + items
                + ", clanId=" + clanId
                + ", allianceId=" + allianceId + "]";
    }

    public static final class Builder {
        private UUID eventId;
        private long characterId;
        private @Nullable String characterName;
        private String channel;
        private String text;
        private @Nullable Long targetCharacterId;
        private @Nullable String targetCharacterName;
        private @Nullable Map<String, String> metadata;
        private @Nullable String senderDisplayName;
        private @Nullable List<Long> recipientCharacterIds;
        private @Nullable List<ItemSnapshot> items;
        private @Nullable Long clanId;
        private @Nullable Long allianceId;

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder characterId(long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder characterName(@Nullable String characterName) {
            this.characterName = characterName;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder channel(ChatChannel channel) {
            this.channel = channel.name();
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder targetCharacterId(@Nullable Long targetCharacterId) {
            this.targetCharacterId = targetCharacterId;
            return this;
        }

        public Builder targetCharacterName(@Nullable String targetCharacterName) {
            this.targetCharacterName = targetCharacterName;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder senderDisplayName(@Nullable String senderDisplayName) {
            this.senderDisplayName = senderDisplayName;
            return this;
        }

        public Builder recipientCharacterIds(@Nullable List<Long> recipientCharacterIds) {
            this.recipientCharacterIds = recipientCharacterIds;
            return this;
        }

        public Builder items(@Nullable List<ItemSnapshot> items) {
            this.items = items;
            return this;
        }

        public Builder clanId(@Nullable Long clanId) {
            this.clanId = clanId;
            return this;
        }

        public Builder allianceId(@Nullable Long allianceId) {
            this.allianceId = allianceId;
            return this;
        }

        public ChatMessageEvent build() {
            return new ChatMessageEvent(
                    eventId,
                    characterId,
                    characterName,
                    channel,
                    text,
                    targetCharacterId,
                    targetCharacterName,
                    metadata,
                    senderDisplayName,
                    recipientCharacterIds,
                    items,
                    clanId,
                    allianceId);
        }
    }
}
