package app.l2nx.gs.adapter.api.domain.character;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Keys of the activity-usage row {@code metadata} map; the wire string is {@link #key()}. Open vocabulary: hosts may
 * add keys, consumers ignore unknown ones. Every value is a string; ids are decimal strings.
 */
public enum ActivityMetadataKey implements Vocabularies.Keyed {

    /** ISO-8601 duration the current run has been active; on any running row. */
    ELAPSED("elapsed"),

    /**
     * ISO-8601 duration left of the planned run (an app session: planned minus active time); on a running row that has
     * a plan.
     */
    REMAINING("remaining"),

    /** Inventory instance id of the session's rod; {@link ActivityType#OFFLINE_FISHING} rows. */
    ROD_ITEM_ID("rodItemId"),

    /** Inventory instance id of the session's bait stack; {@link ActivityType#OFFLINE_FISHING} rows. */
    BAIT_ITEM_ID("baitItemId"),

    /**
     * Inventory instance id of the session's fishing-shot stack; absent without shots; {@link
     * ActivityType#OFFLINE_FISHING} rows.
     */
    FISHING_SHOT_ITEM_ID("fishingShotItemId"),

    /** {@link ActivityType#OFFLINE_FISHING} rows. */
    SPOT_POOL("spotPool"),

    /** The spot inside {@link #SPOT_POOL}. */
    SPOT_ID("spotId"),

    /**
     * Lines listed in the current private store, decimal; a {@code BUY} store counts the lines of its buy list.
     * {@link ActivityType#PRIVATE_STORE} / {@link ActivityType#OFFLINE_PRIVATE_STORE} rows of a {@code SELL},
     * {@code PACKAGE_SELL} or {@code BUY} store; absent for {@code CRAFT}.
     */
    LOTS("lots");

    private final String key;

    ActivityMetadataKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    public static @Nullable ActivityMetadataKey fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ActivityMetadataKey.class, token);
    }
}
