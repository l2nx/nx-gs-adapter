package app.l2nx.gs.adapter.api.kafka.events.privatestore.model;

import app.l2nx.gs.adapter.api.domain.character.CharacterPrivateStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class StoreState {

    private static final EnumSet<CharacterPrivateStore> MARKET_TYPES =
            EnumSet.of(CharacterPrivateStore.SELL, CharacterPrivateStore.PACKAGE_SELL, CharacterPrivateStore.BUY);

    private final CharacterPrivateStore type;
    private final @Nullable String title;
    private final boolean offline;
    private final List<StoreLine> lines;

    public StoreState(
            CharacterPrivateStore type, @Nullable String title, boolean offline, @Nullable List<StoreLine> lines) {
        this.type = Objects.requireNonNull(type, "type");
        if (!MARKET_TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be SELL, PACKAGE_SELL or BUY (got " + type + ")");
        }
        this.title = title == null || title.trim().isEmpty() ? null : title;
        this.offline = offline;
        this.lines = freezeList(lines);
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
    public List<StoreLine> getLines() {
        return lines == null ? Collections.<StoreLine>emptyList() : lines;
    }

    public Builder toBuilder() {
        return new Builder().type(type).title(title).offline(offline).lines(lines);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<StoreLine> freezeList(@Nullable List<StoreLine> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<StoreLine>(src));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoreState)) return false;
        StoreState that = (StoreState) o;
        return offline == that.offline
                && type == that.type
                && Objects.equals(title, that.title)
                && Objects.equals(lines, that.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, title, offline, lines);
    }

    @Override
    public String toString() {
        return "StoreState[type=" + type + ", title=" + title + ", offline=" + offline + ", lines=" + lines + "]";
    }

    public static final class Builder {
        private CharacterPrivateStore type;
        private @Nullable String title;
        private boolean offline;
        private @Nullable List<StoreLine> lines;

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

        public Builder lines(@Nullable List<StoreLine> lines) {
            this.lines = lines;
            return this;
        }

        public StoreState build() {
            return new StoreState(type, title, offline, lines);
        }
    }
}
