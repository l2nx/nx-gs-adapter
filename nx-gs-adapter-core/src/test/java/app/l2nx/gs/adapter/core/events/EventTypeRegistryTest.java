package app.l2nx.gs.adapter.core.events;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.events.account.AccountAuthAttemptEvent;
import app.l2nx.gs.adapter.api.kafka.events.account.AuthOutcomes;
import app.l2nx.gs.adapter.api.kafka.events.castle.CastleSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.castle.SiegeFinishedEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.AppSessionsSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.CharacterDeathEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResult;
import app.l2nx.gs.adapter.api.kafka.events.characterlog.CharacterLogEvent;
import app.l2nx.gs.adapter.api.kafka.events.chat.ChatMessageEvent;
import app.l2nx.gs.adapter.api.kafka.events.gameevents.GameEventSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailAcceptedEvent;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailCancelledEvent;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailDeletedEvent;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailReadEvent;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailReturnedEvent;
import app.l2nx.gs.adapter.api.kafka.events.mail.MailSentEvent;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.*;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.model.OlympiadGameType;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.model.OlympiadMatchReason;
import app.l2nx.gs.adapter.api.kafka.events.olympiad.model.OlympiadMatchResult;
import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.PremiumPurchaseEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStorePurchaseEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStoreStateEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.PrivateStoreSide;
import app.l2nx.gs.adapter.api.kafka.events.privatetrade.PrivateTradeFinishedEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatetrade.TradeParty;
import app.l2nx.gs.adapter.api.kafka.events.raid.kill.RaidKillEvent;
import app.l2nx.gs.adapter.api.kafka.events.raid.respawn.BossRespawnSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.server.online.ServerOnlineSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.server.online.ServerStartedEvent;
import app.l2nx.gs.adapter.api.kafka.events.server.online.ServerStoppingEvent;
import app.l2nx.gs.adapter.api.kafka.events.server.online.model.ServerOnlineBucket;
import app.l2nx.gs.adapter.api.kafka.events.sync.ResyncCompletedEvent;
import app.l2nx.gs.commons.UUIDv7;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EventTypeRegistryTest {

    private static Map<ServerOnlineBucket, Long> buckets(long total) {
        Map<ServerOnlineBucket, Long> buckets = new EnumMap<ServerOnlineBucket, Long>(ServerOnlineBucket.class);
        buckets.put(ServerOnlineBucket.TOTAL, total);
        buckets.put(ServerOnlineBucket.UNIQUE, total);
        return buckets;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void lookup_shouldReturnTheFamilyAndTheMessageType(Class<?> type, String family, String messageType) {
        EventTypeBinding binding = new EventTypeRegistry().lookup(type);

        assertNotNull(binding);
        assertEquals(family, binding.familyKey());
        assertEquals(messageType, binding.messageType());
    }

    private static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of(PremiumPurchaseEvent.class, "premiumpurchase", "PremiumPurchaseEvent"),
                Arguments.of(ServerOnlineSnapshotEvent.class, "serveronline", "ServerOnlineSnapshotEvent"),
                Arguments.of(ServerStartedEvent.class, "serveronline", "ServerStartedEvent"),
                Arguments.of(ServerStoppingEvent.class, "serveronline", "ServerStoppingEvent"),
                Arguments.of(GameEventSnapshotEvent.class, "gameevents", "GameEventSnapshotEvent"),
                Arguments.of(PrivateStorePurchaseEvent.class, "privatestore", "PrivateStorePurchaseEvent"),
                Arguments.of(PrivateStoreStateEvent.class, "privatestore", "PrivateStoreStateEvent"),
                Arguments.of(CharacterPresenceEvent.class, "character", "CharacterPresenceEvent"),
                Arguments.of(CharacterDeathEvent.class, "character", "CharacterDeathEvent"),
                Arguments.of(CharacterLogEvent.class, "characterlog", "CharacterLogEvent"),
                Arguments.of(RaidKillEvent.class, "raid", "RaidKillEvent"),
                Arguments.of(BossRespawnSnapshotEvent.class, "raid", "BossRespawnSnapshotEvent"),
                Arguments.of(CastleSnapshotEvent.class, "castle", "CastleSnapshotEvent"),
                Arguments.of(SiegeFinishedEvent.class, "castle", "SiegeFinishedEvent"),
                Arguments.of(MailSentEvent.class, "mail", "MailSentEvent"),
                Arguments.of(MailAcceptedEvent.class, "mail", "MailAcceptedEvent"),
                Arguments.of(MailCancelledEvent.class, "mail", "MailCancelledEvent"),
                Arguments.of(MailReturnedEvent.class, "mail", "MailReturnedEvent"),
                Arguments.of(MailReadEvent.class, "mail", "MailReadEvent"),
                Arguments.of(MailDeletedEvent.class, "mail", "MailDeletedEvent"),
                Arguments.of(PrivateTradeFinishedEvent.class, "privatetrade", "PrivateTradeFinishedEvent"),
                Arguments.of(OlympiadMatchResultEvent.class, "olympiad", "OlympiadMatchResultEvent"),
                Arguments.of(HeroGrantedEvent.class, "olympiad", "HeroGrantedEvent"),
                Arguments.of(ResyncCompletedEvent.class, "sync", "ResyncCompletedEvent"),
                Arguments.of(ChatMessageEvent.class, "chat", "ChatMessageEvent"),
                Arguments.of(AccountAuthAttemptEvent.class, "account", "AccountAuthAttemptEvent"));
    }

    @Test
    void lookup_shouldReturnNull_forUnregisteredType() {
        assertNull(new EventTypeRegistry().lookup(String.class));
    }

    @ParameterizedTest(name = "{0} by {2}")
    @MethodSource("keyedEvents")
    void partitionKeyExtractor_shouldReturnTheIdAsLongBytes(String type, Object event, long expectedId) {
        EventTypeBinding binding = new EventTypeRegistry().lookup(event.getClass());

        byte[] key = binding.partitionKeyExtractor().apply(event);

        assertEquals(8, key.length);
        assertEquals(expectedId, ByteBuffer.wrap(key).getLong());
    }

    private static Stream<Arguments> keyedEvents() {
        return Stream.of(
                keyed(
                        PremiumPurchaseEvent.builder()
                                .eventId(UUIDv7.generate())
                                .characterId(0xDEADBEEFL)
                                .build(),
                        0xDEADBEEFL),
                keyed(
                        PrivateStoreStateEvent.builder()
                                .eventId(UUIDv7.generate())
                                .characterId(42L)
                                .build(),
                        42L),
                keyed(
                        CharacterPresenceEvent.builder()
                                .eventId(UUIDv7.generate())
                                .characterId(0xCAFEBABEL)
                                .online(true)
                                .source(SessionSource.CLIENT)
                                .build(),
                        0xCAFEBABEL),
                keyed(
                        RaidKillEvent.builder()
                                .eventId(UUIDv7.generate())
                                .bossNpcId(29028)
                                .build(),
                        29028L),
                keyed(
                        MailSentEvent.builder()
                                .eventId(UUIDv7.generate())
                                .mailId(0xCAFEBABEL)
                                .senderCharacterId(0L)
                                .receiverCharacterId(2L)
                                .subject("s")
                                .expiresAt(Instant.EPOCH)
                                .build(),
                        0xCAFEBABEL),
                keyed(
                        MailAcceptedEvent.builder()
                                .eventId(UUIDv7.generate())
                                .mailId(0xBEEFL)
                                .claimedByCharacterId(1L)
                                .build(),
                        0xBEEFL),
                keyed(
                        MailCancelledEvent.builder()
                                .eventId(UUIDv7.generate())
                                .mailId(0xBEEFL)
                                .cancelledByCharacterId(1L)
                                .build(),
                        0xBEEFL),
                keyed(
                        MailReturnedEvent.builder()
                                .eventId(UUIDv7.generate())
                                .mailId(0xBEEFL)
                                .returnedToSenderId(1L)
                                .build(),
                        0xBEEFL),
                keyed(
                        OlympiadMatchResultEvent.builder()
                                .eventId(UUIDv7.generate())
                                .matchId(UUIDv7.generate())
                                .olympiadCycle(7)
                                .gameType(OlympiadGameType.CLASSED)
                                .characterId(0xCAFEBABEL)
                                .classId(88)
                                .opponentCharacterId(1L)
                                .opponentClassId(92)
                                .result(OlympiadMatchResult.WIN)
                                .reason(OlympiadMatchReason.NORMAL)
                                .pointsBefore(40)
                                .pointsAfter(43)
                                .build(),
                        0xCAFEBABEL),
                keyed(
                        HeroGrantedEvent.builder()
                                .eventId(UUIDv7.generate())
                                .characterId(0xCAFEBABEL)
                                .classId(88)
                                .olympiadCycle(7)
                                .build(),
                        0xCAFEBABEL));
    }

    private static Arguments keyed(Object event, long expectedId) {
        return Arguments.of(event.getClass().getSimpleName(), event, expectedId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unkeyedEvents")
    void partitionKeyExtractor_shouldReturnNull_whenTheTypeIsUnkeyed(String type, Object event) {
        EventTypeBinding binding = new EventTypeRegistry().lookup(event.getClass());

        assertNull(binding.partitionKeyExtractor().apply(event));
    }

    private static Stream<Arguments> unkeyedEvents() {
        TradeParty empty = TradeParty.builder().characterId(1L).build();
        return Stream.of(
                unkeyed(ServerOnlineSnapshotEvent.builder()
                        .eventId(UUIDv7.generate())
                        .buckets(buckets(100L))
                        .build()),
                unkeyed(PrivateStorePurchaseEvent.builder()
                        .eventId(UUIDv7.generate())
                        .storeType(PrivateStoreSide.ASK)
                        .sellerId(1L)
                        .buyerId(2L)
                        .build()),
                unkeyed(PrivateTradeFinishedEvent.builder()
                        .eventId(UUIDv7.generate())
                        .tradeId(UUIDv7.generate())
                        .partyA(empty)
                        .partyB(empty)
                        .build()),
                unkeyed(ResyncCompletedEvent.builder()
                        .eventId(UUIDv7.generate())
                        .resyncId(UUIDv7.generate())
                        .entityName("item")
                        .cycleStartedAt(Instant.parse("2026-06-12T10:00:00Z"))
                        .completedAt(Instant.parse("2026-06-12T10:05:00Z"))
                        .build()));
    }

    private static Arguments unkeyed(Object event) {
        return Arguments.of(event.getClass().getSimpleName(), event);
    }

    @Test
    void knownFamilies_shouldListEveryFamily() {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "premiumpurchase",
                "serveronline",
                "gameevents",
                "privatestore",
                "character",
                "characterlog",
                "raid",
                "castle",
                "mail",
                "privatetrade",
                "olympiad",
                "sync",
                "chat",
                "account"));

        assertEquals(expected, new EventTypeRegistry().knownFamilies());
    }

    @Nested
    class Presence {

        private final EventTypeBinding binding = new EventTypeRegistry().lookup(CharacterPresenceEvent.class);

        private CharacterPresenceEvent.Builder storeEnd() {
            return CharacterPresenceEvent.builder()
                    .eventId(UUIDv7.generate())
                    .characterId(7L)
                    .online(false)
                    .source(SessionSource.CLIENT)
                    .activity(ActivityType.OFFLINE_PRIVATE_STORE)
                    .logoutReason(LogoutReason.SOLD_OUT)
                    .result(SessionResult.builder().overflow(3L).build());
        }

        @Test
        void wireNormalizer_shouldKeepResultAndActivity_whenItRebuildsTheEvent() {
            CharacterPresenceEvent event = storeEnd().ip("N/A").hwid("0").build();

            CharacterPresenceEvent actual =
                    (CharacterPresenceEvent) binding.wireNormalizer().apply(event);

            assertNotSame(event, actual);
            assertNull(actual.getIp());
            assertNull(actual.getHwid());
            assertEquals(event.getResult(), actual.getResult());
            assertEquals(ActivityType.OFFLINE_PRIVATE_STORE, actual.getActivity());
        }

        @Test
        void sendGuard_shouldKeepTheEvent_whenTheResultFits() {
            CharacterPresenceEvent event = storeEnd().build();

            assertSame(event, binding.sendGuard().apply(event));
        }

        @Test
        void sendGuard_shouldDropOnlyTheResult_whenItIsOverTheBudget() {
            CharacterPresenceEvent event =
                    storeEnd().result(SessionResultJsonTest.overBudget()).build();

            CharacterPresenceEvent actual =
                    (CharacterPresenceEvent) binding.sendGuard().apply(event);

            assertNull(actual.getResult());
            assertEquals(LogoutReason.SOLD_OUT, actual.getLogoutReason());
            assertEquals(event.getEventId(), actual.getEventId());
        }
    }

    @Test
    void partitionKey_shouldEncodeLowercasedAccountNameAsUtf8_forAccountAuthAttemptEvent() {
        EventTypeBinding binding = new EventTypeRegistry().lookup(AccountAuthAttemptEvent.class);
        AccountAuthAttemptEvent event = AccountAuthAttemptEvent.builder()
                .eventId(UUIDv7.generate().toString())
                .serverId(UUIDv7.generate().toString())
                .accountName("Olya")
                .clientIp("10.0.0.1")
                .outcome(AuthOutcomes.SUCCESS)
                .attemptedAt(Instant.parse("2026-06-01T12:00:00Z"))
                .build();

        byte[] key = binding.partitionKeyExtractor().apply(event);

        assertArrayEquals("olya".getBytes(java.nio.charset.StandardCharsets.UTF_8), key);
    }

    @Test
    void partitionKey_shouldPreserveNonAsciiAccountName_forAccountAuthAttemptEvent() {
        EventTypeBinding binding = new EventTypeRegistry().lookup(AccountAuthAttemptEvent.class);
        AccountAuthAttemptEvent event = AccountAuthAttemptEvent.builder()
                .eventId(UUIDv7.generate().toString())
                .serverId(UUIDv7.generate().toString())
                .accountName("Олья")
                .clientIp("10.0.0.1")
                .outcome(AuthOutcomes.SUCCESS)
                .attemptedAt(Instant.parse("2026-06-01T12:00:00Z"))
                .build();

        byte[] key = binding.partitionKeyExtractor().apply(event);

        assertArrayEquals("олья".getBytes(java.nio.charset.StandardCharsets.UTF_8), key);
    }

    @Nested
    class WireNormalizer {

        @ParameterizedTest(name = "[{index}] ip={0} hwid={1}")
        @CsvSource(
                nullValues = "NULL",
                value = {"N/A,0", "0.0.0.0,-", "' :: ',unknown", "'',' '"})
        void wireNormalizer_shouldNullPlaceholders_forCharacterPresenceEvent(String ip, String hwid) {
            EventTypeBinding binding = new EventTypeRegistry().lookup(CharacterPresenceEvent.class);
            CharacterPresenceEvent event =
                    presence().ip(ip).hwid(hwid).accountName("acc").build();

            CharacterPresenceEvent actual =
                    (CharacterPresenceEvent) binding.wireNormalizer().apply(event);

            assertNull(actual.getIp());
            assertNull(actual.getHwid());
            assertEquals(event.toBuilder().ip(null).hwid(null).build(), actual);
        }

        @Test
        void wireNormalizer_shouldTrimRealValues_forCharacterPresenceEvent() {
            EventTypeBinding binding = new EventTypeRegistry().lookup(CharacterPresenceEvent.class);
            CharacterPresenceEvent event =
                    presence().ip(" 10.0.0.1 ").hwid(" AbC123 ").build();

            CharacterPresenceEvent actual =
                    (CharacterPresenceEvent) binding.wireNormalizer().apply(event);

            assertEquals("10.0.0.1", actual.getIp());
            assertEquals("AbC123", actual.getHwid());
        }

        @Test
        void wireNormalizer_shouldReturnSameInstance_whenPresenceIdentifiersAreClean() {
            EventTypeBinding binding = new EventTypeRegistry().lookup(CharacterPresenceEvent.class);
            CharacterPresenceEvent event = presence().ip("10.0.0.1").build();

            assertSame(event, binding.wireNormalizer().apply(event));
        }

        @ParameterizedTest(name = "[{index}] hwid={0}")
        @ValueSource(strings = {"0", "n\\a", "disconnected"})
        void wireNormalizer_shouldNullHwidPlaceholderAndKeepClientIp_forAccountAuthAttemptEvent(String hwid) {
            EventTypeBinding binding = new EventTypeRegistry().lookup(AccountAuthAttemptEvent.class);
            AccountAuthAttemptEvent event =
                    authAttempt().clientIp("0.0.0.0").hwid(hwid).build();

            AccountAuthAttemptEvent actual =
                    (AccountAuthAttemptEvent) binding.wireNormalizer().apply(event);

            assertEquals("0.0.0.0", actual.getClientIp());
            assertNull(actual.getHwid());
        }

        @Test
        void wireNormalizer_shouldReturnSameInstance_whenAuthAttemptIdentifiersAreClean() {
            EventTypeBinding binding = new EventTypeRegistry().lookup(AccountAuthAttemptEvent.class);
            AccountAuthAttemptEvent event =
                    authAttempt().clientIp("10.0.0.1").hwid("hw-1").build();

            assertSame(event, binding.wireNormalizer().apply(event));
        }

        @Test
        void wireNormalizer_shouldBeIdentity_forEventsWithoutClientIdentifiers() {
            EventTypeBinding binding = new EventTypeRegistry().lookup(PremiumPurchaseEvent.class);
            PremiumPurchaseEvent event = PremiumPurchaseEvent.builder()
                    .eventId(UUIDv7.generate())
                    .characterId(1L)
                    .build();

            assertSame(event, binding.wireNormalizer().apply(event));
        }

        private CharacterPresenceEvent.Builder presence() {
            return CharacterPresenceEvent.builder()
                    .eventId(UUIDv7.generate())
                    .characterId(7L)
                    .online(true)
                    .source(SessionSource.CLIENT);
        }

        private AccountAuthAttemptEvent.Builder authAttempt() {
            return AccountAuthAttemptEvent.builder()
                    .eventId(UUIDv7.generate().toString())
                    .serverId(UUIDv7.generate().toString())
                    .accountName("acc")
                    .outcome(AuthOutcomes.SUCCESS)
                    .attemptedAt(Instant.parse("2026-06-01T12:00:00Z"));
        }
    }

    @Nested
    class AppSessionsSnapshot {

        private final AppSessionsSnapshotEvent snapshot = AppSessionsSnapshotEvent.builder()
                .eventId(UUIDv7.generate())
                .takenAt(Instant.parse("2026-10-26T10:00:00Z"))
                .build();

        private final UUID serverId = UUID.fromString("0192f000-0000-7000-8000-00000000000a");

        @Test
        void lookup_shouldRouteTheSnapshotToTheCharacterFamily() {
            EventTypeBinding actual = new EventTypeRegistry(serverId).lookup(AppSessionsSnapshotEvent.class);

            assertEquals("character", actual.familyKey());
            assertEquals("AppSessionsSnapshotEvent", actual.messageType());
        }

        @Test
        void partitionKeyExtractor_shouldKeyTheSnapshotByTheServer() {
            byte[] actual = new EventTypeRegistry(serverId)
                    .lookup(AppSessionsSnapshotEvent.class)
                    .partitionKeyExtractor()
                    .apply(snapshot);

            assertArrayEquals(NxHeaders.encodeUuid(serverId), actual);
        }

        @Test
        void lookup_shouldHaveNoSnapshotBinding_whenTheServerIsUnknown() {
            assertNull(new EventTypeRegistry().lookup(AppSessionsSnapshotEvent.class));
        }

        @Test
        void constructor_shouldRejectANullServerId() {
            assertThrows(NullPointerException.class, () -> new EventTypeRegistry((UUID) null));
        }
    }
}
