package app.l2nx.gs.adapter.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Lenient token lookup for open vocabulary enums: an unknown token is {@code null}, never an exception. The wire token
 * is {@link Keyed#key()} for a {@link Keyed} enum, otherwise {@code name()}.
 */
public final class Vocabularies {

    public interface Keyed {
        String key();
    }

    private static final ClassValue<Map<String, Object>> TOKENS = new ClassValue<Map<String, Object>>() {
        @Override
        protected Map<String, Object> computeValue(Class<?> type) {
            Map<String, Object> byToken = new HashMap<>();
            for (Object constant : type.getEnumConstants()) {
                String token = constant instanceof Keyed ? ((Keyed) constant).key() : ((Enum<?>) constant).name();
                byToken.put(token, constant);
            }
            return Collections.unmodifiableMap(byToken);
        }
    };

    private Vocabularies() {}

    public static <E extends Enum<E>> @Nullable E fromToken(Class<E> type, @Nullable String token) {
        if (token == null) {
            return null;
        }
        return type.cast(TOKENS.get(type).get(token));
    }
}
