package app.l2nx.gs.adapter.api.kafka.commands.character.revive.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Open vocabulary: the command carries the token as a string, so a host that does not know a newer point refuses it
 * with {@code RESTART_POINT_UNSUPPORTED} instead of failing to read the command.
 */
public enum RestartPoint {
    TOWN;

    /** {@code null} for a token this api does not know. */
    public static @Nullable RestartPoint fromToken(@Nullable String token) {
        return Vocabularies.fromToken(RestartPoint.class, token);
    }
}
