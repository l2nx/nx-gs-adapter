package app.l2nx.gs.adapter.api.kafka.events.chat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatChannelTest {

    @Nested
    class Wire {

        @Test
        void name_shouldKeepEveryShippedChannelToken() {
            List<String> actual = new ArrayList<String>();
            for (ChatChannel channel : ChatChannel.values()) {
                actual.add(channel.name());
            }

            assertEquals(
                    Arrays.asList(
                            "GENERAL",
                            "SHOUT",
                            "WHISPER",
                            "PARTY",
                            "CLAN",
                            "ALLIANCE",
                            "TRADE",
                            "WORLD",
                            "HERO",
                            "GM",
                            "PETITION",
                            "PETITION_GM",
                            "ANNOUNCEMENT",
                            "CRITICAL_ANNOUNCEMENT",
                            "SCREEN_ANNOUNCEMENT",
                            "BATTLEFIELD",
                            "BOAT",
                            "FRIEND",
                            "MSN",
                            "PARTY_ROOM",
                            "COMMAND_CHANNEL",
                            "COMMAND_CHANNEL_COMMANDER",
                            "NPC_GENERAL",
                            "NPC_SHOUT",
                            "NPC_WHISPER"),
                    actual);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ChatChannel.class)
        void fromToken_shouldReadTheName(ChatChannel channel) {
            assertSame(channel, ChatChannel.fromToken(channel.name()));
        }

        @ParameterizedTest(name = "''{0}''")
        @NullSource
        @ValueSource(strings = {"UNKNOWN_42", "whisper", ""})
        void fromToken_shouldBeNull_whenUnknown(String token) {
            assertNull(ChatChannel.fromToken(token));
        }

        @Test
        void unknownToken_shouldPrefixTheRawType() {
            assertEquals("UNKNOWN_42", ChatChannel.unknownToken(42));
        }
    }

    @Nested
    class Sendable {

        @Test
        void sendableFromApp_shouldBeExactlyClanAllianceGroupAndWhisper() {
            assertEquals(
                    EnumSet.of(ChatChannel.CLAN, ChatChannel.ALLIANCE, ChatChannel.PARTY, ChatChannel.WHISPER),
                    where(true));
        }

        @Test
        void sendableOffline_shouldDropParty() {
            assertEquals(EnumSet.of(ChatChannel.CLAN, ChatChannel.ALLIANCE, ChatChannel.WHISPER), where(false));
        }

        private Set<ChatChannel> where(boolean online) {
            Set<ChatChannel> channels = EnumSet.noneOf(ChatChannel.class);
            for (ChatChannel channel : ChatChannel.values()) {
                if (online ? channel.sendableFromApp() : channel.sendableOffline()) {
                    channels.add(channel);
                }
            }
            return channels;
        }
    }
}
