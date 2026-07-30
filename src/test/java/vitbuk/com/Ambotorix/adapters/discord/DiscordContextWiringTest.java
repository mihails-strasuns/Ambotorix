package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.EventListener;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vitbuk.com.Ambotorix.chat.ChatGateway;
import vitbuk.com.Ambotorix.chat.ChatGatewayRegistry;
import vitbuk.com.Ambotorix.chat.Platform;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Loads the context <em>with the Discord adapter enabled</em>.
 *
 * <p>This exists because every other test runs Discord-disabled, so none of them exercise the Discord
 * half of the bean graph — and the first live start failed with a bean cycle
 * ({@code BotDispatcher → AmbotorixService → ChatGatewayRegistry → DiscordGateway → JDA → DiscordBot}).
 * A green build told us nothing. Now enabling the adapter is part of the build.
 *
 * <p>{@code JDA} is mocked so nothing tries to reach Discord; the point is the wiring, not the network.
 */
@SpringBootTest(properties = {
        "discord.token=test-discord-token",
        "bot.discord-admin-id=999999999"
})
class DiscordContextWiringTest {

    /** Replaces the real bean, so {@code DiscordConfig} never opens a gateway connection. */
    @MockitoBean
    JDA jda;

    @Autowired ChatGatewayRegistry gateways;
    @Autowired DiscordBot discordBot;
    @Autowired DiscordSlashCommandRegistrar registrar;

    @Test
    void discordAdapterWiresWithoutABeanCycle() {
        ChatGateway discord = gateways.of(Platform.DISCORD);

        assertNotNull(discord);
        assertEquals(Platform.DISCORD, discord.platform());
    }

    /**
     * The actual regression guard. The wiring tests above cannot catch this one: mocking the
     * {@code JDA} bean replaces its definition, which removes the very dependency edge that closed
     * the cycle. So assert the invariant directly instead — the JDA bean must not be constructed with
     * a listener, because handling an event needs the dispatch chain, which needs JDA.
     */
    @Test
    void jdaBeanIsNotConstructedWithAnEventListener() {
        Method factory = Arrays.stream(DiscordConfig.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> JDA.class.isAssignableFrom(m.getReturnType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DiscordConfig no longer declares a JDA @Bean"));

        for (Class<?> parameter : factory.getParameterTypes()) {
            assertFalse(EventListener.class.isAssignableFrom(parameter),
                    () -> "The JDA bean takes " + parameter.getSimpleName() + ", a JDA event listener. "
                            + "That closes a bean cycle (JDA → listener → dispatcher → gateway → JDA) and the "
                            + "application will not start. Let the listener register itself on "
                            + "ApplicationReadyEvent instead.");
        }
    }

    @Test
    void telegramGatewayStillPresentAlongsideDiscord() {
        // The whole point is both at once — a Discord deployment must not displace Telegram.
        assertEquals(Platform.TELEGRAM, gateways.of(Platform.TELEGRAM).platform());
    }
}
