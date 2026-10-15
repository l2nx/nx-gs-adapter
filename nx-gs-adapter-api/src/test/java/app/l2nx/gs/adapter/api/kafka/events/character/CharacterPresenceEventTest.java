package app.l2nx.gs.adapter.api.kafka.events.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.events.character.model.LogoutReason;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CharacterPresenceEventTest {

    private static UUID id() {
        return UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");
    }

    private static CharacterPresenceEvent.Builder clientLogin() {
        return CharacterPresenceEvent.builder()
                .eventId(id())
                .charId(1L)
                .online(true)
                .source(SessionSource.CLIENT);
    }

    private static CharacterPresenceEvent.Builder clientLogout() {
        return CharacterPresenceEvent.builder()
                .eventId(id())
                .charId(1L)
                .online(false)
                .source(SessionSource.CLIENT)
                .logoutReason(LogoutReason.LOGOUT);
    }

    private static CharacterPresenceEvent.Builder appStart() {
        return CharacterPresenceEvent.builder()
                .eventId(id())
                .charId(1L)
                .online(true)
                .source(SessionSource.APP)
                .activity(ActivityType.OFFLINE_FISHING);
    }

    @Nested
    class Constructor {

        @Test
        void constructor_shouldRejectNullEventId() {
            assertThrows(
                    NullPointerException.class,
                    () -> clientLogin().eventId(null).build());
        }

        @Test
        void constructor_shouldRejectNullSource() {
            assertThrows(
                    NullPointerException.class, () -> clientLogin().source(null).build());
        }

        @Test
        void constructor_shouldRejectApp_whenActivityMissing() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> appStart().activity(null).build());
        }

        @Test
        void constructor_shouldRejectClient_whenActivityPresent() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> clientLogin().activity(ActivityType.PRIVATE_STORE).build());
        }

        @Test
        void constructor_shouldRejectResumed_whenClientSource() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> clientLogin().resumed(true).build());
        }

        @Test
        void constructor_shouldRejectResumed_whenOffline() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> appStart()
                            .online(false)
                            .logoutReason(LogoutReason.APP_TIME_UP)
                            .resumed(true)
                            .build());
        }

        @Test
        void constructor_shouldAcceptResumed_whenOnlineApp() {
            assertTrue(appStart().resumed(true).build().isResumed());
        }

        @Test
        void constructor_shouldRejectOffline_whenLogoutReasonMissing() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> clientLogout().logoutReason(null).build());
        }

        @Test
        void constructor_shouldRejectOnline_whenLogoutReasonPresent() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> clientLogin().logoutReason(LogoutReason.LOGOUT).build());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(LogoutReason.class)
        void constructor_shouldAcceptEveryLogoutReason_whenOffline(LogoutReason reason) {
            CharacterPresenceEvent event = clientLogout().logoutReason(reason).build();

            assertEquals(reason, event.getLogoutReason());
        }

        @Test
        void constructor_shouldAcceptAppEnd_withActivityAndReason() {
            CharacterPresenceEvent event = appStart()
                    .online(false)
                    .logoutReason(LogoutReason.APP_OUT_OF_BAIT)
                    .build();

            assertEquals(SessionSource.APP, event.getSource());
            assertEquals(ActivityType.OFFLINE_FISHING, event.getActivity());
            assertEquals(LogoutReason.APP_OUT_OF_BAIT, event.getLogoutReason());
        }
    }

    @Nested
    class Metadata {

        @Test
        void getMetadata_shouldReturnNull_whenBuilderOmits() {
            assertNull(clientLogin().build().getMetadata());
        }

        @Test
        void getMetadata_shouldBeUnmodifiable() {
            Map<String, String> source = new HashMap<String, String>();
            source.put("k", "v");

            CharacterPresenceEvent event = clientLogin().metadata(source).build();

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> event.getMetadata().put("k2", "v2"));
        }

        @Test
        void constructor_shouldDefensivelyCopyMetadataMap() {
            Map<String, String> source = new HashMap<String, String>();
            source.put("k", "v");

            CharacterPresenceEvent event = clientLogin().metadata(source).build();
            source.put("late", "value");

            assertEquals(1, event.getMetadata().size());
        }
    }

    @Nested
    class Builder {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            UUID sessionId = UUID.fromString("018f5fa3-1e3d-7000-8000-0000000000aa");
            CharacterPresenceEvent original = appStart()
                    .online(false)
                    .logoutReason(LogoutReason.APP_DAILY_LIMIT)
                    .sessionId(sessionId)
                    .accountName("acc")
                    .ip("1.2.3.4")
                    .hwid("HW")
                    .metadata(Collections.singletonMap("k", "v"))
                    .build();

            CharacterPresenceEvent copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertNotSame(original, copy);
            assertEquals(sessionId, copy.getSessionId());
            assertEquals("acc", copy.getAccountName());
            assertEquals("1.2.3.4", copy.getIp());
            assertEquals("HW", copy.getHwid());
        }

        @Test
        void toBuilder_shouldRoundtripResumed() {
            CharacterPresenceEvent original = appStart().resumed(true).build();

            assertTrue(original.toBuilder().build().isResumed());
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_shouldDistinguishSessionId() {
            CharacterPresenceEvent withoutSession = clientLogin().build();
            CharacterPresenceEvent withSession = clientLogin()
                    .sessionId(UUID.fromString("018f5fa3-1e3d-7000-8000-0000000000bb"))
                    .build();

            assertNotEquals(withoutSession, withSession);
        }

        @Test
        void equals_shouldDistinguishSourceAndActivity() {
            assertNotEquals(clientLogin().build(), appStart().build());
            assertNotEquals(
                    appStart().build(),
                    appStart().activity(ActivityType.OFFLINE_PRIVATE_STORE).build());
        }

        @Test
        void equals_shouldDistinguishLogoutReason() {
            assertNotEquals(
                    clientLogout().build(),
                    clientLogout().logoutReason(LogoutReason.DISCONNECT).build());
        }

        @Test
        void equals_shouldDistinguishResumed() {
            assertNotEquals(appStart().build(), appStart().resumed(true).build());
        }

        @Test
        void toString_shouldRenderSourceAndReason() {
            String s = clientLogout()
                    .logoutReason(LogoutReason.SERVER_STOP)
                    .build()
                    .toString();

            assertTrue(s.contains("eventId=" + id()));
            assertTrue(s.contains("source=CLIENT"));
            assertTrue(s.contains("logoutReason=SERVER_STOP"));
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
        }
    }
}
