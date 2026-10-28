package app.l2nx.gs.adapter.core.appsettings;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.appsettings.ReviveTownMode;
import app.l2nx.gs.adapter.core.appsettings.AppSettingsCatalog.InvalidValueException;
import app.l2nx.gs.adapter.core.appsettings.AppSettingsCatalog.Key;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** One bad or unknown key rejects the whole file, so a half-valid edit never runs mixed with the previous values. */
final class AppSettingsFile {

    static final String FILE_NAME = "l2nx-app.properties";

    private static final int BYTE_ORDER_MARK = 0xFEFF;

    private AppSettingsFile() {}

    static Path location(Path configFile) {
        return configFile.resolveSibling(FILE_NAME);
    }

    /** A leading byte order mark is skipped: editors on Windows write one. */
    static Result parse(Reader reader) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(withoutByteOrderMark(reader));
        } catch (IllegalArgumentException e) {
            // Properties.load reports a malformed backslash-u escape this way, not as an IOException
            return Result.invalid(Collections.singletonList("the file cannot be parsed: " + e.getMessage()));
        }

        AppRules.Builder builder = AppRules.builder();
        List<String> errors = new ArrayList<String>();
        Set<String> failed = new HashSet<String>();
        for (Key key : AppSettingsCatalog.all()) {
            String raw = properties.getProperty(key.name);
            if (raw == null) {
                continue;
            }
            try {
                key.apply(raw.trim(), builder);
            } catch (InvalidValueException e) {
                errors.add(key.name + ": " + e.getMessage());
                failed.add(key.name);
            }
        }
        for (String name : new TreeSet<String>(properties.stringPropertyNames())) {
            if (!AppSettingsCatalog.contains(name)) {
                errors.add(name + ": unknown key");
            }
        }

        // checked on the values themselves: the builder enforces the same rules and would throw instead of reporting
        AppRules defaults = AppRules.defaults();
        if (!failed.contains(AppSettingsCatalog.FISHING_MIN_DURATION_MINUTES)
                && !failed.contains(AppSettingsCatalog.FISHING_MAX_DURATION_MINUTES)) {
            int min = intOr(
                    properties, AppSettingsCatalog.FISHING_MIN_DURATION_MINUTES, defaults.fishingMinDurationMinutes());
            int max = intOr(
                    properties, AppSettingsCatalog.FISHING_MAX_DURATION_MINUTES, defaults.fishingMaxDurationMinutes());
            if (!AppRules.fishingDurationsOrdered(min, max)) {
                errors.add(AppSettingsCatalog.FISHING_MAX_DURATION_MINUTES + ": less than "
                        + AppSettingsCatalog.FISHING_MIN_DURATION_MINUTES + " (" + min + ")");
            }
        }
        if (!failed.contains(AppSettingsCatalog.FISHING_SPOT_POOL)
                && blank(trimmed(properties, AppSettingsCatalog.FISHING_SPOT_POOL))) {
            for (Map.Entry<String, ActivityType> type : AppSettingsCatalog.SESSION_TYPE_KEYS.entrySet()) {
                if (AppRules.needsSpotPool(type.getValue())
                        && !failed.contains(type.getKey())
                        && "true".equals(trimmed(properties, type.getKey()))) {
                    errors.add(type.getKey() + ": true requires " + AppSettingsCatalog.FISHING_SPOT_POOL);
                }
            }
        }
        if (!failed.contains(AppSettingsCatalog.REVIVE_TOWN_MODE)
                && !failed.contains(AppSettingsCatalog.REVIVE_TOWN_SPOT_POOL)) {
            String mode = trimmed(properties, AppSettingsCatalog.REVIVE_TOWN_MODE);
            String pools = trimmed(properties, AppSettingsCatalog.REVIVE_TOWN_SPOT_POOL);
            if (!AppRules.reviveTownSpotPoolsSuffice(
                    blank(mode) ? defaults.reviveTownMode() : AppSettingsCatalog.token(ReviveTownMode.class, mode),
                    blank(pools) ? defaults.reviveTownSpotPools() : AppSettingsCatalog.spotPools(pools))) {
                errors.add(AppSettingsCatalog.REVIVE_TOWN_MODE + ": " + ReviveTownMode.SPOT_POOL + " requires "
                        + AppSettingsCatalog.REVIVE_TOWN_SPOT_POOL);
            }
        }
        return errors.isEmpty() ? Result.valid(builder.build()) : Result.invalid(errors);
    }

    private static Reader withoutByteOrderMark(Reader reader) throws IOException {
        PushbackReader pushback = new PushbackReader(reader, 1);
        int first = pushback.read();
        if (first != -1 && first != BYTE_ORDER_MARK) {
            pushback.unread(first);
        }
        return pushback;
    }

    private static @Nullable String trimmed(Properties properties, String key) {
        String raw = properties.getProperty(key);
        return raw == null ? null : raw.trim();
    }

    private static boolean blank(@Nullable String trimmed) {
        return trimmed == null || trimmed.isEmpty();
    }

    /** Only for a key that passed its own check, so the value is an integer. */
    private static int intOr(Properties properties, String key, int fallback) {
        String value = trimmed(properties, key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    /** Exactly one of {@link #rules()} and a non-empty {@link #errors()}. */
    static final class Result {
        private final @Nullable AppRules rules;
        private final List<String> errors;

        private Result(@Nullable AppRules rules, List<String> errors) {
            this.rules = rules;
            this.errors = errors;
        }

        static Result valid(AppRules rules) {
            return new Result(rules, Collections.<String>emptyList());
        }

        static Result invalid(List<String> errors) {
            return new Result(null, Collections.unmodifiableList(new ArrayList<String>(errors)));
        }

        @Nullable
        AppRules rules() {
            return rules;
        }

        /** Each reads {@code <key>: <reason>}, or only the reason when the whole file cannot be parsed. */
        List<String> errors() {
            return errors;
        }
    }
}
