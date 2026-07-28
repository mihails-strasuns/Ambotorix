package vitbuk.com.Ambotorix.adapters.telegram;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import vitbuk.com.Ambotorix.config.BotConfig;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramClientConfig {

    @Bean
    public TelegramClient telegramClient(BotConfig botConfig) {
        return new OkHttpTelegramClient(botConfig.getToken());
    }
}
