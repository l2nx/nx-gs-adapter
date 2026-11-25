package app.l2nx.gs.adapter.core.kafka.gson;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommand;
import com.google.gson.Gson;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdapterGsonTest {

    private final Gson gson = AdapterGson.create();

    @Nested
    class BanCommandDeserialization {

        @Test
        void create_shouldDecodeInstantExpiresAt_onNonPermanentBan() {
            String wire = "{\"subjectType\":\"CHARACTER\",\"subjectValue\":\"269234946\","
                    + "\"banType\":\"GAME_LOGIN\",\"permanent\":false,"
                    + "\"expiresAt\":\"2026-07-03T01:34:33.873516885Z\",\"publicReason\":null,"
                    + "\"issuedBy\":\"01988063-1582-7c13-a4dd-d9db4eeeaa21\"}";

            BanCommand cmd = gson.fromJson(wire, BanCommand.class);

            assertEquals("GAME_LOGIN", cmd.getBanType());
            assertFalse(cmd.isPermanent());
            assertEquals(Instant.parse("2026-07-03T01:34:33.873516885Z"), cmd.getExpiresAt());
        }

        @Test
        void create_shouldDecodeNullExpiresAt_onPermanentJailBan() {
            String wire = "{\"subjectType\":\"CHARACTER\",\"subjectValue\":\"269234946\","
                    + "\"banType\":\"JAIL\",\"permanent\":true,\"expiresAt\":null,"
                    + "\"publicReason\":null,\"issuedBy\":\"01988063-1582-7c13-a4dd-d9db4eeeaa21\"}";

            BanCommand cmd = gson.fromJson(wire, BanCommand.class);

            assertEquals("JAIL", cmd.getBanType());
            assertTrue(cmd.isPermanent());
            assertNull(cmd.getExpiresAt());
        }
    }
}
