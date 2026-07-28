package vitbuk.com.Ambotorix.adapters.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import vitbuk.com.Ambotorix.chat.Attachment;
import vitbuk.com.Ambotorix.chat.Audience;
import vitbuk.com.Ambotorix.chat.ChatGateway;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.MentionTable;
import vitbuk.com.Ambotorix.chat.MessageRef;
import vitbuk.com.Ambotorix.chat.OutgoingMessage;
import vitbuk.com.Ambotorix.chat.Platform;
import vitbuk.com.Ambotorix.chat.ui.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

/**
 * The Telegram half of the chat port. Everything the bot sends to Telegram goes through here, and
 * this is the only class outside the dispatcher that touches {@link TelegramClient}.
 *
 * <p>Message text is already in Telegram's HTML dialect and {@code @username} is already Telegram's
 * mention syntax, so rendering is a pass-through plus {@code parseMode=HTML} — all the interesting
 * translation work lives in the Discord adapter.
 */
@Service
public class TelegramGateway implements ChatGateway {

    private static final Logger log = LoggerFactory.getLogger(TelegramGateway.class);

    /** Telegram's own limits: 4096 chars of text, 1024 of photo caption. */
    private static final Capabilities CAPABILITIES = new Capabilities(4096, 1024, Integer.MAX_VALUE);

    private final TelegramClient telegramClient;
    private final TelegramComponentRenderer componentRenderer;

    public TelegramGateway(TelegramClient telegramClient, TelegramComponentRenderer componentRenderer) {
        this.telegramClient = telegramClient;
        this.componentRenderer = componentRenderer;
    }

    @Override
    public Platform platform() {
        return Platform.TELEGRAM;
    }

    @Override
    public Optional<MessageRef> send(OutgoingMessage message) {
        ChatRef target = targetOf(message.to());
        if (target == null) {
            log.warn("Cannot send to {} — no addressable target", message.to());
            return Optional.empty();
        }
        InlineKeyboardMarkup markup = componentRenderer.render(message.components());
        try {
            Message sent = message.hasAttachment()
                    ? telegramClient.execute(photo(target, message, markup))
                    : telegramClient.execute(text(target, message, markup));
            return Optional.ofNullable(sent).map(m -> new MessageRef(target, String.valueOf(m.getMessageId())));
        } catch (TelegramApiException e) {
            // Overwhelmingly this is an unreachable DM; callers fall back to the group channel.
            log.warn("Failed to send to {}: {}", target, e.getMessage());
            return Optional.empty();
        }
    }

    private SendMessage text(ChatRef target, OutgoingMessage message, InlineKeyboardMarkup markup) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(target.channelId())
                .messageThreadId(target.asTelegramThreadId())
                .text(message.text() == null ? "" : message.text())
                .parseMode("HTML");
        if (markup != null) builder.replyMarkup(markup);
        if (message.replyTo() != null) builder.replyToMessageId(message.replyTo().asTelegramMessageId());
        if (message.silent()) builder.disableNotification(true);
        return builder.build();
    }

    private SendPhoto photo(ChatRef target, OutgoingMessage message, InlineKeyboardMarkup markup) {
        Attachment attachment = message.attachment();
        SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder()
                .chatId(target.channelId())
                .messageThreadId(target.asTelegramThreadId())
                .photo(new InputFile(new ByteArrayInputStream(attachment.bytes()), attachment.fileName()))
                .parseMode("HTML");
        if (message.text() != null && !message.text().isBlank()) builder.caption(message.text());
        if (markup != null) builder.replyMarkup(markup);
        if (message.replyTo() != null) builder.replyToMessageId(message.replyTo().asTelegramMessageId());
        if (message.silent()) builder.disableNotification(true);
        return builder.build();
    }

    @Override
    public boolean editText(MessageRef ref, String text, MentionTable mentions) {
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(ref.chat().channelId())
                    .messageId(ref.asTelegramMessageId())
                    .text(text)
                    .parseMode("HTML")
                    .build());
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to edit message {} in {}: {}", ref.messageId(), ref.chat(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean editComponents(MessageRef ref, List<Component> components) {
        InlineKeyboardMarkup markup = componentRenderer.render(components);
        try {
            telegramClient.execute(EditMessageReplyMarkup.builder()
                    .chatId(ref.chat().channelId())
                    .messageId(ref.asTelegramMessageId())
                    // An empty keyboard is how a grid is locked once its owner has submitted.
                    .replyMarkup(markup == null ? InlineKeyboardMarkup.builder().keyboard(List.of()).build() : markup)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            log.warn("Failed to edit components of {} in {}: {}", ref.messageId(), ref.chat(), e.getMessage());
            return false;
        }
    }

    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }

    /** A DM is just the user's own chat on Telegram, so both audiences collapse to a ChatRef. */
    private ChatRef targetOf(Audience audience) {
        return switch (audience) {
            case Audience.Channel channel -> channel.chat();
            case Audience.Direct direct -> direct.user().isAddressable()
                    ? ChatRef.telegram(direct.user().asTelegramUserId(), null)
                    : null;
        };
    }
}
