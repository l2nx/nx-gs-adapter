package app.l2nx.gs.adapter.api.kafka.events.character.result;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A change the host saw but no source explains: a token that maps to no source, or a reconciliation difference. */
public final class SessionResultUntracked {

    private final long itemTemplateId;
    private final long delta;
    private final @Nullable String process;

    public SessionResultUntracked(long itemTemplateId, long delta, @Nullable String process) {
        if (itemTemplateId <= 0L) {
            throw new IllegalArgumentException(
                    "SessionResultUntracked.itemTemplateId must be positive, got " + itemTemplateId);
        }
        this.itemTemplateId = itemTemplateId;
        this.delta = delta;
        this.process = SessionResultText.blankToNull(process);
    }

    public long getItemTemplateId() {
        return itemTemplateId;
    }

    /** Signed net. */
    public long getDelta() {
        return delta;
    }

    /**
     * The engine's own token, verbatim, for the platform to see what to map next; {@code null} on a row the
     * reconciliation found, which has no operation.
     */
    public @Nullable String getProcess() {
        return process;
    }

    public Builder toBuilder() {
        return new Builder().itemTemplateId(itemTemplateId).delta(delta).process(process);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResultUntracked)) return false;
        SessionResultUntracked that = (SessionResultUntracked) o;
        return itemTemplateId == that.itemTemplateId && delta == that.delta && Objects.equals(process, that.process);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemTemplateId, delta, process);
    }

    @Override
    public String toString() {
        return "SessionResultUntracked[itemTemplateId=" + itemTemplateId
                + ", delta=" + delta
                + ", process=" + process + "]";
    }

    public static final class Builder {
        private long itemTemplateId;
        private long delta;
        private @Nullable String process;

        public Builder itemTemplateId(long itemTemplateId) {
            this.itemTemplateId = itemTemplateId;
            return this;
        }

        public Builder delta(long delta) {
            this.delta = delta;
            return this;
        }

        public Builder process(@Nullable String process) {
            this.process = process;
            return this;
        }

        public SessionResultUntracked build() {
            return new SessionResultUntracked(itemTemplateId, delta, process);
        }
    }
}
