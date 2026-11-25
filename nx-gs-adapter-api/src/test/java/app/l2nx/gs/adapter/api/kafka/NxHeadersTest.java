package app.l2nx.gs.adapter.api.kafka;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class NxHeadersTest {

    @Test
    void names_shouldCarryNoPrefix() {
        assertEquals("Server-Id", NxHeaders.SERVER_ID);
        assertEquals("Message-Type", NxHeaders.MESSAGE_TYPE);
        assertEquals("Correlation-Id", NxHeaders.CORRELATION_ID);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "00000000-0000-0000-0000-000000000000, 00000000000000000000000000000000",
        "ffffffff-ffff-ffff-ffff-ffffffffffff, ffffffffffffffffffffffffffffffff",
        "01234567-89ab-cdef-fedc-ba9876543210, 0123456789abcdeffedcba9876543210",
        "550e8400-e29b-41d4-a716-446655440000, 550e8400e29b41d4a716446655440000"
    })
    void encodeUuid_shouldProduceMostSigThenLeastSigBigEndian_andDecodeBack(String literal, String expectedHex) {
        UUID uuid = UUID.fromString(literal);

        byte[] encoded = NxHeaders.encodeUuid(uuid);

        assertArrayEquals(bytes(expectedHex), encoded);
        assertEquals(uuid, NxHeaders.decodeUuid(encoded));
    }

    private static byte[] bytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static Stream<Arguments> invalidInputs() {
        Stream<Arguments> nulls = Stream.of(
                Arguments.of(
                        "encode null",
                        (Executable) () -> NxHeaders.encodeUuid(null),
                        NullPointerException.class,
                        "uuid"),
                Arguments.of(
                        "decode null",
                        (Executable) () -> NxHeaders.decodeUuid(null),
                        IllegalArgumentException.class,
                        "UUID header value must not be null"));
        Stream<Arguments> lengths = Stream.of(0, 1, 8, 15, 17, 32)
                .map(len -> Arguments.of(
                        "decode " + len + " bytes",
                        (Executable) () -> NxHeaders.decodeUuid(new byte[len]),
                        IllegalArgumentException.class,
                        "UUID header value must be 16 bytes, got " + len));
        return Stream.concat(nulls, lengths);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void codec_shouldThrow_whenInputIsInvalid(
            String input, Executable call, Class<? extends Throwable> expected, String message) {
        Throwable ex = assertThrows(expected, call);

        assertEquals(message, ex.getMessage());
    }
}
