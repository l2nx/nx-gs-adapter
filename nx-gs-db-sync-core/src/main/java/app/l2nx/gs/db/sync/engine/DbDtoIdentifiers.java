package app.l2nx.gs.db.sync.engine;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.WellKnownBanTargetTypes;
import app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDto;
import app.l2nx.gs.adapter.api.kafka.sync.db.character.CharacterDbDto;
import app.l2nx.gs.adapter.api.kafka.sync.db.character.CharacterLockDbDto;
import app.l2nx.gs.adapter.api.kafka.sync.db.character.WellKnownCharacterLockTypes;
import app.l2nx.gs.commons.identifiers.ClientIdentifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Nulls hwid / IP placeholders in mapped db DTOs, whatever the host's mapper did. The CRC covers the raw columns, so a
 * change between two placeholders still re-emits an identical payload.
 */
final class DbDtoIdentifiers {

    private DbDtoIdentifiers() {}

    /** Returns the same instance when nothing changes. */
    static Object normalize(Object dto) {
        if (dto instanceof BanDbDto) {
            return normalizeBan((BanDbDto) dto);
        }
        if (dto instanceof CharacterDbDto) {
            return normalizeCharacter((CharacterDbDto) dto);
        }
        return dto;
    }

    private static BanDbDto normalizeBan(BanDbDto ban) {
        String targetType = ban.getTargetType();
        String raw = ban.getTargetValue();
        String value;
        if (WellKnownBanTargetTypes.IP.equals(targetType)) {
            value = ClientIdentifiers.ip(raw);
        } else if (WellKnownBanTargetTypes.HWID.equals(targetType)) {
            value = ClientIdentifiers.hwid(raw);
        } else {
            return ban;
        }
        return Objects.equals(value, raw)
                ? ban
                : ban.toBuilder().targetValue(value).build();
    }

    private static CharacterDbDto normalizeCharacter(CharacterDbDto character) {
        List<CharacterLockDbDto> locks = character.getLocks();
        if (locks == null) {
            return character;
        }
        List<CharacterLockDbDto> normalized = null;
        for (int i = 0; i < locks.size(); i++) {
            CharacterLockDbDto lock = locks.get(i);
            CharacterLockDbDto next = normalizeLock(lock);
            if (next != lock && normalized == null) {
                normalized = new ArrayList<CharacterLockDbDto>(locks.subList(0, i));
            }
            if (normalized != null) {
                normalized.add(next);
            }
        }
        return normalized == null
                ? character
                : character.toBuilder().locks(normalized).build();
    }

    private static CharacterLockDbDto normalizeLock(CharacterLockDbDto lock) {
        String raw = lock.getLockValue();
        String value;
        String lockType = lock.getLockType();
        if (WellKnownCharacterLockTypes.IP.equals(lockType)) {
            value = ClientIdentifiers.ip(raw);
        } else if (WellKnownCharacterLockTypes.HWID.equals(lockType)
                || WellKnownCharacterLockTypes.ITEM.equals(lockType)) {
            value = ClientIdentifiers.hwid(raw);
        } else {
            return lock;
        }
        return Objects.equals(value, raw)
                ? lock
                : lock.toBuilder().lockValue(value).build();
    }
}
