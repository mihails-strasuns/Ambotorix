package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vitbuk.com.Ambotorix.BotDispatcher;
import vitbuk.com.Ambotorix.chat.ChatEvent;
import vitbuk.com.Ambotorix.chat.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Inbound mapping for the one event source that is not an interaction: plain messages.
 *
 * <p>This exists because of a real defect — the adapter used to read {@code getContentRaw()} on every
 * guild message, which it is not allowed to do without the privileged {@code MESSAGE_CONTENT} intent.
 * It could never act on that content (group chatter is ignored by design), but it logged a JDA warning
 * for every message posted anywhere the bot could see.
 */
class DiscordBotInboundTest {

    private final BotDispatcher dispatcher = mock(BotDispatcher.class);
    private final DiscordBot bot = new DiscordBot(dispatcher, mock(JDA.class),
            new DiscordComponentRenderer(), new DiscordChooserPager());

    private static MessageReceivedEvent event(ChannelType type, String content, boolean fromBot) {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.isFromType(ChannelType.PRIVATE)).thenReturn(type == ChannelType.PRIVATE);
        if (type == ChannelType.PRIVATE) {
            User author = mock(User.class);
            when(author.isBot()).thenReturn(fromBot);
            when(author.getId()).thenReturn("42");
            when(author.getName()).thenReturn("alice");
            when(author.getEffectiveName()).thenReturn("Alice");
            when(event.getAuthor()).thenReturn(author);

            Message message = mock(Message.class);
            when(message.getContentRaw()).thenReturn(content);
            when(event.getMessage()).thenReturn(message);

            MessageChannelUnion channel = mock(MessageChannelUnion.class);
            when(channel.getId()).thenReturn("777");
            when(event.getChannel()).thenReturn(channel);
        }
        return event;
    }

    @Test
    void guildMessagesAreIgnoredWithoutReadingTheirContent() {
        MessageReceivedEvent guildMessage = event(ChannelType.TEXT, "anything at all", false);

        bot.onMessageReceived(guildMessage);

        verifyNoInteractions(dispatcher);
        // The load-bearing assertion: never touch the message, so JDA never warns about
        // MESSAGE_CONTENT and the bot needs no privileged intent.
        verify(guildMessage, never()).getMessage();
    }

    @Test
    void dmTextIsRoutedAsFreeText() {
        bot.onMessageReceived(event(ChannelType.PRIVATE, "1. lincoln 2. curtin 3. pedro 4. trajan", false));

        ArgumentCaptor<ChatEvent> captured = ArgumentCaptor.forClass(ChatEvent.class);
        verify(dispatcher).handle(captured.capture());

        ChatEvent.FreeText freeText = assertInstanceOf(ChatEvent.FreeText.class, captured.getValue());
        assertEquals("1. lincoln 2. curtin 3. pedro 4. trajan", freeText.text());
        assertTrue(freeText.direct(), "a DM must be marked direct or the dispatcher drops it");
        assertEquals(Platform.DISCORD, freeText.from().platform());
        assertEquals("alice", freeText.from().userName());
    }

    @Test
    void slashCommandsTypedAsTextInADmAreNotTreatedAsPicks() {
        bot.onMessageReceived(event(ChannelType.PRIVATE, "/help", false));

        verify(dispatcher, never()).handle(any());
    }

    @Test
    void theBotsOwnMessagesAreIgnored() {
        bot.onMessageReceived(event(ChannelType.PRIVATE, "✅ Picks recorded", true));

        verify(dispatcher, never()).handle(any());
    }
}
