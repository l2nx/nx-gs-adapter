package app.l2nx.gs.adapter.api.kafka.commands.captcha;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import org.junit.jupiter.api.Test;

class SendCaptchaCommandTest {

    @Test
    void builder_shouldRoundtripFields() {
        SendCaptchaCommand cmd = SendCaptchaCommand.builder()
                .characterId(42L)
                .issuedBy("sac-sentinel")
                .staffNotes("episode 7")
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, cmd.getCharacterId().longValue());
        assertEquals("sac-sentinel", cmd.getIssuedBy());
        assertEquals("episode 7", cmd.getStaffNotes());
        assertEquals(cmd, cmd.toBuilder().build());
        assertEquals(cmd.hashCode(), cmd.toBuilder().build().hashCode());
        assertEquals(TestInitiators.USER, cmd.getInitiator());
    }

    @Test
    void constructor_shouldRejectNullCharacterId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new SendCaptchaCommand(null, null, null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("characterId"));
    }
}
