package vitbuk.com.Ambotorix.adapters.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import vitbuk.com.Ambotorix.config.BotConfig;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * The Telegram client, created only when a token is configured — mirroring the Discord adapter, so
 * either platform can run alone or both together. Without this bean there is no Telegram gateway and
 * no long-polling bot, and the app starts on whatever else is configured.
 */
@Configuration
@ConditionalOnProperty(name = "bot.token")
public class TelegramClientConfig {

    @Bean
    public TelegramClient telegramClient(BotConfig botConfig) {
        return new OkHttpTelegramClient(botConfig.getToken());
    }
}
