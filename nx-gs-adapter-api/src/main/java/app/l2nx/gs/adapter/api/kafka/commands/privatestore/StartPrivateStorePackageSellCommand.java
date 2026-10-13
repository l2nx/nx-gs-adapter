package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** "Package sell" store: a buyer must purchase every line at once (enforced host-side, not by this shape). */
public final class StartPrivateStorePackageSellCommand extends StartPrivateStoreCommand {

    public StartPrivateStorePackageSellCommand(
            int charId,
            @Nullable String title,
            List<SellLine> lines,
            @Nullable Integer sellerFeePercent,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline) {
        super(charId, title, lines, sellerFeePercent, offlineAllowed, ownerVerified, deadline);
    }

    public Builder toBuilder() {
        return new Builder()
                .charId(getCharId())
                .title(getTitle())
                .lines(getLines())
                .sellerFeePercent(getSellerFeePercent())
                .offlineAllowed(getOfflineAllowed())
                .ownerVerified(isOwnerVerified())
                .deadline(getDeadline());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int charId;
        private @Nullable String title;
        private @Nullable List<SellLine> lines;
        private @Nullable Integer sellerFeePercent;
        private @Nullable Boolean offlineAllowed;
        private boolean ownerVerified;
        private @Nullable Instant deadline;

        public Builder charId(int charId) {
            this.charId = charId;
            return this;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder lines(List<SellLine> lines) {
            this.lines = lines;
            return this;
        }

        public Builder sellerFeePercent(@Nullable Integer sellerFeePercent) {
            this.sellerFeePercent = sellerFeePercent;
            return this;
        }

        public Builder offlineAllowed(@Nullable Boolean offlineAllowed) {
            this.offlineAllowed = offlineAllowed;
            return this;
        }

        public Builder ownerVerified(boolean ownerVerified) {
            this.ownerVerified = ownerVerified;
            return this;
        }

        public Builder deadline(@Nullable Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public StartPrivateStorePackageSellCommand build() {
            return new StartPrivateStorePackageSellCommand(
                    charId, title, lines, sellerFeePercent, offlineAllowed, ownerVerified, deadline);
        }
    }
}
