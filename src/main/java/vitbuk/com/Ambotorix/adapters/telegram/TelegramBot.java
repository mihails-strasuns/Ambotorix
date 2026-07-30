package vitbuk.com.Ambotorix.adapters.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import vitbuk.com.Ambotorix.BotDispatcher;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ChatEvent;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.UserRef;
import vitbuk.com.Ambotorix.config.BotConfig;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The Telegram inbound adapter: long-polls, turns each {@link Update} into a {@link ChatEvent}, and
 * hands it to the platform-free {@link BotDispatcher}.
 *
 * <p>Everything Telegram-shaped about an incoming message is resolved here — the {@code @botname}
 * suffix, the {@code /command_argument} underscore convention, forum topic ids, and acknowledging a
 * callback query so the tapped button stops spinning.
 */
// Only polls when Telegram is configured.
@ConditionalOnProperty(name = "bot.token")
@Component
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final BotDispatcher dispatcher;
    private final BotConfig botConfig;
    private final TelegramClient telegramClient;

    public TelegramBot(BotDispatcher dispatcher, BotConfig botConfig, TelegramClient telegramClient) {
        this.dispatcher = dispatcher;
        this.botConfig = botConfig;
        this.telegramClient = telegramClient;
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        ChatEvent event = toEvent(update);
        if (event != null) dispatcher.handle(event);
    }

    private ChatEvent toEvent(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery query = update.getCallbackQuery();
            // Ack at the platform edge so no core code has to remember to: Telegram leaves the button
            // spinning until the query is answered (Discord's deferEdit is the same obligation).
            acknowledge(query);
            ActionRef action = ActionRef.decode(query.getData());
            if (action == null) return null;
            ChatRef chat = chatOf(query);
            return new ChatEvent.Interaction(userOf(query.getFrom()), chat, isDirect(query), action, List.of());
        }

        if (!update.hasMessage()) return null;
        Message message = update.getMessage();
        String text = message.getText();
        if (text == null) return null;

        boolean direct = message.getChat() != null && message.getChat().isUserChat();
        ChatRef chat = ChatRef.telegram(message.getChatId(), message.getMessageThreadId());
        UserRef from = userOf(message.getFrom());

        if (!text.startsWith("/")) {
            return new ChatEvent.FreeText(from, chat, direct, text.trim());
        }

        String botName = botConfig.getUsername() == null ? "" : botConfig.getUsername().replaceFirst("^@", "");
        String cleaned = botName.isEmpty() ? text : text.replaceAll("(?i)@" + Pattern.quote(botName), "");
        // Telegram commands take their argument after a space or an underscore (/ban_gandhi), but a
        // shortname may itself contain underscores (roosevelt_bull_moose) — so split exactly once.
        String[] parts = cleaned.trim().split("[\\s_]+", 2);
        String args = parts.length > 1 ? parts[1].trim() : "";
        return new ChatEvent.SlashCommand(from, chat, direct, parts[0].trim(), args);
    }

    private void acknowledge(CallbackQuery callbackQuery) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query: {}", e.getMessage(), e);
        }
    }

    private UserRef userOf(User user) {
        return user == null ? null : UserRef.telegram(user.getId(), user.getUserName());
    }

    /** Where the tapped component was shown — often a DM, hence the lobby id inside the payload. */
    private ChatRef chatOf(CallbackQuery query) {
        if (query.getMessage() instanceof Message message) {
            return ChatRef.telegram(message.getChatId(), message.getMessageThreadId());
        }
        // Message no longer accessible (too old) — fall back to the presser's own chat.
        return ChatRef.telegram(query.getFrom().getId(), null);
    }

    private boolean isDirect(CallbackQuery query) {
        return query.getMessage() instanceof Message message
                && message.getChat() != null && message.getChat().isUserChat();
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        log.info("Registered bot running state is: {}", botSession.isRunning());
    }
}
