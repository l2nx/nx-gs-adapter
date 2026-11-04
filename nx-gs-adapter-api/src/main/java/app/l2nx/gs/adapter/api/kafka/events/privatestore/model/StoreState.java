package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.domain.character.CharacterPrivateStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The side of {@code type} carries the lines, the other side is empty. */
public final class StoreState {

    private static final EnumSet<CharacterPrivateStore> MARKET_TYPES =
            EnumSet.of(CharacterPrivateStore.SELL, CharacterPrivateStore.PACKAGE_SELL, CharacterPrivateStore.BUY);

    private final CharacterPrivateStore type;
    private final @Nullable String title;
    private final boolean offline;
    private final List<SellStoreLine> sellLines;
    private final List<BuyStoreLine> buyLines;

    public StoreState(
            CharacterPrivateStore type,
            @Nullable String title,
            boolean offline,
            @Nullable List<SellStoreLine> sellLines,
            @Nullable List<BuyStoreLine> buyLines) {
        this.type = Objects.requireNonNull(type, "type");
        if (!MARKET_TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be SELL, PACKAGE_SELL or BUY (got " + type + ")");
        }
        this.title = title == null || title.trim().isEmpty() ? null : title;
        this.offline = offline;
        this.sellLines = freezeList(sellLines);
        this.buyLines = freezeList(buyLines);
        if (type == CharacterPrivateStore.BUY && !this.sellLines.isEmpty()) {
            throw new IllegalArgumentException("sellLines must be empty in a BUY store");
        }
        if (type != CharacterPrivateStore.BUY && !this.buyLines.isEmpty()) {
            throw new IllegalArgumentException("buyLines must be empty in a " + type + " store");
        }
    }

    /** {@code SELL}, {@code PACKAGE_SELL} or {@code BUY} only; others (e.g. {@code CRAFT}) are rejected. */
    public CharacterPrivateStore getType() {
        return type;
    }

    /** Store sign text; {@code null} when none is set, never blank. */
    public @Nullable String getTitle() {
        return title;
    }

    /** {@code true} while the trader stands in the world without a game client. */
    public boolean isOffline() {
        return offline;
    }

    /** Never null on read, also after Gson/Jackson bypassed the constructor. */
    public List<SellStoreLine> getSellLines() {
        return sellLines == null ? Collections.<SellStoreLine>emptyList() : sellLines;
    }

    /** Never null on read, also after Gson/Jackson bypassed the constructor. */
    public List<BuyStoreLine> getBuyLines() {
        return buyLines == null ? Collections.<BuyStoreLine>emptyList() : buyLines;
    }

    public Builder toBuilder() {
        return new Builder()
                .type(type)
                .title(title)
                .offline(offline)
                .sellLines(sellLines)
                .buyLines(buyLines);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static <T> List<T> freezeList(@Nullable List<T> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(src));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoreState)) return false;
        StoreState that = (StoreState) o;
        return offline == that.offline
                && type == that.type
                && Objects.equals(title, that.title)
                && Objects.equals(sellLines, that.sellLines)
                && Objects.equals(buyLines, that.buyLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, title, offline, sellLines, buyLines);
    }

    @Override
    public String toString() {
        return "StoreState[type=" + type + ", title=" + title + ", offline=" + offline + ", sellLines=" + sellLines
                + ", buyLines=" + buyLines + "]";
    }

    public static final class Builder {
        private CharacterPrivateStore type;
        private @Nullable String title;
        private boolean offline;
        private @Nullable List<SellStoreLine> sellLines;
        private @Nullable List<BuyStoreLine> buyLines;

        public Builder type(CharacterPrivateStore type) {
            this.type = type;
            return this;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder sellLines(@Nullable List<SellStoreLine> sellLines) {
            this.sellLines = sellLines;
            return this;
        }

        public Builder buyLines(@Nullable List<BuyStoreLine> buyLines) {
            this.buyLines = buyLines;
            return this;
        }

        public StoreState build() {
            return new StoreState(type, title, offline, sellLines, buyLines);
        }
    }
}
