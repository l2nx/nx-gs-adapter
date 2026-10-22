package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class PrivateStoreMetadata {

    private PrivateStoreMetadata() {}

    /** {@code null} when absent or unusable (non-numeric, negative, percent above 100); never throws. */
    public static @Nullable PrivateStoreFee sellerFee(@Nullable Map<String, String> metadata) {
        return fee(
                metadata,
                PrivateStoreMetadataKey.SELLER_FEE_ADENA,
                PrivateStoreMetadataKey.SELLER_FEE_PERCENT,
                PrivateStoreFee.MAX_SELLER_FEE_PERCENT);
    }

    /** {@code null} when absent or unusable (non-numeric, negative); never throws. */
    public static @Nullable PrivateStoreFee buyerFee(@Nullable Map<String, String> metadata) {
        return fee(
                metadata,
                PrivateStoreMetadataKey.BUYER_FEE_ADENA,
                PrivateStoreMetadataKey.BUYER_FEE_PERCENT,
                Integer.MAX_VALUE);
    }

    private static @Nullable PrivateStoreFee fee(
            @Nullable Map<String, String> metadata,
            PrivateStoreMetadataKey adenaKey,
            PrivateStoreMetadataKey percentKey,
            int maxPercent) {
        if (metadata == null) {
            return null;
        }
        Long adena = parseLong(metadata.get(adenaKey.key()));
        Integer percent = parseInt(metadata.get(percentKey.key()));
        if (adena == null || percent == null || adena < 0 || percent < 0 || percent > maxPercent) {
            return null;
        }
        return PrivateStoreFee.of(adena, percent);
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
