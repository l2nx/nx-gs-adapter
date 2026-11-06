package app.l2nx.gs.adapter.api.kafka.commands.character;

import java.util.Objects;

/** Post-upsert state of the one lock the command named, so the platform needs no follow-up read. */
public final class UpsertCharacterLockResult {

    private final Long characterId;
    private final CharacterLockState lock;

    public UpsertCharacterLockResult(Long characterId, CharacterLockState lock) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        if (lock == null) {
            throw new IllegalArgumentException("lock is required");
        }
        this.characterId = characterId;
        this.lock = lock;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public CharacterLockState getLock() {
        return lock;
    }

    public Builder toBuilder() {
        return new Builder().characterId(characterId).lock(lock);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpsertCharacterLockResult)) return false;
        UpsertCharacterLockResult that = (UpsertCharacterLockResult) o;
        return Objects.equals(characterId, that.characterId) && Objects.equals(lock, that.lock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, lock);
    }

    @Override
    public String toString() {
        return "UpsertCharacterLockResult[characterId=" + characterId + ", lock=" + lock + "]";
    }

    public static final class Builder {
        private Long characterId;
        private CharacterLockState lock;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder lock(CharacterLockState lock) {
            this.lock = lock;
            return this;
        }

        public UpsertCharacterLockResult build() {
            return new UpsertCharacterLockResult(characterId, lock);
        }
    }
}
