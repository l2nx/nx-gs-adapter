package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class DroppedLineTest {

    @Test
    void constructor_shouldAcceptBuyLine_whenItemIdNull() {
        DroppedLine actual = new DroppedLine(2, null, DroppedLineReason.BAD_COUNT.name());

        assertEquals(2, actual.getLineIndex());
        assertNull(actual.getItemId());
    }

    @Test
    void constructor_shouldAcceptSellLine_whenItemIdPresent() {
        DroppedLine actual = new DroppedLine(1, 100, DroppedLineReason.EQUIPPED.name());

        assertEquals(1, actual.getLineIndex());
        assertEquals(Integer.valueOf(100), actual.getItemId());
    }

    @Test
    void constructor_shouldReject_whenLineIndexNegative() {
        assertThrows(
                IllegalArgumentException.class, () -> new DroppedLine(-1, null, DroppedLineReason.NOT_FOUND.name()));
    }

    @Test
    void constructor_shouldReject_whenReasonNull() {
        assertThrows(IllegalArgumentException.class, () -> new DroppedLine(0, 100, null));
    }

    @Test
    void constructor_shouldReject_whenReasonBlank() {
        assertThrows(IllegalArgumentException.class, () -> new DroppedLine(0, 100, " "));
    }

    @Test
    void build_shouldReject_whenLineIndexUnset() {
        DroppedLine.Builder builder = DroppedLine.builder().itemId(1).reason(DroppedLineReason.NOT_FOUND.name());

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        DroppedLine original = DroppedLine.builder()
                .lineIndex(0)
                .itemId(100)
                .reason(DroppedLineReason.NOT_AVAILABLE.name())
                .build();

        assertEquals(original, original.toBuilder().build());
        assertEquals(original.hashCode(), original.toBuilder().build().hashCode());
    }

    @Test
    void gson_shouldOmitItemId_forABuyLine() {
        String actual = new Gson().toJson(new DroppedLine(3, null, DroppedLineReason.ADENA.name()));

        assertEquals("{\"lineIndex\":3,\"reason\":\"ADENA\"}", actual);
    }
}
