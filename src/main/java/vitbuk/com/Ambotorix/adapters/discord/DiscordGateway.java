package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.Attachment;
import vitbuk.com.Ambotorix.chat.Audience;
import vitbuk.com.Ambotorix.chat.ChatGateway;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.MentionTable;
import vitbuk.com.Ambotorix.chat.MessageRef;
import vitbuk.com.Ambotorix.chat.OutgoingMessage;
import vitbuk.com.Ambotorix.chat.Platform;
import vitbuk.com.Ambotorix.chat.ui.Component;

import java.util.List;
import java.util.Optional;

/**
 * The Discord half of the chat port.
 *
 * <p>Deliberately synchronous ({@code .complete()}): the bot's whole flow assumes a send has landed
 * before the handler returns, and JDA handles rate limits by blocking internally
 * (MULTIPLATFORM_PLAN.md D3).
 *
 * <p>The lobby's ambient status message is sent as an <b>embed</b>: its description allows 4096
 * characters against 2000 for message content, and Discord's "suppress notifications" flag gives it
 * the same silent, ambient character it has on Telegram.
 */
// Only exists when the Discord adapter is configured — it needs a live JDA.
@ConditionalOnProperty(name = "discord.token")
@Service
public class DiscordGateway implements ChatGateway {

    private static final Logger log = LoggerFactory.getLogger(DiscordGateway.class);

    private static final Capabilities CAPABILITIES = new Capabilities(
            DiscordTextRenderer.MAX_MESSAGE_LENGTH, DiscordTextRenderer.MAX_MESSAGE_LENGTH,
            DiscordComponentRenderer.MAX_ACTION_ROWS * DiscordComponentRenderer.MAX_OPTIONS_PER_MENU);

    private final JDA jda;
    private final DiscordTextRenderer textRenderer;
    private final DiscordComponentRenderer componentRenderer;
    private final DiscordChooserPager pager;

    public DiscordGateway(JDA jda, DiscordTextRenderer textRenderer, DiscordComponentRenderer componentRenderer,
                          DiscordChooserPager pager) {
        this.jda = jda;
        this.textRenderer = textRenderer;
        this.componentRenderer = componentRenderer;
        this.pager = pager;
    }

    @Override
    public Platform platform() {
        return Platform.DISCORD;
    }

    @Override
    public Optional<MessageRef> send(OutgoingMessage message) {
        try {
            MessageChannel channel = channelFor(message.to());
            if (channel == null) return Optional.empty();

            String text = textRenderer.render(message.text(), message.mentions());
            List<ActionRow> rows = componentRenderer.render(message.components());

            // Long text is split so nothing is silently dropped; only the last part carries the
            // components, so buttons sit at the end of the message the user is reading.
            List<String> chunks = message.silent()
                    ? List.of(text)   // the status embed has its own, larger budget
                    : textRenderer.chunk(text, DiscordTextRenderer.MAX_MESSAGE_LENGTH);

            Message sent = null;
            for (int i = 0; i < chunks.size(); i++) {
                boolean last = i == chunks.size() - 1;
                MessageCreateAction action = message.silent()
                        ? channel.sendMessageEmbeds(embed(chunks.get(i)))
                        : channel.sendMessage(chunks.get(i));
                if (last && !rows.isEmpty()) action = action.setComponents(rows);
                if (last && message.hasAttachment()) action = action.addFiles(upload(message.attachment()));
                if (message.replyTo() != null) action = action.setMessageReference(message.replyTo().messageId());
                if (message.silent()) action = action.setSuppressedNotifications(true);
                sent = action.complete();
            }
            ChatRef chat = chatOf(message.to(), sent);
            if (sent != null && !message.components().isEmpty()) {
                // Track the message so a later badge update re-renders the page the player is on.
                pager.remember(sent.getId(), message.components(), 0);
            }
            return Optional.ofNullable(sent).map(m -> new MessageRef(chat, m.getId()));
        } catch (Exception e) {
            // Almost always an unopenable DM (privacy settings, no shared guild) — callers fall back.
            log.warn("Failed to send to {}: {}", message.to(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean editText(MessageRef ref, String text, MentionTable mentions) {
        try {
            MessageChannel channel = jda.getChannelById(MessageChannel.class, ref.chat().channelId());
            if (channel == null) return false;
            // Status messages are embeds, so the edit has to replace the embed rather than content.
            channel.editMessageEmbedsById(ref.messageId(), embed(textRenderer.render(text, mentions))).complete();
            return true;
        } catch (Exception e) {
            log.error("Failed to edit message {} in {}: {}", ref.messageId(), ref.chat(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean editComponents(MessageRef ref, List<Component> components) {
        try {
            MessageChannel channel = jda.getChannelById(MessageChannel.class, ref.chat().channelId());
            if (channel == null) return false;
            // Re-render at whatever page this message is showing, so updating a rank badge does not
            // yank the player back to page 1.
            int page = pager.pageOf(ref.messageId());
            channel.editMessageComponentsById(ref.messageId(),
                    componentRenderer.render(components, page)).complete();
            if (components.isEmpty()) {
                pager.forget(ref.messageId());
            } else {
                pager.remember(ref.messageId(), components, page);
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to edit components of {} in {}: {}", ref.messageId(), ref.chat(), e.getMessage());
            return false;
        }
    }

    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }

    private MessageEmbed embed(String description) {
        return new EmbedBuilder().setDescription(description).build();
    }

    private FileUpload upload(Attachment attachment) {
        return FileUpload.fromData(attachment.bytes(), attachment.fileName());
    }

    /** Opening a DM channel is itself a request that can fail — that failure is the "unreachable" signal. */
    private MessageChannel channelFor(Audience audience) {
        return switch (audience) {
            case Audience.Channel channel -> jda.getChannelById(MessageChannel.class, channel.chat().channelId());
            case Audience.Direct direct -> {
                if (!direct.user().isAddressable()) yield null;
                User user = jda.retrieveUserById(direct.user().id()).complete();
                yield user == null ? null : user.openPrivateChannel().complete();
            }
        };
    }

    /** A DM's real channel id is only known once the channel is open, so read it back off the send. */
    private ChatRef chatOf(Audience audience, Message sent) {
        if (audience instanceof Audience.Channel channel) return channel.chat();
        return sent == null ? null : ChatRef.discord(sent.getChannel().getId());
    }
}
