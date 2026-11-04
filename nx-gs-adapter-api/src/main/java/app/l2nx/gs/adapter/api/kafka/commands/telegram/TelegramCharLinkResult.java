package app.l2nx.gs.adapter.api.kafka.commands.telegram;

import java.util.Objects;

/**
 * Resolved character's primary key. The platform persists a tentative binding immediately, before the user types the
 * code back into the bot.
 */
public final class TelegramCharLinkResult {

    private final Long characterId;

    public TelegramCharLinkResult(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        this.characterId = characterId;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public Builder toBuilder() {
        return new Builder().characterId(characterId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TelegramCharLinkResult)) return false;
        TelegramCharLinkResult that = (TelegramCharLinkResult) o;
        return Objects.equals(characterId, that.characterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId);
    }

    @Override
    public String toString() {
        return "TelegramCharLinkResult[characterId=" + characterId + "]";
    }

    public static final class Builder {
        private Long characterId;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public TelegramCharLinkResult build() {
            return new TelegramCharLinkResult(characterId);
        }
    }
}
