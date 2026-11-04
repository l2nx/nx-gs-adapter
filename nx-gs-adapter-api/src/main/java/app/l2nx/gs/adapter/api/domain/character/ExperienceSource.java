package app.l2nx.gs.adapter.api.domain.character;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Why the experience or SP of a character changed during a session; the wire value is the constant name. A closed
 * vocabulary, read leniently through {@link #fromToken}: an unknown token is {@code null}, logged and kept. Never
 * {@code null} on the wire. The sign of the value applies to experience and SP alike.
 */
public enum ExperienceSource {

    /** Kill of an NPC ({@code +}). */
    KILL,

    /** Quest reward ({@code +}). */
    QUEST,

    /** Reward of a game system with no other source ({@code +}). */
    REWARD,

    /** Lost on death ({@code -}). */
    DEATH_PENALTY,

    /** Spent: skill learning or enchanting, a clan spend, a multisell price ({@code -}). */
    SPEND,

    /** Returned by a resurrection ({@code +}). */
    RESURRECT,

    /** Changed by staff in the game. */
    ADMIN,

    /** Changed by a platform command during the session. */
    PLATFORM;

    public static @Nullable ExperienceSource fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ExperienceSource.class, token);
    }
}
