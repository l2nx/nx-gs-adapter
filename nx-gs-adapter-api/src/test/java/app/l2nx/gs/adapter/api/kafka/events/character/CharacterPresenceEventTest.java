package app.l2nx.gs.adapter.api.kafka.events.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class CharacterPresenceEventTest {

    private static UUID id() {
        return UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");
    }

    private static CharacterPresenceEvent.Builder clientLogin() {
        return CharacterPresenceEvent.builder()
                .eventId(id())
                .characterId(1L)
                .online(true)
                .source(SessionSource.CLIENT);
    }

    private static CharacterPresenceEvent.Builder clientLogout() {
        return CharacterPresenceEvent.builder()
                .eventId(id())
                .characterId(1L)
                .online(false)
                .source(SessionSource.CLIENT)
                .logoutReason(LogoutReason.LOGOUT);
    }

    private static CharacterPresenceEvent.Builder appStart() {
        return CharacterPresenceEvent.builder()
                .eventId(id())
                .characterId(1L)
                .online(true)
                .source(SessionSource.APP)
                .activity(ActivityType.OFFLINE_FISHING);
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of("eventId null", clientLogin().eventId(null), NullPointerException.class),
                Arguments.of("source null", clientLogin().source(null), NullPointerException.class),
                Arguments.of("app without activity", appStart().activity(null), IllegalArgumentException.class),
                Arguments.of(
                        "online with a result",
                        clientLogin().result(SessionResult.builder().build()),
                        IllegalArgumentException.class),
                Arguments.of("client resumed", clientLogin().resumed(true), IllegalArgumentException.class),
                Arguments.of(
                        "offline resumed",
                        appStart()
                                .online(false)
                                .logoutReason(LogoutReason.APP_TIME_UP)
                                .resumed(true),
                        IllegalArgumentException.class),
                Arguments.of(
                        "offline without logoutReason",
                        clientLogout().logoutReason(null),
                        IllegalArgumentException.class),
                Arguments.of(
                        "online with logoutReason",
                        clientLogin().logoutReason(LogoutReason.LOGOUT),
                        IllegalArgumentException.class));
    }

    static Stream<Arguments> fullEvents() {
        UUID sessionId = UUID.fromString("018f5fa3-1e3d-7000-8000-0000000000aa");
        return Stream.of(
                Arguments.of(
                        "app end with every field",
                        appStart()
                                .online(false)
                                .logoutReason(LogoutReason.APP_DAILY_LIMIT)
                                .sessionId(sessionId)
                                .accountName("acc")
                                .ip("1.2.3.4")
                                .hwid("HW")
                                .metadata(Collections.singletonMap("k", "v"))
                                .result(SessionResult.builder().overflow(1L).build())
                                .build(),
                        sessionId,
                        "acc",
                        "1.2.3.4",
                        "HW",
                        false),
                Arguments.of(
                        "client end with a result",
                        clientLogout().result(SessionResult.builder().build()).build(),
                        null,
                        null,
                        null,
                        null,
                        false),
                Arguments.of("resumed app start", appStart().resumed(true).build(), null, null, null, null, true));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEventTest#invalidEvents")
        void constructor_shouldReject_whenEventInvalid(
                String name, CharacterPresenceEvent.Builder builder, Class<? extends RuntimeException> expected) {
            assertThrows(expected, builder::build);
        }

        @Test
        void constructor_shouldAcceptClient_whenActivityMarksANativeStoreSession() {
            CharacterPresenceEvent actual =
                    clientLogin().activity(ActivityType.OFFLINE_PRIVATE_STORE).build();

            assertEquals(ActivityType.OFFLINE_PRIVATE_STORE, actual.getActivity());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ActivityType.class, mode = EnumSource.Mode.EXCLUDE, names = "OFFLINE_PRIVATE_STORE")
        void constructor_shouldRejectClient_whenActivityIsNotTheNativeStore(ActivityType activity) {
            CharacterPresenceEvent.Builder builder = clientLogin().activity(activity);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldAcceptResult_whenAClientOrAppSessionEnds() {
            SessionResult result = SessionResult.builder().overflow(1L).build();

            assertEquals(result, clientLogout().result(result).build().getResult());
            assertEquals(
                    result,
                    appStart()
                            .online(false)
                            .logoutReason(LogoutReason.APP_OVERWEIGHT)
                            .result(result)
                            .build()
                            .getResult());
        }

        @Test
        void wire_shouldNameTheCharacterCharacterId() {
            String json = new Gson().toJson(clientLogin().build());

            assertTrue(json.contains("\"characterId\":1"), json);
            assertFalse(json.contains("charId"), json);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(LogoutReason.class)
        void constructor_shouldAcceptEveryLogoutReason_whenOffline(LogoutReason reason) {
            CharacterPresenceEvent event = clientLogout().logoutReason(reason).build();

            assertEquals(reason, event.getLogoutReason());
        }
    }

    @Nested
    class Metadata {

        @Test
        void getMetadata_shouldReturnNull_whenBuilderOmits() {
            assertNull(clientLogin().build().getMetadata());
        }

        @Test
        void constructor_shouldFreezeACopyOfTheMetadataMap() {
            Map<String, String> source = new HashMap<String, String>();
            source.put("k", "v");

            CharacterPresenceEvent event = clientLogin().metadata(source).build();
            source.put("late", "value");

            assertEquals(1, event.getMetadata().size());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> event.getMetadata().put("k2", "v2"));
        }
    }

    @Nested
    class Builder {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEventTest#fullEvents")
        void toBuilder_shouldRoundtripAllFields(
                String name,
                CharacterPresenceEvent original,
                UUID sessionId,
                String accountName,
                String ip,
                String hwid,
                boolean resumed) {
            CharacterPresenceEvent copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertNotSame(original, copy);
            assertEquals(sessionId, copy.getSessionId());
            assertEquals(accountName, copy.getAccountName());
            assertEquals(ip, copy.getIp());
            assertEquals(hwid, copy.getHwid());
            assertEquals(resumed, copy.isResumed());
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(CharacterPresenceEvent.class)
                    .withNonnullFields("eventId")
                    .verify();
        }
    }

    @Nested
    class JsonRoundtrip {

        private final ObjectMapper jackson = JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        @Test
        void gson_shouldRoundtripEnumsAsConstantNames() {
            Gson gson = new Gson();
            CharacterPresenceEvent original = appStart()
                    .online(false)
                    .logoutReason(LogoutReason.APP_STOPPED_BEFORE_RESTORE)
                    .build();

            String json = gson.toJson(original);

            assertTrue(json.contains("\"source\":\"APP\""));
            assertTrue(json.contains("\"activity\":\"OFFLINE_FISHING\""));
            assertTrue(json.contains("\"logoutReason\":\"APP_STOPPED_BEFORE_RESTORE\""));
            assertEquals(original, gson.fromJson(json, CharacterPresenceEvent.class));
        }

        @Test
        void jackson_shouldRoundtripThroughTheConstructor() throws Exception {
            CharacterPresenceEvent original = appStart().resumed(true).build();

            String json = jackson.writeValueAsString(original);

            assertEquals(original, jackson.readValue(json, CharacterPresenceEvent.class));
            assertEquals(1L, original.getCharacterId());
            assertTrue(original.isOnline());
            assertEquals(SessionSource.APP, original.getSource());
            assertEquals(id(), original.getEventId());
        }
    }
}
