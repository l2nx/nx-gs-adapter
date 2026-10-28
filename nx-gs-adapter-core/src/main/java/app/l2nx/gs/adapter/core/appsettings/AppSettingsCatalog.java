package app.l2nx.gs.adapter.core.appsettings;

import app.l2nx.gs.adapter.api.Vocabularies;
import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.appsettings.ReviveTownMode;
import app.l2nx.gs.adapter.api.spi.model.appsettings.SpotOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class AppSettingsCatalog {

    private static final Map<ActivityType, String> SESSION_TYPE_TOKENS = sessionTypeTokens();

    static final String PHANTOM_TITLE_TEXT = "app.phantom.title.text";
    static final String PHANTOM_TITLE_COLOR = "app.phantom.title.color";
    static final String RECENT_LOGOUT_MINUTES = "app.session.recent-logout-minutes";
    static final String RESTART_WINDOW_MINUTES = "app.session.restart-window-minutes";
    static final String RETURN_THRESHOLD_MINUTES = "app.session.return-threshold-minutes";
    static final String PRIVATE_STORE_ENABLED = enabledKey(ActivityType.OFFLINE_PRIVATE_STORE);
    static final String PRIVATE_STORE_START_COOLDOWN_MINUTES = "app.private-store.start-cooldown-minutes";
    static final String FISHING_ENABLED = enabledKey(ActivityType.OFFLINE_FISHING);
    static final String FISHING_START_COOLDOWN_MINUTES = "app.fishing.start-cooldown-minutes";
    static final String FISHING_MIN_DURATION_MINUTES = "app.fishing.min-duration-minutes";
    static final String FISHING_MAX_DURATION_MINUTES = "app.fishing.max-duration-minutes";
    static final String FISHING_FLOAT_DISTANCE = "app.fishing.float-distance";
    static final String FISHING_SPOT_ORDER = "app.fishing.spot-order";
    static final String FISHING_SPOT_POOL = "app.fishing.spot-pool";
    static final String REVIVE_ENABLED = "app.revive.enabled";
    static final String REVIVE_TOWN_MODE = "app.revive.town.mode";
    static final String REVIVE_TOWN_SPOT_POOL = "app.revive.town.spot-pool";
    static final String CHAT_ENABLED = "app.chat.enabled";
    static final String CHAT_ONLINE_SEND_CHANNELS = "app.chat.online-send-channels";
    static final String CHAT_OFFLINE_SEND_CHANNELS = "app.chat.offline-send-channels";
    static final String RESTORE_BATCH_SIZE = "app.restore.batch-size";
    static final String RESTORE_BATCH_PAUSE_MILLIS = "app.restore.batch-pause-millis";
    static final String HISTORY_RETENTION_DAYS = "app.history.retention-days";

    static final String UNSET = "<unset>";
    /** An empty list that is a value of its own, not a fallback to a default. */
    static final String NONE = "<none>";

    private static final Pattern RGB_HEX = Pattern.compile("[0-9A-Fa-f]{6}");

    private static final Set<ChatChannel> SENDABLE_FROM_APP = sendableFromApp();

    /** In {@link AppRules#SESSION_TYPES} order, which the snapshot keeps. */
    static final Map<String, ActivityType> SESSION_TYPE_KEYS = sessionTypeKeys();

    private static final Map<String, Key> KEYS = keys();

    private AppSettingsCatalog() {}

    static boolean contains(String name) {
        return KEYS.containsKey(name);
    }

    static Iterable<Key> all() {
        return KEYS.values();
    }

    static List<String> diff(AppRules before, AppRules after) {
        List<String> changes = new ArrayList<String>();
        for (Key key : KEYS.values()) {
            String was = key.render(before);
            String now = key.render(after);
            if (!was.equals(now)) {
                changes.add(key.name + ": " + was + " -> " + now);
            }
        }
        return changes;
    }

    interface Renderer {
        String render(AppRules rules);
    }

    interface Applier {
        /** @throws InvalidValueException with the reason the log shows after the key */
        void apply(String trimmed, AppRules.Builder builder);
    }

    static final class Key {
        final String name;
        private final Renderer renderer;
        private final Applier applier;

        private Key(String name, Renderer renderer, Applier applier) {
            this.name = name;
            this.renderer = renderer;
            this.applier = applier;
        }

        String render(AppRules rules) {
            return renderer.render(rules);
        }

        void apply(String trimmed, AppRules.Builder builder) {
            applier.apply(trimmed, builder);
        }
    }

    static final class InvalidValueException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidValueException(String reason) {
            super(reason, null, false, false);
        }
    }

    private static Map<ActivityType, String> sessionTypeTokens() {
        Map<ActivityType, String> tokens = new EnumMap<ActivityType, String>(ActivityType.class);
        tokens.put(ActivityType.OFFLINE_PRIVATE_STORE, "private-store");
        tokens.put(ActivityType.OFFLINE_FISHING, "fishing");
        return tokens;
    }

    private static String enabledKey(ActivityType activity) {
        String token = SESSION_TYPE_TOKENS.get(activity);
        if (token == null) {
            throw new IllegalStateException("session type " + activity + " has no app.<type>.enabled key name");
        }
        return "app." + token + ".enabled";
    }

    private static Map<String, ActivityType> sessionTypeKeys() {
        Map<String, ActivityType> keys = new LinkedHashMap<String, ActivityType>();
        for (ActivityType activity : AppRules.SESSION_TYPES) {
            keys.put(enabledKey(activity), activity);
        }
        return Collections.unmodifiableMap(keys);
    }

    private static Map<String, Key> keys() {
        Map<String, Key> keys = new LinkedHashMap<String, Key>();
        put(
                keys,
                PHANTOM_TITLE_TEXT,
                r -> orUnset(r.phantomTitleText()),
                (raw, b) -> b.phantomTitleText(titleText(raw)));
        put(
                keys,
                PHANTOM_TITLE_COLOR,
                r -> String.format("%06X", r.phantomTitleColorRgb()),
                (raw, b) -> b.phantomTitleColorRgb(rgb(raw)));
        put(
                keys,
                RECENT_LOGOUT_MINUTES,
                r -> String.valueOf(r.recentLogoutMinutes()),
                (raw, b) -> b.recentLogoutMinutes(intIn(raw, AppRules.RECENT_LOGOUT_MINUTES)));
        put(
                keys,
                RESTART_WINDOW_MINUTES,
                r -> String.valueOf(r.restartWindowMinutes()),
                (raw, b) -> b.restartWindowMinutes(intIn(raw, AppRules.RESTART_WINDOW_MINUTES)));
        put(
                keys,
                RETURN_THRESHOLD_MINUTES,
                r -> String.valueOf(r.returnThresholdMinutes()),
                (raw, b) -> b.returnThresholdMinutes(intIn(raw, AppRules.RETURN_THRESHOLD_MINUTES)));
        for (Map.Entry<String, ActivityType> type : SESSION_TYPE_KEYS.entrySet()) {
            ActivityType activity = type.getValue();
            put(
                    keys,
                    type.getKey(),
                    r -> String.valueOf(r.sessionTypeEnabled(activity)),
                    (raw, b) -> b.sessionTypeEnabled(activity, bool(raw)));
        }
        put(
                keys,
                PRIVATE_STORE_START_COOLDOWN_MINUTES,
                r -> String.valueOf(r.privateStoreStartCooldownMinutes()),
                (raw, b) ->
                        b.privateStoreStartCooldownMinutes(intIn(raw, AppRules.PRIVATE_STORE_START_COOLDOWN_MINUTES)));
        put(
                keys,
                FISHING_START_COOLDOWN_MINUTES,
                r -> String.valueOf(r.fishingStartCooldownMinutes()),
                (raw, b) -> b.fishingStartCooldownMinutes(intIn(raw, AppRules.FISHING_START_COOLDOWN_MINUTES)));
        put(
                keys,
                FISHING_MIN_DURATION_MINUTES,
                r -> String.valueOf(r.fishingMinDurationMinutes()),
                (raw, b) -> b.fishingMinDurationMinutes(intIn(raw, AppRules.FISHING_DURATION_MINUTES)));
        put(
                keys,
                FISHING_MAX_DURATION_MINUTES,
                r -> String.valueOf(r.fishingMaxDurationMinutes()),
                (raw, b) -> b.fishingMaxDurationMinutes(intIn(raw, AppRules.FISHING_DURATION_MINUTES)));
        put(
                keys,
                FISHING_FLOAT_DISTANCE,
                r -> String.valueOf(r.fishingFloatDistance()),
                (raw, b) -> b.fishingFloatDistance(intIn(raw, AppRules.FISHING_FLOAT_DISTANCE)));
        put(
                keys,
                FISHING_SPOT_ORDER,
                r -> r.fishingSpotOrder().name(),
                (raw, b) -> b.fishingSpotOrder(token(SpotOrder.class, raw)));
        put(keys, FISHING_SPOT_POOL, r -> orUnset(r.fishingSpotPool()), (raw, b) -> b.fishingSpotPool(spotPool(raw)));
        put(keys, REVIVE_ENABLED, r -> String.valueOf(r.reviveEnabled()), (raw, b) -> b.reviveEnabled(bool(raw)));
        put(
                keys,
                REVIVE_TOWN_MODE,
                r -> r.reviveTownMode().name(),
                (raw, b) -> b.reviveTownMode(token(ReviveTownMode.class, raw)));
        put(
                keys,
                REVIVE_TOWN_SPOT_POOL,
                r -> r.reviveTownSpotPools().isEmpty() ? UNSET : String.join(",", r.reviveTownSpotPools()),
                (raw, b) -> b.reviveTownSpotPools(spotPools(raw)));
        put(keys, CHAT_ENABLED, r -> String.valueOf(r.chatEnabled()), (raw, b) -> b.chatEnabled(bool(raw)));
        put(
                keys,
                CHAT_ONLINE_SEND_CHANNELS,
                r -> channels(r.chatOnlineSendChannels()),
                (raw, b) -> b.chatOnlineSendChannels(chatChannels(raw, false)));
        put(
                keys,
                CHAT_OFFLINE_SEND_CHANNELS,
                r -> channels(r.chatOfflineSendChannels()),
                (raw, b) -> b.chatOfflineSendChannels(chatChannels(raw, true)));
        put(
                keys,
                RESTORE_BATCH_SIZE,
                r -> String.valueOf(r.restoreBatchSize()),
                (raw, b) -> b.restoreBatchSize(intIn(raw, AppRules.RESTORE_BATCH_SIZE)));
        put(
                keys,
                RESTORE_BATCH_PAUSE_MILLIS,
                r -> String.valueOf(r.restoreBatchPauseMillis()),
                (raw, b) -> b.restoreBatchPauseMillis(intIn(raw, AppRules.RESTORE_BATCH_PAUSE_MILLIS)));
        put(
                keys,
                HISTORY_RETENTION_DAYS,
                r -> String.valueOf(r.historyRetentionDays()),
                (raw, b) -> b.historyRetentionDays(intIn(raw, AppRules.HISTORY_RETENTION_DAYS)));
        return Collections.unmodifiableMap(keys);
    }

    private static void put(Map<String, Key> keys, String name, Renderer renderer, Applier applier) {
        keys.put(name, new Key(name, renderer, applier));
    }

    private static String orUnset(@Nullable String value) {
        return value == null ? UNSET : value;
    }

    /** Blank is unset: the character keeps its own title. */
    private static @Nullable String titleText(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.codePointCount(0, raw.length()) > AppRules.PHANTOM_TITLE_MAX_CODE_POINTS) {
            throw new InvalidValueException("longer than " + AppRules.PHANTOM_TITLE_MAX_CODE_POINTS + " characters");
        }
        return raw;
    }

    private static @Nullable String spotPool(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        if (!AppRules.isSpotPoolName(raw)) {
            throw new InvalidValueException("expected 1-32 characters of a-z, 0-9 and _, got '" + raw + "'");
        }
        return raw;
    }

    static List<String> spotPools(String raw) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> pools = new LinkedHashSet<String>();
        for (String part : raw.split(",", -1)) {
            String pool = part.trim();
            if (!AppRules.isSpotPoolName(pool)) {
                throw new InvalidValueException(
                        "expected comma-separated names of 1-32 characters of a-z, 0-9 and _, got '" + pool + "'");
            }
            if (!pools.add(pool)) {
                throw new InvalidValueException("'" + pool + "' is listed twice");
            }
        }
        return new ArrayList<String>(pools);
    }

    /** Empty: read-only in that character state, not the default list. */
    private static Set<ChatChannel> chatChannels(String raw, boolean offline) {
        Set<ChatChannel> channels = EnumSet.noneOf(ChatChannel.class);
        if (raw.isEmpty()) {
            return channels;
        }
        for (String part : raw.split(",", -1)) {
            ChatChannel channel = token(ChatChannel.class, part.trim(), SENDABLE_FROM_APP);
            if (!channels.add(channel)) {
                throw new InvalidValueException("'" + channel + "' is listed twice");
            }
            if (offline && !channel.sendableOffline()) {
                throw new InvalidValueException(channel + " is not available out of game");
            }
        }
        return channels;
    }

    private static Set<ChatChannel> sendableFromApp() {
        Set<ChatChannel> channels = EnumSet.allOf(ChatChannel.class);
        channels.removeIf(channel -> !channel.sendableFromApp());
        return Collections.unmodifiableSet(channels);
    }

    private static String channels(Set<ChatChannel> channels) {
        return channels.isEmpty() ? NONE : String.join(",", channelNames(channels));
    }

    static List<String> channelNames(Set<ChatChannel> channels) {
        List<String> names = new ArrayList<String>(channels.size());
        for (ChatChannel channel : channels) {
            names.add(channel.name());
        }
        return names;
    }

    private static int rgb(String raw) {
        if (!RGB_HEX.matcher(raw).matches()) {
            throw new InvalidValueException("expected 6 hex digits RGB, got '" + raw + "'");
        }
        return Integer.parseInt(raw, 16);
    }

    private static int intIn(String raw, AppRules.Range range) {
        int value = integer(raw);
        if (!range.contains(value)) {
            throw new InvalidValueException(
                    range.bounded()
                            ? "out of range " + range.min() + ".." + range.max() + ": " + value
                            : "less than " + range.min() + ": " + value);
        }
        return value;
    }

    private static int integer(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new InvalidValueException("not an integer: '" + raw + "'");
        }
    }

    private static boolean bool(String raw) {
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw new InvalidValueException("expected true or false, got '" + raw + "'");
    }

    static <E extends Enum<E>> E token(Class<E> type, String raw) {
        return token(type, raw, EnumSet.allOf(type));
    }

    /** {@code allowed} renders the error in declaration order, so the message follows the enum. */
    private static <E extends Enum<E>> E token(Class<E> type, String raw, Set<E> allowed) {
        E value = Vocabularies.fromToken(type, raw);
        if (value == null || !allowed.contains(value)) {
            throw new InvalidValueException("expected " + alternatives(allowed) + ", got '" + raw + "'");
        }
        return value;
    }

    private static String alternatives(Set<? extends Enum<?>> allowed) {
        StringBuilder text = new StringBuilder();
        int index = 0;
        for (Enum<?> value : allowed) {
            if (index > 0) {
                text.append(index == allowed.size() - 1 ? " or " : ", ");
            }
            text.append(value.name());
            index++;
        }
        return text.toString();
    }
}
