package app.l2nx.gs.adapter.api.kafka.commands.chat;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.chat.model.ChatAudience;
import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import com.google.gson.Gson;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SendChatMessageCommandTest {

    private static final Gson GSON = new Gson();

    private static SendChatMessageCommand.Builder base() {
        return SendChatMessageCommand.builder()
                .messageId(UUID.randomUUID())
                .channel("CLAN")
                .audience(ChatAudience.CLAN)
                .audienceId(7L)
                .senderCharacterId(100L)
                .source("APP")
                .text("hello")
                .initiator(TestInitiators.USER);
    }

    @Nested
    class Sender {

        @Test
        void build_shouldAccept_whenCharacterSetAndDisplayNameNull() {
            SendChatMessageCommand command = base().build();

            assertNull(command.getSenderDisplayName());
            assertEquals(100L, command.getSenderCharacterId());
        }

        @Test
        void build_shouldAccept_whenCharacterAndDisplayNameBothSet() {
            SendChatMessageCommand command = base().senderDisplayName("Vasya").build();

            assertEquals("Vasya", command.getSenderDisplayName());
        }

        @Test
        void build_shouldAcceptEmptyDisplayName_whenNoCharacter() {
            SendChatMessageCommand command = base().audience(ChatAudience.ALL_ONLINE)
                    .audienceId(null)
                    .senderCharacterId(null)
                    .senderDisplayName("")
                    .build();

            assertEquals("", command.getSenderDisplayName());
        }

        @Test
        void build_shouldAcceptNullDisplayName_whenAnnouncement() {
            SendChatMessageCommand command = base().channel(ChatChannel.ANNOUNCEMENT)
                    .audience(ChatAudience.ALL_ONLINE)
                    .audienceId(null)
                    .senderCharacterId(null)
                    .build();

            assertNull(command.getSenderDisplayName());
        }

        @Test
        void build_shouldReject_whenNoCharacterAndDisplayNameNull() {
            assertThrows(
                    NullPointerException.class,
                    () -> base().audience(ChatAudience.ALL_ONLINE)
                            .audienceId(null)
                            .senderCharacterId(null)
                            .build());
        }
    }

    @Nested
    class Audience {

        @Test
        void build_shouldRequireAudienceId_whenAlliance() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.ALLIANCE)
                            .audienceId(null)
                            .build());
            assertEquals(
                    "ALLIANCE", base().audience(ChatAudience.ALLIANCE).build().getAudience());
        }

        @Test
        void build_shouldAcceptParty_whenNoAudienceIdAndSenderSet() {
            SendChatMessageCommand command =
                    base().audience(ChatAudience.PARTY).audienceId(null).build();

            assertNull(command.getAudienceId());
        }

        @Test
        void build_shouldRejectParty_whenAudienceIdPresent() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.PARTY).build());
        }

        @Test
        void build_shouldRejectParty_whenNoSender() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.PARTY)
                            .audienceId(null)
                            .senderCharacterId(null)
                            .senderDisplayName("")
                            .build());
        }

        @Test
        void build_shouldAcceptCharacter_whenOnlyAudienceId() {
            SendChatMessageCommand command =
                    base().audience(ChatAudience.CHARACTER).audienceId(55L).build();

            assertEquals(55L, command.getAudienceId());
            assertNull(command.getTargetCharacterName());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Vasya", "Vasya*"})
        void build_shouldAcceptCharacter_whenOnlyTargetName(String name) {
            SendChatMessageCommand command = base().audience(ChatAudience.CHARACTER)
                    .audienceId(null)
                    .targetCharacterName(name)
                    .build();

            assertEquals(name, command.getTargetCharacterName());
            assertNull(command.getAudienceId());
        }

        @Test
        void build_shouldRejectCharacter_whenBothAudienceIdAndName() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.CHARACTER)
                            .audienceId(55L)
                            .targetCharacterName("Vasya")
                            .build());
        }

        @Test
        void build_shouldRejectCharacter_whenNeither() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.CHARACTER)
                            .audienceId(null)
                            .build());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void build_shouldRejectCharacter_whenNameBlank(String name) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.CHARACTER)
                            .audienceId(null)
                            .targetCharacterName(name)
                            .build());
        }

        @Test
        void build_shouldRejectTargetName_whenAudienceIsNotCharacter() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().targetCharacterName("Vasya").build());
        }

        @Test
        void build_shouldRejectAudienceId_whenAllOnline() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> base().audience(ChatAudience.ALL_ONLINE).build());
        }
    }

    @Nested
    class Compatibility {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            SendChatMessageCommand original = base().audience(ChatAudience.CHARACTER)
                    .audienceId(null)
                    .targetCharacterName("Vasya*")
                    .offlineDelivery(false)
                    .build();

            assertEquals(original, original.toBuilder().build());
        }

        @Test
        void fromJson_shouldReadNoOfflineDelivery_whenAnOlderPlatformOmitsIt() {
            String json = GSON.toJson(whisper().offlineDelivery(null).build());

            SendChatMessageCommand actual = GSON.fromJson(json, SendChatMessageCommand.class);

            assertFalse(json.contains("offlineDelivery"), json);
            assertNull(actual.getOfflineDelivery());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void fromJson_shouldReadOfflineDelivery_whenSent(boolean offlineDelivery) {
            SendChatMessageCommand expected =
                    whisper().offlineDelivery(offlineDelivery).build();

            SendChatMessageCommand actual = GSON.fromJson(GSON.toJson(expected), SendChatMessageCommand.class);

            assertEquals(offlineDelivery, actual.getOfflineDelivery());
        }

        @Test
        void equals_shouldTellOfflineDeliveryApart() {
            SendChatMessageCommand original = whisper().offlineDelivery(true).build();

            assertNotEquals(
                    original, original.toBuilder().offlineDelivery(false).build());
            assertNotEquals(original, original.toBuilder().offlineDelivery(null).build());
        }

        @Test
        void builder_shouldWriteTheEnumNames_whenGivenVocabularyConstants() {
            SendChatMessageCommand actual = whisper().build();

            assertEquals("WHISPER", actual.getChannel());
            assertEquals("CHARACTER", actual.getAudience());
        }

        @Test
        void fromJson_shouldKeepAChannelTheApiDoesNotKnow() {
            String json = GSON.toJson(base().build()).replace("\"channel\":\"CLAN\"", "\"channel\":\"UNKNOWN_99\"");

            SendChatMessageCommand actual = GSON.fromJson(json, SendChatMessageCommand.class);

            assertEquals("UNKNOWN_99", actual.getChannel());
            assertNull(ChatChannel.fromToken(actual.getChannel()));
        }

        private SendChatMessageCommand.Builder whisper() {
            return base().channel(ChatChannel.WHISPER)
                    .audience(ChatAudience.CHARACTER)
                    .audienceId(null)
                    .targetCharacterName("Vasya");
        }
    }
}
