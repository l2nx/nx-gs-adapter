package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.PrivateStoreFees;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStorePurchaseEvent;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Canonical keys for the {@code metadata} map of {@link PrivateStorePurchaseEvent}; hosts MAY add others (opaque to consumers).
 *
 * <ul>
 *   <li>{@link #STORE_OWNER_ADENA} - store opener's adena balance <b>after</b> the deal (decimal string); the opener is the notification recipient (seller on {@link PrivateStoreSide#ASK ASK}, buyer on {@link PrivateStoreSide#BID BID}).</li>
 *   <li>{@link #SOURCE} - absent or {@link #SOURCE_IN_GAME} for the in-game store packet, {@link #SOURCE_REMOTE} for a platform buy command.</li>
 *   <li>{@link #BUYER_FEE_ADENA} - burned buyer fee (decimal string), platform buys only; not part of the seller's proceeds.</li>
 *   <li>{@link #BUYER_FEE_PERCENT} - rate {@link #BUYER_FEE_ADENA} was computed at, in whole percent.</li>
 *   <li>{@link #SELLER_FEE_ADENA} - fee withheld from the seller's proceeds and burned (decimal string), on every sale from a store with a seller fee, in-game included; omitted otherwise.</li>
 *   <li>{@link #SELLER_FEE_PERCENT} - rate {@link #SELLER_FEE_ADENA} was computed at, in whole percent; omitted with it.</li>
 * </ul>
 */
public final class WellKnownPrivateStoreMetadata {

    private WellKnownPrivateStoreMetadata() {}

    public static final String STORE_OWNER_ADENA = "store_owner_adena";

    public static final String SOURCE = "source";

    public static final String BUYER_FEE_ADENA = "buyer_fee_adena";

    public static final String BUYER_FEE_PERCENT = "buyer_fee_percent";

    public static final String SELLER_FEE_ADENA = "seller_fee_adena";

    public static final String SELLER_FEE_PERCENT = "seller_fee_percent";

    public static final String SOURCE_IN_GAME = "in_game";

    public static final String SOURCE_REMOTE = "remote";

    /**
     * The seller fee recorded in {@code metadata}, or {@code null} when the store carried none or the entries are unusable
     * (missing, non-numeric, non-positive adena, percent outside {@code 0..}{@link PrivateStoreFees#MAX_FEE_PERCENT}). Never throws.
     */
    public static @Nullable SellerFee sellerFee(@Nullable Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        Long adena = parseLong(metadata.get(SELLER_FEE_ADENA));
        Integer percent = parseInt(metadata.get(SELLER_FEE_PERCENT));
        if (adena == null || percent == null || adena < 0 || !PrivateStoreFees.isValidPercent(percent)) {
            return null;
        }
        return SellerFee.of(adena, percent);
    }

    private static @Nullable Long parseLong(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Integer parseInt(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
