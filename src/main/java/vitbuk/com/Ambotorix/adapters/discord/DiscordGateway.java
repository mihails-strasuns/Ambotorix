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

import java.util.ArrayList;
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
    private final DiscordChooserFanout fanout;

    public DiscordGateway(JDA jda, DiscordTextRenderer textRenderer, DiscordComponentRenderer componentRenderer,
                          DiscordChooserFanout fanout) {
        this.jda = jda;
        this.textRenderer = textRenderer;
        this.componentRenderer = componentRenderer;
        this.fanout = fanout;
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
            List<List<ActionRow>> chunks = componentRenderer.renderChunks(message.components());

            // Long text is split so nothing is silently dropped; the status embed has its own, larger
            // budget and is never chunked.
            List<String> parts = message.silent()
                    ? List.of(text)
                    : textRenderer.chunk(text, DiscordTextRenderer.MAX_MESSAGE_LENGTH);

            Message first = null;
            for (int i = 0; i < parts.size(); i++) {
                boolean last = i == parts.size() - 1;
                MessageCreateAction action = message.silent()
                        ? channel.sendMessageEmbeds(embed(parts.get(i)))
                        : channel.sendMessage(parts.get(i));
                // Components ride on the final text part; anything beyond one chunk follows after.
                if (last && !chunks.isEmpty()) action = action.setComponents(chunks.get(0));
                if (last && message.hasAttachment()) action = action.addFiles(upload(message.attachment()));
                if (message.replyTo() != null) action = action.setMessageReference(message.replyTo().messageId());
                if (message.silent()) action = action.setSuppressedNotifications(true);
                Message sent = action.complete();
                if (first == null) first = sent;
            }
            if (first == null) return Optional.empty();

            List<String> messageIds = new ArrayList<>();
            List<List<String>> signatures = new ArrayList<>();
            messageIds.add(first.getId());
            if (!chunks.isEmpty()) signatures.add(componentRenderer.signatureOf(chunks.get(0)));

            // A chooser too big for one message continues in follow-ups, so every option stays on
            // screen instead of hiding behind a page control.
            for (int i = 1; i < chunks.size(); i++) {
                Message extra = channel.sendMessageComponents(chunks.get(i)).complete();
                messageIds.add(extra.getId());
                signatures.add(componentRenderer.signatureOf(chunks.get(i)));
            }
            if (!chunks.isEmpty()) fanout.remember(first.getId(), messageIds, signatures);

            ChatRef chat = chatOf(message.to(), first);
            final Message handle = first;
            return Optional.of(new MessageRef(chat, handle.getId()));
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

            DiscordChooserFanout.Group group = fanout.of(ref.messageId());
            List<String> messageIds = group == null ? List.of(ref.messageId()) : group.messageIds();

            // Clearing the components locks the chooser — every message of it, not just the first.
            if (components.isEmpty()) {
                for (String messageId : messageIds) {
                    channel.editMessageComponentsById(messageId, List.of()).complete();
                }
                fanout.forget(ref.messageId());
                return true;
            }

            List<List<ActionRow>> chunks = componentRenderer.renderChunks(components);
            List<List<String>> signatures = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                List<String> signature = componentRenderer.signatureOf(chunks.get(i));
                signatures.add(signature);
                boolean known = group != null && i < group.signatures().size() && i < messageIds.size();
                // Skip messages that would render identically — one tap should cost one edit, not five.
                if (known && signature.equals(group.signatures().get(i))) continue;
                if (i < messageIds.size()) {
                    channel.editMessageComponentsById(messageIds.get(i), chunks.get(i)).complete();
                } else {
                    // The chooser grew past what it was sent with; append rather than lose options.
                    Message extra = channel.sendMessageComponents(chunks.get(i)).complete();
                    messageIds = new ArrayList<>(messageIds);
                    messageIds.add(extra.getId());
                }
            }
            fanout.remember(ref.messageId(), messageIds, signatures);
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
