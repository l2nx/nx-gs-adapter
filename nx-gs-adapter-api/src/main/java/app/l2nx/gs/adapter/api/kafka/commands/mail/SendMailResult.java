package app.l2nx.gs.adapter.api.kafka.commands.mail;

import app.l2nx.gs.adapter.api.kafka.commands.mail.model.ItemDeliveryError;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Created mail ids (non-empty on success; one per mail, titled {@code "<title> 1/N"}..{@code "<title> N/N"} when split)
 * plus optional per-line errors. A host reporting partial delivery does so on an OK envelope, so the platform must
 * inspect {@code itemErrors}.
 */
public final class SendMailResult {

    private final List<Long> createdMailIds;
    private final List<ItemDeliveryError> itemErrors;

    public SendMailResult(@Nullable List<Long> createdMailIds, @Nullable List<ItemDeliveryError> itemErrors) {
        this.createdMailIds = MailLists.freeze(createdMailIds);
        this.itemErrors = MailLists.freeze(itemErrors);
    }

    public List<Long> getCreatedMailIds() {
        return createdMailIds;
    }

    /** Empty means every attachment materialized; non-empty means partial delivery. */
    public List<ItemDeliveryError> getItemErrors() {
        return itemErrors;
    }

    public Builder toBuilder() {
        return new Builder().createdMailIds(createdMailIds).itemErrors(itemErrors);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendMailResult)) return false;
        SendMailResult that = (SendMailResult) o;
        return Objects.equals(createdMailIds, that.createdMailIds) && Objects.equals(itemErrors, that.itemErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(createdMailIds, itemErrors);
    }

    @Override
    public String toString() {
        return "SendMailResult[createdMailIds=" + createdMailIds + ", itemErrors=" + itemErrors + "]";
    }

    public static final class Builder {
        private @Nullable List<Long> createdMailIds;
        private @Nullable List<ItemDeliveryError> itemErrors;

        public Builder createdMailIds(@Nullable List<Long> createdMailIds) {
            this.createdMailIds = createdMailIds;
            return this;
        }

        public Builder itemErrors(@Nullable List<ItemDeliveryError> itemErrors) {
            this.itemErrors = itemErrors;
            return this;
        }

        public SendMailResult build() {
            return new SendMailResult(createdMailIds, itemErrors);
        }
    }
}
