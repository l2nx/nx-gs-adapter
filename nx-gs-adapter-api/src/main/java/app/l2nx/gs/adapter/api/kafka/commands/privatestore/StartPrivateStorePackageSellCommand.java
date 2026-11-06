package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** "Package sell" store: a buyer must purchase every line at once (enforced host-side, not by this shape). */
public final class StartPrivateStorePackageSellCommand extends StartPrivateStoreSellingCommand {

    public StartPrivateStorePackageSellCommand(
            int characterId,
            @Nullable String title,
            List<SellLine> lines,
            @Nullable Integer sellerFeePercent,
            @Nullable Boolean offlineAllowed,
            boolean ownerVerified,
            @Nullable Instant deadline,
            UUID idempotencyKey,
            CommandInitiator initiator) {
        super(
                characterId,
                title,
                lines,
                sellerFeePercent,
                offlineAllowed,
                ownerVerified,
                deadline,
                idempotencyKey,
                initiator);
    }

    public Builder toBuilder() {
        return new Builder()
                .characterId(getCharacterId())
                .title(getTitle())
                .lines(getLines())
                .sellerFeePercent(getSellerFeePercent())
                .offlineAllowed(getOfflineAllowed())
                .ownerVerified(isOwnerVerified())
                .deadline(getDeadline())
                .idempotencyKey(getIdempotencyKey())
                .initiator(getInitiator());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int characterId;
        private @Nullable String title;
        private @Nullable List<SellLine> lines;
        private @Nullable Integer sellerFeePercent;
        private @Nullable Boolean offlineAllowed;
        private boolean ownerVerified;
        private @Nullable Instant deadline;
        private @Nullable UUID idempotencyKey;
        private CommandInitiator initiator;

        public Builder characterId(int characterId) {
            this.characterId = characterId;
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

        public Builder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public StartPrivateStorePackageSellCommand build() {
            return new StartPrivateStorePackageSellCommand(
                    characterId,
                    title,
                    lines,
                    sellerFeePercent,
                    offlineAllowed,
                    ownerVerified,
                    deadline,
                    idempotencyKey,
                    initiator);
        }
    }
}
