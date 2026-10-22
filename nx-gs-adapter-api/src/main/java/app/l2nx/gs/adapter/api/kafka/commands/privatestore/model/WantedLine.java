package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Buy-store line. Gson bypasses the constructor, so the host re-validates every line. */
public final class WantedLine {

    private final long itemTemplateId;
    private final long count;
    private final long priceAdena;

    public WantedLine(long itemTemplateId, long count, long priceAdena) {
        if (itemTemplateId <= 0L) {
            throw new IllegalArgumentException("itemTemplateId must be positive (got " + itemTemplateId + ")");
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive (got " + count + ")");
        }
        if (priceAdena < 0L) {
            throw new IllegalArgumentException("priceAdena must be non-negative (got " + priceAdena + ")");
        }
        try {
            Math.multiplyExact(count, priceAdena);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "count * priceAdena overflows a long (count=" + count + ", priceAdena=" + priceAdena + ")", e);
        }
        this.itemTemplateId = itemTemplateId;
        this.count = count;
        this.priceAdena = priceAdena;
    }

    /** Catalog template id, NOT an instance object-id. */
    public long getItemTemplateId() {
        return itemTemplateId;
    }

    public long getCount() {
        return count;
    }

    /** Per unit; {@code 0} is valid. */
    public long getPriceAdena() {
        return priceAdena;
    }

    /** A second line of the same template is unreachable: the host's fill matches by template and price only. */
    public static @Nullable Long firstDuplicateItemTemplateId(Collection<WantedLine> lines) {
        Set<Long> seen = new HashSet<>();
        for (WantedLine line : lines) {
            if (line != null && !seen.add(line.itemTemplateId)) {
                return line.itemTemplateId;
            }
        }
        return null;
    }

    public Builder toBuilder() {
        return new Builder().itemTemplateId(itemTemplateId).count(count).priceAdena(priceAdena);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WantedLine)) return false;
        WantedLine that = (WantedLine) o;
        return itemTemplateId == that.itemTemplateId && count == that.count && priceAdena == that.priceAdena;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, count, priceAdena);
    }

    @Override
    public String toString() {
        return "WantedLine[itemTemplateId=" + itemTemplateId + ", count=" + count + ", priceAdena=" + priceAdena + "]";
    }

    public static final class Builder {
        private long itemTemplateId;
        private long count;
        private long priceAdena;

        public Builder itemTemplateId(long itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder priceAdena(long priceAdena) {
            this.priceAdena = priceAdena;
            return this;
        }

        public WantedLine build() {
            return new WantedLine(itemTemplateId, count, priceAdena);
        }
    }
}
