package app.l2nx.gs.db.sync.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanSubjectTypes;
import app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDto;
import app.l2nx.gs.adapter.api.kafka.sync.db.character.CharacterDbDto;
import app.l2nx.gs.adapter.api.kafka.sync.db.character.CharacterLockDbDto;
import app.l2nx.gs.adapter.api.kafka.sync.db.character.WellKnownCharacterLockTypes;
import app.l2nx.gs.adapter.api.kafka.sync.db.clan.ClanDbDto;
import app.l2nx.gs.kafka.NxGsonAdapters;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DbDtoIdentifiersTest {

    @Nested
    class Ban {

        @ParameterizedTest(name = "[{index}] {0}={1}")
        @CsvSource({"IP,N/A", "IP,0.0.0.0", "IP,'::'", "IP,' '", "HWID,0", "HWID,-", "HWID,unknown"})
        void normalize_shouldNullSubjectValue_whenIdentifierIsPlaceholder(String subjectType, String subjectValue) {
            BanDbDto ban = ban(subjectType, subjectValue);

            assertNulled(ban, (BanDbDto) DbDtoIdentifiers.normalize(ban));
        }

        @Test
        void normalize_shouldTrimSubjectValue_whenIdentifierIsReal() {
            BanDbDto actual = (BanDbDto) DbDtoIdentifiers.normalize(ban(WellKnownBanSubjectTypes.IP, " 10.0.0.1 "));

            JsonObject wire = wire(actual);
            assertEquals("10.0.0.1", actual.getSubjectValue());
            assertEquals("10.0.0.1", wire.get("subjectValue").getAsString());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {WellKnownBanSubjectTypes.CHARACTER, WellKnownBanSubjectTypes.ACCOUNT, "CUSTOM"})
        void normalize_shouldKeepSubjectValue_whenSubjectTypeIsNotAnIdentifier(String subjectType) {
            BanDbDto ban = ban(subjectType, "0");

            assertSame(ban, DbDtoIdentifiers.normalize(ban));
        }

        @Test
        void normalize_shouldReturnSameInstance_whenIdentifierIsClean() {
            BanDbDto ban = ban(WellKnownBanSubjectTypes.HWID, "a1b2c3");

            assertSame(ban, DbDtoIdentifiers.normalize(ban));
        }

        private BanDbDto ban(String subjectType, String subjectValue) {
            return BanDbDto.builder()
                    .id(1L)
                    .subjectType(subjectType)
                    .subjectValue(subjectValue)
                    .banType("CHAT")
                    .build();
        }

        private void assertNulled(BanDbDto ban, BanDbDto actual) {
            JsonObject wire = wire(actual);
            assertNull(actual.getSubjectValue());
            assertEquals(ban.toBuilder().subjectValue(null).build(), actual);
            assertEquals(ban.getSubjectType(), wire.get("subjectType").getAsString());
            assertFalse(wire.has("subjectValue"));
        }

        private JsonObject wire(BanDbDto ban) {
            return NxGsonAdapters.builder().create().toJsonTree(ban).getAsJsonObject();
        }
    }

    @Nested
    class CharacterLocks {

        @ParameterizedTest(name = "[{index}] {0}={1}")
        @CsvSource({"IP,N/A", "IP,0.0.0.0", "HWID,0000", "HWID,none", "ITEM,'-'", "ITEM,' '"})
        void normalize_shouldNullLockValue_whenIdentifierIsPlaceholder(String lockType, String lockValue) {
            CharacterLockDbDto kept = new CharacterLockDbDto(WellKnownCharacterLockTypes.HWID, "a1b2c3");
            CharacterDbDto character = character(Arrays.asList(kept, new CharacterLockDbDto(lockType, lockValue)));

            CharacterDbDto actual = (CharacterDbDto) DbDtoIdentifiers.normalize(character);

            List<CharacterLockDbDto> expected = Arrays.asList(kept, new CharacterLockDbDto(lockType, null));
            assertEquals(expected, actual.getLocks());
            assertEquals(character.toBuilder().locks(expected).build(), actual);
        }

        @Test
        void normalize_shouldTrimLockValue_whenIdentifierIsReal() {
            CharacterDbDto character =
                    character(Arrays.asList(new CharacterLockDbDto(WellKnownCharacterLockTypes.IP, " 10.0.0.1 ")));

            CharacterDbDto actual = (CharacterDbDto) DbDtoIdentifiers.normalize(character);

            assertEquals("10.0.0.1", actual.getLocks().get(0).getLockValue());
        }

        @Test
        void normalize_shouldKeepLockValue_whenLockTypeIsNotAnIdentifier() {
            CharacterDbDto character = character(Arrays.asList(new CharacterLockDbDto("CUSTOM", "0")));

            assertSame(character, DbDtoIdentifiers.normalize(character));
        }

        @Test
        void normalize_shouldReturnSameInstance_whenLocksAreClean() {
            CharacterDbDto character =
                    character(Arrays.asList(new CharacterLockDbDto(WellKnownCharacterLockTypes.IP, "10.0.0.1")));

            assertSame(character, DbDtoIdentifiers.normalize(character));
        }

        @Test
        void normalize_shouldReturnSameInstance_whenLocksAreNotSynced() {
            CharacterDbDto character = character(null);

            assertSame(character, DbDtoIdentifiers.normalize(character));
        }

        private CharacterDbDto character(List<CharacterLockDbDto> locks) {
            return CharacterDbDto.builder().id(7L).name("Hero").locks(locks).build();
        }
    }

    @Test
    void normalize_shouldReturnSameInstance_forDtoWithoutClientIdentifiers() {
        ClanDbDto clan = ClanDbDto.builder().id(1L).name("Clan").build();

        assertSame(clan, DbDtoIdentifiers.normalize(clan));
    }
}
