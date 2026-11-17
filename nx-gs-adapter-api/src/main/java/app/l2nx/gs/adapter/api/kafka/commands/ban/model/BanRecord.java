package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanResult;
import app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDto;
import java.util.Objects;

/**
 * One ban row of a {@link BanResult}, as it stands after the command, in the shape the ban sync stream carries.
 * {@link BanDbDto#getId() row.id} lives in the id space of {@link #getSource() source}.
 */
public final class BanRecord {

    private final String source;
    private final BanDbDto row;

    public BanRecord(String source, BanDbDto row) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (source.trim().isEmpty()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (row == null) {
            throw new IllegalArgumentException("row is required");
        }
        this.source = source;
        this.row = row;
    }

    /**
     * A {@link BanSource} token, kept a string so a token from a later api release does not fail the whole result;
     * {@link BanSource#fromToken} reads it as {@code null}.
     */
    public String getSource() {
        return source;
    }

    public BanDbDto getRow() {
        return row;
    }

    public Builder toBuilder() {
        return new Builder().source(source).row(row);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanRecord)) return false;
        BanRecord that = (BanRecord) o;
        return Objects.equals(source, that.source) && Objects.equals(row, that.row);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, row);
    }

    @Override
    public String toString() {
        return "BanRecord[source=" + source + ", row=" + row + "]";
    }

    public static final class Builder {
        private String source;
        private BanDbDto row;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder source(BanSource source) {
            this.source = source.name();
            return this;
        }

        public Builder row(BanDbDto row) {
            this.row = row;
            return this;
        }

        public BanRecord build() {
            return new BanRecord(source, row);
        }
    }
}
