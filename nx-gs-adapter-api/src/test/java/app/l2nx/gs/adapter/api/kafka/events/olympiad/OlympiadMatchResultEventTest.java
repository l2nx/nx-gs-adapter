package app.l2nx.gs.adapter.api.kafka.events.olympiad;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.model.OlympiadGameType;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.model.OlympiadMatchReason;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.model.OlympiadMatchResult;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class OlympiadMatchResultEventTest {

    @Test
    void build_shouldLeaveOptionalFieldsNull_whenAbsent() {
        OlympiadMatchResultEvent event = newBuilder()
                .clanId(null)
                .opponentClanId(null)
                .reason(OlympiadMatchReason.BOTH_DEFAULTED)
                .result(OlympiadMatchResult.DRAW)
                .fightStartedAt(null)
                .fightDurationSec(0L)
                .damageDealt(0)
                .opponentDamageDealt(0)
                .build();

        assertNull(event.getClanId());
        assertNull(event.getOpponentClanId());
        assertNull(event.getClazz());
        assertNull(event.getOpponentClazz());
        assertNull(event.getFightStartedAt());
        assertEquals(0L, event.getFightDurationSec());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        UUID eventId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        OlympiadMatchResultEvent original = OlympiadMatchResultEvent.builder()
                .eventId(eventId)
                .matchId(matchId)
                .olympiadCycle(7)
                .gameType(OlympiadGameType.CLASSED)
                .characterId(268437521L)
                .classId(88)
                .clazz(CharacterClass.DUELIST)
                .clanId(101L)
                .opponentCharacterId(268437522L)
                .opponentClassId(92)
                .opponentClazz(CharacterClass.SAGITTARIUS)
                .opponentClanId(102L)
                .result(OlympiadMatchResult.WIN)
                .reason(OlympiadMatchReason.NORMAL)
                .pointsBefore(40)
                .pointsAfter(43)
                .damageDealt(12_500)
                .opponentDamageDealt(9_400)
                .fightStartedAt(Instant.parse("2026-06-01T12:00:00Z"))
                .fightDurationSec(178L)
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        OlympiadMatchResultEvent copy = original.toBuilder().build();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(matchId, copy.getMatchId());
        assertEquals(7, copy.getOlympiadCycle());
        assertEquals(OlympiadGameType.CLASSED, copy.getGameType());
        assertEquals(268437521L, copy.getCharacterId());
        assertEquals(88, copy.getClassId());
        assertEquals(CharacterClass.DUELIST, copy.getClazz());
        assertEquals(Long.valueOf(101L), copy.getClanId());
        assertEquals(268437522L, copy.getOpponentCharacterId());
        assertEquals(92, copy.getOpponentClassId());
        assertEquals(CharacterClass.SAGITTARIUS, copy.getOpponentClazz());
        assertEquals(Long.valueOf(102L), copy.getOpponentClanId());
        assertEquals(OlympiadMatchResult.WIN, copy.getResult());
        assertEquals(OlympiadMatchReason.NORMAL, copy.getReason());
        assertEquals(40, copy.getPointsBefore());
        assertEquals(43, copy.getPointsAfter());
        assertEquals(12_500, copy.getDamageDealt());
        assertEquals(9_400, copy.getOpponentDamageDealt());
        assertEquals(Instant.parse("2026-06-01T12:00:00Z"), copy.getFightStartedAt());
        assertEquals(178L, copy.getFightDurationSec());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(OlympiadMatchResultEvent.class).verify();
    }

    private static OlympiadMatchResultEvent.Builder newBuilder() {
        return OlympiadMatchResultEvent.builder()
                .eventId(UUID.randomUUID())
                .matchId(UUID.randomUUID())
                .olympiadCycle(7)
                .gameType(OlympiadGameType.CLASSED)
                .characterId(268437521L)
                .classId(88)
                .clanId(101L)
                .opponentCharacterId(268437522L)
                .opponentClassId(92)
                .opponentClanId(102L)
                .result(OlympiadMatchResult.WIN)
                .reason(OlympiadMatchReason.NORMAL)
                .pointsBefore(40)
                .pointsAfter(43)
                .damageDealt(12_500)
                .opponentDamageDealt(9_400)
                .fightStartedAt(Instant.parse("2026-06-01T12:00:00Z"))
                .fightDurationSec(178L);
    }
}
