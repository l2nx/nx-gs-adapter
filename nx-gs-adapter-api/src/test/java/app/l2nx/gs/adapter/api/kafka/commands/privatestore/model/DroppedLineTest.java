package app.l2nx.gs.adapter.api.kafka.commands.privatestore.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DroppedLineTest {

    @Test
    void constructor_shouldAcceptSellLine_whenItemIdPresent() {
        DroppedLine actual = new DroppedLine(1, 100, DroppedLineReason.EQUIPPED.name());

        assertEquals(1, actual.getLineIndex());
        assertEquals(Integer.valueOf(100), actual.getItemId());
    }

    static Stream<Arguments> invalidLines() {
        String reason = DroppedLineReason.NOT_FOUND.name();
        return Stream.of(
                Arguments.of("lineIndex negative", (Executable) () -> new DroppedLine(-1, null, reason)),
                Arguments.of("lineIndex unset", (Executable)
                        () -> DroppedLine.builder().itemId(1).reason(reason).build()),
                Arguments.of("reason null", (Executable) () -> new DroppedLine(0, 100, null)),
                Arguments.of("reason blank", (Executable) () -> new DroppedLine(0, 100, " ")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLines")
    void constructor_shouldReject_whenLineInvalid(String name, Executable build) {
        assertThrows(IllegalArgumentException.class, build);
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
        assertEquals(DroppedLineReason.NOT_AVAILABLE.name(), original.getReason());
    }

    @Test
    void gson_shouldOmitItemId_forABuyLine() {
        String actual = new Gson().toJson(new DroppedLine(3, null, DroppedLineReason.ADENA.name()));

        assertEquals("{\"lineIndex\":3,\"reason\":\"ADENA\"}", actual);
    }
}
