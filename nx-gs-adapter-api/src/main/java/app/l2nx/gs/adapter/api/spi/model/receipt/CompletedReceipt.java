package app.l2nx.gs.adapter.api.spi.model.receipt;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An {@link ReceiptStatus#OK} receipt whose follow-up steps are not marked done yet. */
public final class CompletedReceipt {

    private final String key;
    private final @Nullable String claimJson;
    private final Instant completedAt;

    private CompletedReceipt(Builder b) {
        this.key = Objects.requireNonNull(b.key, "key");
        this.claimJson = b.claimJson;
        this.completedAt = Objects.requireNonNull(b.completedAt, "completedAt");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getKey() {
        return key;
    }

    public @Nullable String getClaimJson() {
        return claimJson;
    }

    /** {@link Instant#EPOCH} when the row carries no completion time, so age checks treat it as long completed. */
    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompletedReceipt)) return false;
        CompletedReceipt that = (CompletedReceipt) o;
        return key.equals(that.key)
                && Objects.equals(claimJson, that.claimJson)
                && completedAt.equals(that.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, claimJson, completedAt);
    }

    @Override
    public String toString() {
        return "CompletedReceipt[key=" + key + ", claimJson=" + claimJson + ", completedAt=" + completedAt + "]";
    }

    public static final class Builder {
        private @Nullable String key;
        private @Nullable String claimJson;
        private @Nullable Instant completedAt;

        private Builder() {}

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder claimJson(@Nullable String claimJson) {
            this.claimJson = claimJson;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public CompletedReceipt build() {
            return new CompletedReceipt(this);
        }
    }
}
