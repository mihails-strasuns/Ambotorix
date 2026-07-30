package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Brings the Discord adapter up only when a token is configured, so a Telegram-only deployment keeps
 * working untouched — the whole adapter is absent from the context rather than half-initialized.
 *
 * <p>Intents are deliberately minimal: {@code GUILD_MESSAGES} + {@code DIRECT_MESSAGES} are enough
 * because commands arrive as interactions and Discord always delivers message content in DMs. The
 * privileged {@code MESSAGE_CONTENT} intent is not required.
 *
 * <p><b>Do not pass the listener to {@link JDABuilder} here.</b> Handling an event needs the
 * dispatcher, whose chain ends at {@link DiscordGateway}, which needs this very {@code JDA} — so
 * building JDA with {@link DiscordBot} attached closes a bean cycle and the context refuses to start.
 * {@code DiscordBot} registers itself once the application is ready instead.
 */
@Configuration
@ConditionalOnProperty(name = "discord.token")
public class DiscordConfig {

    private static final Logger log = LoggerFactory.getLogger(DiscordConfig.class);

    @Bean(destroyMethod = "shutdown")
    public JDA jda(DiscordSlashCommandRegistrar registrar,
                   @org.springframework.beans.factory.annotation.Value("${discord.token}") String token)
            throws InterruptedException {
        JDA jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES)
                .disableCache(java.util.Arrays.asList(CacheFlag.values()))
                .build()
                .awaitReady();
        registrar.register(jda);
        log.info("Discord adapter connected as {}", jda.getSelfUser().getName());
        return jda;
    }
}
