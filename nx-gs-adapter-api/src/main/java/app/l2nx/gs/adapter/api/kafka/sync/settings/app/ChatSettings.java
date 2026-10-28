package app.l2nx.gs.adapter.api.kafka.sync.settings.app;

import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Chat with the app; channels are {@link ChatChannel} names, each {@link ChatChannel#sendableFromApp() sendable from
 * the app}. Gson bypasses the constructor, so the getters, {@code equals} and {@code toString} read the lists
 * null-safe and hand them out unmodifiable.
 */
public final class ChatSettings {

    private final boolean enabled;
    private final List<String> onlineSendChannels;
    private final List<String> offlineSendChannels;

    public ChatSettings(
            boolean enabled, @Nullable List<String> onlineSendChannels, @Nullable List<String> offlineSendChannels) {
        this.enabled = enabled;
        this.onlineSendChannels = copy(onlineSendChannels);
        this.offlineSendChannels = copy(offlineSendChannels);
    }

    private static List<String> copy(@Nullable List<String> channels) {
        return channels == null || channels.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(channels));
    }

    /**
     * {@code false}: no game chat reaches the platform and the host refuses every send from a character; lines without
     * a character (announcements, staff personas) still go out.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /** Where the app may send while the character is in game; empty: read-only in that state. */
    public List<String> getOnlineSendChannels() {
        return view(onlineSendChannels);
    }

    /** Where the app may send while the character is out of game; empty: read-only in that state. */
    public List<String> getOfflineSendChannels() {
        return view(offlineSendChannels);
    }

    private static List<String> view(@Nullable List<String> channels) {
        return channels == null ? Collections.<String>emptyList() : Collections.unmodifiableList(channels);
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(enabled)
                .onlineSendChannels(getOnlineSendChannels())
                .offlineSendChannels(getOfflineSendChannels());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatSettings)) return false;
        ChatSettings that = (ChatSettings) o;
        return enabled == that.enabled
                && getOnlineSendChannels().equals(that.getOnlineSendChannels())
                && getOfflineSendChannels().equals(that.getOfflineSendChannels());
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, getOnlineSendChannels(), getOfflineSendChannels());
    }

    @Override
    public String toString() {
        return "ChatSettings[enabled=" + enabled
                + ", onlineSendChannels=" + getOnlineSendChannels()
                + ", offlineSendChannels=" + getOfflineSendChannels() + "]";
    }

    public static final class Builder {
        private boolean enabled;
        private @Nullable List<String> onlineSendChannels;
        private @Nullable List<String> offlineSendChannels;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder onlineSendChannels(@Nullable List<String> onlineSendChannels) {
            this.onlineSendChannels = onlineSendChannels;
            return this;
        }

        public Builder offlineSendChannels(@Nullable List<String> offlineSendChannels) {
            this.offlineSendChannels = offlineSendChannels;
            return this;
        }

        public ChatSettings build() {
            return new ChatSettings(enabled, onlineSendChannels, offlineSendChannels);
        }
    }
}
