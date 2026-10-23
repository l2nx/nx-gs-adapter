package app.l2nx.gs.commons.identifiers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientIdentifiersTest {

    @Nested
    class Hwid {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @NullAndEmptySource
        @ValueSource(
                strings = {
                    " ",
                    "\t \n",
                    "n/a",
                    "N/A",
                    " N/a ",
                    "n\\a",
                    "N\\A",
                    "na",
                    "NA",
                    "n.a.",
                    "N.A.",
                    "none",
                    "NONE",
                    "null",
                    "NULL",
                    "nil",
                    "Nil",
                    "unknown",
                    "Unknown",
                    "undefined",
                    "UNDEFINED",
                    "disconnected",
                    "Disconnected",
                    "-",
                    "0",
                    "00000000",
                    " 000 ",
                    "--",
                    "-----",
                    "0-0",
                    "00000000-0000-0000-0000-000000000000",
                    "00:00:00:00:00:00",
                    "00-00-00-00-00-00",
                    "0.0.0.0",
                    "0000 0000"
                })
        void hwid_shouldReturnNull_whenPlaceholder(String raw) {
            assertNull(ClientIdentifiers.hwid(raw));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource(
                delimiter = '|',
                value = {
                    "AbCdEf0123456789|AbCdEf0123456789",
                    "'  a1b2c3  '|a1b2c3",
                    "00a|00a",
                    "n/a/b|n/a/b",
                    "00000000-0000-0000-0000-000000000001|00000000-0000-0000-0000-000000000001",
                    "::|::"
                })
        void hwid_shouldReturnTrimmedVerbatim_whenRealValue(String raw, String expected) {
            assertEquals(expected, ClientIdentifiers.hwid(raw));
        }
    }

    @Nested
    class Ip {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @NullAndEmptySource
        @ValueSource(
                strings = {
                    " ",
                    "N/A",
                    "n\\a",
                    "na",
                    "n.a.",
                    "none",
                    "null",
                    "nil",
                    "unknown",
                    "undefined",
                    "disconnected",
                    "-",
                    "---",
                    "0",
                    "000",
                    "0.0.0.0",
                    " 0.0.0.0 ",
                    "::",
                    " :: ",
                    "::0",
                    "0:0:0:0:0:0:0:0",
                    "0000:0000:0000:0000:0000:0000:0000:0000",
                    "::ffff:0.0.0.0",
                    "::FFFF:0.0.0.0",
                    "0:0:0:0:0:ffff:0.0.0.0",
                    "::ffff:0:0"
                })
        void ip_shouldReturnNull_whenPlaceholder(String raw) {
            assertNull(ClientIdentifiers.ip(raw));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource(
                delimiter = '|',
                value = {
                    "192.168.0.1|192.168.0.1",
                    "' 10.0.0.1 '|10.0.0.1",
                    "::1|::1",
                    "2001:DB8::1|2001:DB8::1",
                    "0.0.0.1|0.0.0.1",
                    "::ffff:10.0.0.1|::ffff:10.0.0.1",
                    "::FFFF:0.0.0.1|::FFFF:0.0.0.1",
                    "ffff:0.0.0.0|ffff:0.0.0.0",
                    "not-an-ip|not-an-ip"
                })
        void ip_shouldReturnTrimmedVerbatim_whenRealValue(String raw, String expected) {
            assertEquals(expected, ClientIdentifiers.ip(raw));
        }
    }
}
