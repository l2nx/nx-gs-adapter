package app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage;

import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.ActivityUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The activity usage of one character as one sync row: {@code characterId} is the row key and
 * {@link #getUsages() usages} is the COMPLETE set of that character's rows, which a consumer replaces wholesale.
 *
 * <p>Rides the existing character topics of the db and runtime channels, routed by the entity name
 * {@value #ENTITY_NAME}.</p>
 */
public final class CharacterActivityUsagesDto {

    public static final String ENTITY_NAME = "characteractivityusage";

    private final int characterId;
    private final List<ActivityUsage> usages;

    public CharacterActivityUsagesDto(int characterId, @Nullable List<ActivityUsage> usages) {
        List<ActivityUsage> frozen = usages == null
                ? Collections.<ActivityUsage>emptyList()
                : Collections.unmodifiableList(new ArrayList<ActivityUsage>(usages));
        requireDistinctIdentities(frozen);
        this.characterId = characterId;
        this.usages = frozen;
    }

    private static void requireDistinctIdentities(List<ActivityUsage> rows) {
        Set<String> seen = new HashSet<String>();
        for (ActivityUsage row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("CharacterActivityUsagesDto.usages must not contain null");
            }
            // a null key is its own identity, distinct from every string key
            String identity = row.getType().name() + '|' + (row.getKey() == null ? "-" : "+" + row.getKey());
            if (!seen.add(identity)) {
                throw new IllegalArgumentException("CharacterActivityUsagesDto.usages holds two rows for type "
                        + row.getType() + " and key " + row.getKey());
            }
        }
    }

    public int getCharacterId() {
        return characterId;
    }

    /** At most one row per {@code (type, key)}; empty = the character has none. */
    public List<ActivityUsage> getUsages() {
        return usages == null ? Collections.<ActivityUsage>emptyList() : usages;
    }

    public Builder toBuilder() {
        return new Builder().characterId(characterId).usages(usages);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CharacterActivityUsagesDto)) return false;
        CharacterActivityUsagesDto that = (CharacterActivityUsagesDto) o;
        return characterId == that.characterId && Objects.equals(usages, that.usages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, usages);
    }

    @Override
    public String toString() {
        return "CharacterActivityUsagesDto[characterId=" + characterId + ", usages=" + usages + "]";
    }

    public static final class Builder {
        private int characterId;
        private @Nullable List<ActivityUsage> usages;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
            return this;
        }

        public Builder usages(@Nullable List<ActivityUsage> usages) {
            this.usages = usages;
            return this;
        }

        public CharacterActivityUsagesDto build() {
            return new CharacterActivityUsagesDto(characterId, usages);
        }
    }
}
