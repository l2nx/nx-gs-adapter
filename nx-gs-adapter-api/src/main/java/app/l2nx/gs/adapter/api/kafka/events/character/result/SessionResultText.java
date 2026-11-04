package app.l2nx.gs.adapter.api.kafka.events.character.result;

import org.jspecify.annotations.Nullable;

final class SessionResultText {

    private SessionResultText() {}

    static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
