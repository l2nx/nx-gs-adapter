package app.l2nx.gs.adapter.api.kafka.commands.character;

import java.util.Objects;

public final class TransferCharToAccountResult {

    private final Long characterId;
    private final String newAccountName;
    private final boolean wasLoggedOut;

    public TransferCharToAccountResult(Long characterId, String newAccountName, boolean wasLoggedOut) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }
        if (newAccountName == null) {
            throw new IllegalArgumentException("newAccountName is required");
        }
        this.characterId = characterId;
        this.newAccountName = newAccountName;
        this.wasLoggedOut = wasLoggedOut;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public String getNewAccountName() {
        return newAccountName;
    }

    /** {@code true} when the character was online and the host force-logged it out before the rebind. */
    public boolean isWasLoggedOut() {
        return wasLoggedOut;
    }

    public Builder toBuilder() {
        return new Builder().characterId(characterId).newAccountName(newAccountName).wasLoggedOut(wasLoggedOut);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferCharToAccountResult)) return false;
        TransferCharToAccountResult that = (TransferCharToAccountResult) o;
        return wasLoggedOut == that.wasLoggedOut
                && Objects.equals(characterId, that.characterId)
                && Objects.equals(newAccountName, that.newAccountName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, newAccountName, wasLoggedOut);
    }

    @Override
    public String toString() {
        return "TransferCharToAccountResult[characterId=" + characterId
                + ", newAccountName=" + newAccountName
                + ", wasLoggedOut=" + wasLoggedOut + "]";
    }

    public static final class Builder {
        private Long characterId;
        private String newAccountName;
        private boolean wasLoggedOut;

        public Builder characterId(Long characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder newAccountName(String newAccountName) {
            this.newAccountName = newAccountName;
            return this;
        }

        public Builder wasLoggedOut(boolean wasLoggedOut) {
            this.wasLoggedOut = wasLoggedOut;
            return this;
        }

        public TransferCharToAccountResult build() {
            return new TransferCharToAccountResult(characterId, newAccountName, wasLoggedOut);
        }
    }
}
