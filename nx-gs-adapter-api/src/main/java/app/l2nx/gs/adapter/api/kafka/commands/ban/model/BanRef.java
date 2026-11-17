package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanResult;
import java.util.Objects;

/**
 * Key of one ban row an {@link UnbanResult} cleared; {@code id} lives in the id space of {@code source}, so the pair
 * names the row.
 */
public final class BanRef {

    private final String source;
    private final long id;

    public BanRef(String source, long id) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (source.trim().isEmpty()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        this.source = source;
        this.id = id;
    }

    /** A {@link BanSource} token kept as a string, see {@link BanRecord#getSource()}. */
    public String getSource() {
        return source;
    }

    public long getId() {
        return id;
    }

    public Builder toBuilder() {
        return new Builder().source(source).id(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanRef)) return false;
        BanRef that = (BanRef) o;
        return Objects.equals(source, that.source) && id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, id);
    }

    @Override
    public String toString() {
        return "BanRef[source=" + source + ", id=" + id + "]";
    }

    public static final class Builder {
        private String source;
        private long id;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder source(BanSource source) {
            this.source = source.name();
            return this;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public BanRef build() {
            return new BanRef(source, id);
        }
    }
}
