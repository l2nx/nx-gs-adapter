package app.l2nx.gs.adapter.api.kafka.commands.ban;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class BanLists {

    private BanLists() {}

    static <T> List<T> freeze(@Nullable List<T> src, String field) {
        if (src == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        // contains(null) throws on immutable lists (List.of), so scan by hand.
        for (T entry : src) {
            if (entry == null) {
                throw new IllegalArgumentException(field + " must not hold null");
            }
        }
        return Collections.unmodifiableList(new ArrayList<T>(src));
    }
}
