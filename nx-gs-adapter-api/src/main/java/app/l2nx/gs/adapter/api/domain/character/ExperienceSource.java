package app.l2nx.gs.adapter.api.domain.character;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Why a character's experience or SP changed during a session; the wire value is the constant name. Read leniently
 * through {@link #fromToken}: an unknown token is {@code null}, logged and kept.
 */
public enum ExperienceSource {
    KILL,

    QUEST,

    REWARD,

    DEATH_PENALTY,

    SPEND,

    RESURRECT,

    ADMIN,

    PLATFORM;

    public static @Nullable ExperienceSource fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ExperienceSource.class, token);
    }
}
