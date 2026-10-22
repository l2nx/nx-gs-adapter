package app.l2nx.gs.adapter.api.kafka.events.character.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

public enum SessionSource {
    CLIENT,

    /** Loaded by an app request, without a client. */
    APP;

    public static @Nullable SessionSource fromToken(@Nullable String token) {
        return Vocabularies.fromToken(SessionSource.class, token);
    }
}
