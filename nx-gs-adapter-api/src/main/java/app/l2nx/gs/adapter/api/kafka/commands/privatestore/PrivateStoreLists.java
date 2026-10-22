package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.WantedLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class PrivateStoreLists {

    private PrivateStoreLists() {}

    static <T> List<T> freeze(@Nullable List<T> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(src));
    }

    static List<SellLine> requireDistinctLines(@Nullable List<SellLine> lines) {
        requireNonEmptyWithoutNulls(lines);
        Integer duplicate = SellLine.firstDuplicateItemId(lines);
        if (duplicate != null) {
            throw new IllegalArgumentException("lines must not repeat itemId (duplicate " + duplicate + ")");
        }
        return freeze(lines);
    }

    static List<WantedLine> requireDistinctWantedLines(@Nullable List<WantedLine> lines) {
        requireNonEmptyWithoutNulls(lines);
        Long duplicate = WantedLine.firstDuplicateItemTemplateId(lines);
        if (duplicate != null) {
            throw new IllegalArgumentException("lines must not repeat itemTemplateId (duplicate " + duplicate + ")");
        }
        return freeze(lines);
    }

    private static void requireNonEmptyWithoutNulls(@Nullable List<?> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines is required and must be non-empty");
        }
        for (Object line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("lines must not contain null elements");
            }
        }
    }
}
