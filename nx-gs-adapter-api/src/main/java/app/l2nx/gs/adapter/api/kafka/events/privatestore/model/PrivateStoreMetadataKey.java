package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.Vocabularies;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStorePurchaseEvent;
import org.jspecify.annotations.Nullable;

/**
 * Keys of the {@link PrivateStorePurchaseEvent} {@code metadata} map; the wire string is {@link #key()}.
 * Open vocabulary: hosts may add keys, consumers ignore unknown ones.
 */
public enum PrivateStoreMetadataKey implements Vocabularies.Keyed {

    /**
     * Store opener's adena balance after the deal (decimal string). The opener is the notification recipient: the
     * seller on {@link PrivateStoreSide#ASK ASK}, the buyer on {@link PrivateStoreSide#BID BID}.
     */
    STORE_OWNER_ADENA("store_owner_adena"),

    /** {@link SessionSource#name()} of the deal's origin. */
    SOURCE("source"),

    /**
     * Burned buyer fee (decimal string), on platform buys and on fills of an app buy store with a buyer fee; not part
     * of the seller's proceeds; omitted otherwise.
     */
    BUYER_FEE_ADENA("buyer_fee_adena"),

    /** Rate {@link #BUYER_FEE_ADENA} was computed at, in whole percent; omitted with it. */
    BUYER_FEE_PERCENT("buyer_fee_percent"),

    /**
     * Fee withheld from the seller's proceeds and burned (decimal string), on every sale from a store with a seller fee,
     * in-game included; omitted otherwise.
     */
    SELLER_FEE_ADENA("seller_fee_adena"),

    /** Rate {@link #SELLER_FEE_ADENA} was computed at, in whole percent; omitted with it. */
    SELLER_FEE_PERCENT("seller_fee_percent");

    private final String key;

    PrivateStoreMetadataKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    public static @Nullable PrivateStoreMetadataKey fromToken(@Nullable String token) {
        return Vocabularies.fromToken(PrivateStoreMetadataKey.class, token);
    }
}
