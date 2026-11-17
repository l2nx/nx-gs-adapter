package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Which id space a ban id belongs to; the spaces overlap, so source and id together identify a ban.
 */
public enum BanSource {
    /** A row of the host ban table; the id is that row's primary key, as on the {@code ban} sync entity. */
    STANDARD,

    /** A shadow chat ban, kept on the character; the id is the char id, as on the {@code shadowban} sync entity. */
    SHADOW;

    /** {@code null} for a token this api does not know. */
    public static @Nullable BanSource fromToken(@Nullable String token) {
        return Vocabularies.fromToken(BanSource.class, token);
    }
}
