package app.l2nx.gs.commons.identifiers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One definition of "no value" for hwid / IP: a placeholder ({@code N/A}, {@code 0}, zero-filled UUID, blank) counted
 * as a value ties unrelated players together in per-device limits, bans and unique counts. A real value is returned
 * trimmed and otherwise verbatim.
 */
public final class ClientIdentifiers {

    private static final Set<String> PLACEHOLDERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "n/a", "n\\a", "na", "n.a.", "none", "null", "nil", "unknown", "undefined", "disconnected", "-")));

    private static final String IPV4_MAPPED_GROUP = "ffff:";

    private ClientIdentifiers() {}

    public static @Nullable String hwid(@Nullable String raw) {
        return commonValue(raw);
    }

    /**
     * Also treats the unspecified addresses as placeholders: {@code ::} and the IPv4-mapped {@code ::ffff:0.0.0.0},
     * however their zero groups are written.
     */
    public static @Nullable String ip(@Nullable String raw) {
        String value = commonValue(raw);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return "::".equals(lower) || unspecifiedIpv4Mapped(lower) ? null : value;
    }

    private static @Nullable String commonValue(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()
                || PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT))
                || zeroFilled(value)
                || onlyDashes(value)) {
            return null;
        }
        return value;
    }

    /** Only '0' digits once the separators of UUIDs, MAC and IP notations are removed, and at least one of them. */
    private static boolean zeroFilled(String value) {
        boolean zero = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '0') {
                zero = true;
            } else if (!separator(c)) {
                return false;
            }
        }
        return zero;
    }

    private static boolean separator(char c) {
        return c == '-' || c == ':' || c == '.' || Character.isWhitespace(c);
    }

    private static boolean onlyDashes(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean unspecifiedIpv4Mapped(String lower) {
        int mapped = lower.lastIndexOf(IPV4_MAPPED_GROUP);
        if (mapped <= 0) {
            return false;
        }
        String prefix = lower.substring(0, mapped);
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c != '0' && c != ':') {
                return false;
            }
        }
        return prefix.charAt(prefix.length() - 1) == ':'
                && zeroFilled(lower.substring(mapped + IPV4_MAPPED_GROUP.length()));
    }
}
