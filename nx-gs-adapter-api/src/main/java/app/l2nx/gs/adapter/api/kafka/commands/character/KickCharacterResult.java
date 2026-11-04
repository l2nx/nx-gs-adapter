package app.l2nx.gs.adapter.api.kafka.commands.character;

import java.util.Objects;

public final class KickCharacterResult {

    private final Long characterId;
    private final boolean offlineTrader;

    public KickCharacterResult(Long characterId, boolean offlineTrader) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        this.characterId = characterId;
        this.offlineTrader = offlineTrader;
    }

    public Long getCharacterId() {
        return characterId;
    }

    /** {@code true} when the target was an offline trader: store ended, {@code closeClient} had no client to act on. */
    public boolean isOfflineTrader() {
        return offlineTrader;
    }

    public Builder toBuilder() {
        return new Builder().characterId(characterId).offlineTrader(offlineTrader);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KickCharacterResult)) return false;
        KickCharacterResult that = (KickCharacterResult) o;
        return offlineTrader == that.offlineTrader && Objects.equals(characterId, that.characterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, offlineTrader);
    }

    @Override
    public String toString() {
        return "KickCharacterResult[characterId=" + characterId + ", offlineTrader=" + offlineTrader + "]";
    }

    public static final class Builder {
        private Long characterId;
        private boolean offlineTrader;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder offlineTrader(boolean offlineTrader) {
            this.offlineTrader = offlineTrader;
            return this;
        }

        public KickCharacterResult build() {
            return new KickCharacterResult(characterId, offlineTrader);
        }
    }
}
