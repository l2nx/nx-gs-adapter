package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/** Known {@link DroppedLine#getReason()} tokens; the wire token is {@link #name()}. */
public enum DroppedLineReason {
    NOT_FOUND,
    NOT_TRADEABLE,
    ITEM_BLOCKED,
    EQUIPPED,
    BAD_COUNT,
    PRICE_OVERFLOW,
    ADENA,
    PET_CONTROL_ITEM,
    ENCHANTING,
    SKILL_CONSUMABLE,
    PET_COLLAR_NOT_EMPTY,

    /** Fallback for a line the host's native availability checks refuse without a finer code. */
    NOT_AVAILABLE;

    public static @Nullable DroppedLineReason fromToken(@Nullable String token) {
        return Vocabularies.fromToken(DroppedLineReason.class, token);
    }
}
