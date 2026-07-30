package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vitbuk.com.Ambotorix.chat.ChatGatewayRegistry;
import vitbuk.com.Ambotorix.chat.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the application with <em>only</em> Discord configured.
 *
 * <p>This is the deployment that failed on a real run: with no {@code bot.token}, the Telegram client
 * bean blew up with "botToken is marked non-null but is null" and took the whole context down. Both
 * adapters are optional now, and this test is what keeps them that way — every other test configures
 * Telegram, so nothing else covers a single-platform deployment.
 */
@SpringBootTest(properties = "spring.config.location=classpath:/discord-only.properties")
class DiscordOnlyStartupTest {

    /** Mocked so the context does not try to reach Discord's gateway. */
    @MockitoBean
    JDA jda;

    @Autowired ApplicationContext context;
    @Autowired ChatGatewayRegistry gateways;

    @Test
    void startsWithoutTelegramConfigured() {
        assertEquals(Platform.DISCORD, gateways.of(Platform.DISCORD).platform());
    }

    @Test
    void telegramBeansAreAbsentRatherThanBroken() {
        assertFalse(context.containsBean("telegramClient"), "no Telegram client without bot.token");
        assertFalse(context.containsBean("telegramGateway"), "no Telegram gateway without bot.token");
        assertFalse(context.containsBean("telegramBot"), "no long-polling bot without bot.token");
        assertThrows(IllegalStateException.class, () -> gateways.of(Platform.TELEGRAM));
    }

    @Test
    void discordSideIsFullyWired() {
        assertTrue(context.containsBean("discordBot"));
        assertTrue(context.containsBean("discordSlashCommandRegistrar"));
    }
}
