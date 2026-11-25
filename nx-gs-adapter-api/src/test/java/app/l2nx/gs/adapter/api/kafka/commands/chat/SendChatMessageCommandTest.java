package app.l2nx.gs.adapter.api.kafka.commands.chat;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.chat.model.ChatAudience;
import app.l2nx.gs.adapter.api.kafka.events.chat.model.ChatChannel;
import com.google.gson.Gson;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SendChatMessageCommandTest {

    private static final Gson GSON = new Gson();

    private static final UUID MESSAGE_ID = UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");

    private static SendChatMessageCommand.Builder base() {
        return SendChatMessageCommand.builder()
                .messageId(MESSAGE_ID)
                .channel("CLAN")
                .audience(ChatAudience.CLAN)
                .audienceId(7L)
                .senderCharacterId(100L)
                .source("APP")
                .text("hello")
                .initiator(TestInitiators.USER);
    }

    private static Stream<Arguments> invalidSenderOrAudience() {
        return Stream.of(
                Arguments.of("no character and no display name", NullPointerException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.ALL_ONLINE)
                                .audienceId(null)
                                .senderCharacterId(null)),
                Arguments.of("ALLIANCE without audienceId", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.ALLIANCE).audienceId(null)),
                Arguments.of("PARTY with audienceId", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.PARTY)),
                Arguments.of("PARTY without sender", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.PARTY)
                                .audienceId(null)
                                .senderCharacterId(null)
                                .senderDisplayName("")),
                Arguments.of("CHARACTER with both audienceId and name", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.CHARACTER).audienceId(55L).targetCharacterName("Vasya")),
                Arguments.of("CHARACTER with neither audienceId nor name", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.CHARACTER).audienceId(null)),
                Arguments.of("target name outside CHARACTER", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.targetCharacterName("Vasya")),
                Arguments.of("ALL_ONLINE with audienceId", IllegalArgumentException.class, (Consumer<
                                SendChatMessageCommand.Builder>)
                        b -> b.audience(ChatAudience.ALL_ONLINE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSenderOrAudience")
    void build_shouldReject_whenSenderOrAudienceInvalid(
            String reason, Class<? extends Throwable> expected, Consumer<SendChatMessageCommand.Builder> change) {
        SendChatMessageCommand.Builder builder = base();
        change.accept(builder);

        assertThrows(expected, builder::build);
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
    }

    @Nested
    class Audience {

        @Test
        void build_shouldAcceptAlliance_whenAudienceIdSet() {
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
    }

    @Nested
    class Compatibility {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            SendChatMessageCommand toName = new SendChatMessageCommand(
                    UUID.randomUUID(),
                    "WHISPER",
                    "CHARACTER",
                    null,
                    100L,
                    "Vasya",
                    "APP",
                    "hello",
                    "Vasya*",
                    true,
                    TestInitiators.USER,
                    false);
            SendChatMessageCommand toId = base().ownerVerified(true).build();

            assertEquals(toName, toName.toBuilder().build());
            assertEquals(toId, toId.toBuilder().build());
            assertEquals("APP", toId.getSource());
            assertEquals("hello", toId.getText());
            assertEquals(TestInitiators.USER, toId.getInitiator());
            assertEquals(MESSAGE_ID, toId.getMessageId());
            assertTrue(toId.isOwnerVerified());
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
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(SendChatMessageCommand.class).verify();
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
