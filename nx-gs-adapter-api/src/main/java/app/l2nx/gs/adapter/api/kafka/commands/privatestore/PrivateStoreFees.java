package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import org.jspecify.annotations.Nullable;

public final class PrivateStoreFees {

    /** Upper bound of a fee percent, inclusive. A value outside {@code 0..MAX_FEE_PERCENT} is rejected, never clamped. */
    public static final int MAX_FEE_PERCENT = 50;

    private PrivateStoreFees() {}

    public static boolean isValidPercent(int percent) {
        return percent >= 0 && percent <= MAX_FEE_PERCENT;
    }

    /** @throws IllegalArgumentException when {@code value} is {@code null} or out of range */
    public static int requirePercent(@Nullable Integer value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        checkPercent(value, name);
        return value;
    }

    /** {@code null} passes. @throws IllegalArgumentException when {@code value} is present and out of range */
    public static void checkPercent(@Nullable Integer value, String name) {
        if (value != null && !isValidPercent(value)) {
            throw new IllegalArgumentException(name + " must be in 0.." + MAX_FEE_PERCENT + " (got " + value + ")");
        }
    }
}
