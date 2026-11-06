package app.l2nx.gs.adapter.api.kafka.commands.character;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Sets a character's access level to an absolute value, mirroring the in-game {@code //changelvl}; re-delivery
 * converges on the same state.
 *
 * <p>Reply: {@code CommandResult<SetCharacterAccessLevelResult>}. Errors: {@code NOT_FOUND}, {@code INVALID_STATE}
 * (a login raced the offline write), {@code VALIDATION_FAILED} ({@code characterId} / {@code accessLevel} missing,
 * non-integer, negative - bans go through {@code BanCommand} - or unregistered), {@code FORBIDDEN} (above the
 * host's platform-grantable ceiling), {@code UNAVAILABLE} (DB error on the offline path), {@code INTERNAL_ERROR}.</p>
 *
 * <p>Routed by {@code characterId}.</p>
 */
public final class SetCharacterAccessLevelCommand implements NxCommand<SetCharacterAccessLevelResult> {

    private final Long characterId;
    private final String accessLevel;
    private final @Nullable String staffNotes;
    private final CommandInitiator initiator;

    public SetCharacterAccessLevelCommand(
            Long characterId, String accessLevel, @Nullable String staffNotes, CommandInitiator initiator) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        if (accessLevel == null) {
            throw new IllegalArgumentException("accessLevel is required");
        }
        this.characterId = characterId;
        this.accessLevel = accessLevel;
        this.staffNotes = staffNotes;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    public Long getCharacterId() {
        return characterId;
    }

    /**
     * Opaque, same vocabulary as {@code CharacterDbDto.accessLevel}: numeric text on int builds ({@code "7"}), a role
     * name on string-role builds. The host converts, the platform never interprets.
     */
    public String getAccessLevel() {
        return accessLevel;
    }

    /** Staff-only note: never shown in-game, logged by the host and surfaced on the platform command audit. */
    public @Nullable String getStaffNotes() {
        return staffNotes;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(characterId)
                .accessLevel(accessLevel)
                .staffNotes(staffNotes)
                .initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetCharacterAccessLevelCommand)) return false;
        SetCharacterAccessLevelCommand that = (SetCharacterAccessLevelCommand) o;
        return Objects.equals(characterId, that.characterId)
                && Objects.equals(accessLevel, that.accessLevel)
                && Objects.equals(staffNotes, that.staffNotes)
                && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, accessLevel, staffNotes, initiator);
    }

    @Override
    public String toString() {
        return "SetCharacterAccessLevelCommand[characterId=" + characterId + ", accessLevel=" + accessLevel + ", staffNotes="
                + staffNotes + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private Long characterId;
        private String accessLevel;
        private @Nullable String staffNotes;
        private CommandInitiator initiator;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder accessLevel(String accessLevel) {
            this.accessLevel = accessLevel;
            return this;
        }

        public Builder staffNotes(@Nullable String staffNotes) {
            this.staffNotes = staffNotes;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public SetCharacterAccessLevelCommand build() {
            return new SetCharacterAccessLevelCommand(characterId, accessLevel, staffNotes, initiator);
        }
    }
}
