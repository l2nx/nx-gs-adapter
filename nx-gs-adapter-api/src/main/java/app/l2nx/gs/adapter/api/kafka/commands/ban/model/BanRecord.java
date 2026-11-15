package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanResult;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One ban row of a {@link BanResult} with its effective expiry: the existing one when the host left the ban as is.
 */
public final class BanRecord {

    private final long id;
    private final @Nullable Instant expiresAt;

    public BanRecord(long id, @Nullable Instant expiresAt) {
        this.id = id;
        this.expiresAt = expiresAt;
    }

    public long getId() {
        return id;
    }

    /** {@code null} = permanent. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    public Builder toBuilder() {
        return new Builder().id(id).expiresAt(expiresAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanRecord)) return false;
        BanRecord that = (BanRecord) o;
        return id == that.id && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, expiresAt);
    }

    @Override
    public String toString() {
        return "BanRecord[id=" + id + ", expiresAt=" + expiresAt + "]";
    }

    public static final class Builder {
        private long id;
        private @Nullable Instant expiresAt;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder expiresAt(@Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public BanRecord build() {
            return new BanRecord(id, expiresAt);
        }
    }
}
