package app.l2nx.gs.adapter.api.kafka.commands.character;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class SetCharacterAccessLevelResult {

    private final Long characterId;
    private final String accessLevel;
    private final @Nullable String previousAccessLevel;
    private final boolean wasOnline;

    public SetCharacterAccessLevelResult(
            Long characterId, String accessLevel, @Nullable String previousAccessLevel, boolean wasOnline) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        if (accessLevel == null) {
            throw new IllegalArgumentException("accessLevel is required");
        }
        this.characterId = characterId;
        this.accessLevel = accessLevel;
        this.previousAccessLevel = previousAccessLevel;
        this.wasOnline = wasOnline;
    }

    public Long getCharacterId() {
        return characterId;
    }

    /** As stored by the host; an int build echoes the canonical numeric text. */
    public String getAccessLevel() {
        return accessLevel;
    }

    /** Level before the write; {@code null} when the host could not read it. */
    public @Nullable String getPreviousAccessLevel() {
        return previousAccessLevel;
    }

    /** {@code true} when applied to a live session (full effect on next login, as in-game); {@code false} for the offline row. */
    public boolean isWasOnline() {
        return wasOnline;
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .accessLevel(accessLevel)
                .previousAccessLevel(previousAccessLevel)
                .wasOnline(wasOnline);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetCharacterAccessLevelResult)) return false;
        SetCharacterAccessLevelResult that = (SetCharacterAccessLevelResult) o;
        return wasOnline == that.wasOnline
                && Objects.equals(characterId, that.characterId)
                && Objects.equals(accessLevel, that.accessLevel)
                && Objects.equals(previousAccessLevel, that.previousAccessLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, accessLevel, previousAccessLevel, wasOnline);
    }

    @Override
    public String toString() {
        return "SetCharacterAccessLevelResult[characterId=" + characterId + ", accessLevel=" + accessLevel
                + ", previousAccessLevel=" + previousAccessLevel + ", wasOnline=" + wasOnline + "]";
    }

    public static final class Builder {
        private Long characterId;
        private String accessLevel;
        private @Nullable String previousAccessLevel;
        private boolean wasOnline;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder accessLevel(String accessLevel) {
            this.accessLevel = accessLevel;
            return this;
        }

        public Builder previousAccessLevel(@Nullable String previousAccessLevel) {
            this.previousAccessLevel = previousAccessLevel;
            return this;
        }

        public Builder wasOnline(boolean wasOnline) {
            this.wasOnline = wasOnline;
            return this;
        }

        public SetCharacterAccessLevelResult build() {
            return new SetCharacterAccessLevelResult(characterId, accessLevel, previousAccessLevel, wasOnline);
        }
    }
}
