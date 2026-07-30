package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.BotDispatcher;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ChatEvent;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.Platform;
import vitbuk.com.Ambotorix.chat.UserRef;

import java.util.List;

/**
 * The Discord inbound adapter: turns JDA events into {@link ChatEvent}s for the shared
 * {@link BotDispatcher}.
 *
 * <p>Three sources map onto the same three events the Telegram adapter produces:
 * <ul>
 *   <li>slash commands → {@link ChatEvent.SlashCommand} (the {@code args} option holds everything
 *       after the command name, exactly like the text after {@code /ban} on Telegram);</li>
 *   <li>buttons and select menus → {@link ChatEvent.Interaction};</li>
 *   <li>DM text → {@link ChatEvent.FreeText}, which is how Herson ranked picks arrive. Discord
 *       always delivers message content in DMs, so this works without the privileged
 *       {@code MESSAGE_CONTENT} intent.</li>
 * </ul>
 *
 * <p>This class attaches <em>itself</em> to JDA once the application is ready, rather than being
 * handed to {@code JDABuilder}. It has to: dispatching an event reaches {@link DiscordGateway}, which
 * needs the {@code JDA} bean, so constructing JDA with this listener would close a bean cycle. Waiting
 * for {@link ApplicationReadyEvent} also guarantees the whole dispatch chain exists before the first
 * event can arrive; anything a user clicks in the second before that is simply not delivered, which
 * is the right outcome for a bot that is not up yet.
 */
// Only listens when the Discord adapter is configured.
@ConditionalOnProperty(name = "discord.token")
@Component
public class DiscordBot extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBot.class);

    private final BotDispatcher dispatcher;
    private final JDA jda;

    public DiscordBot(BotDispatcher dispatcher, JDA jda) {
        this.dispatcher = dispatcher;
        this.jda = jda;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startListening() {
        jda.addEventListener(this);
        log.info("Discord adapter listening for events");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Discord demands acknowledgement within 3 seconds; the bot answers by sending its own
        // messages, so the interaction itself is simply acked here.
        event.deferReply(true).queue(hook -> hook.deleteOriginal().queue(null, error -> {}), error -> {});

        OptionMapping args = event.getOption(DiscordSlashCommandRegistrar.ARGS_OPTION);
        dispatch(new ChatEvent.SlashCommand(
                userOf(event.getUser()),
                chatOf(event.getChannel().getId()),
                event.getChannelType() == ChannelType.PRIVATE,
                "/" + event.getName(),
                args == null ? "" : args.getAsString().trim()));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        handleComponent(event, event.getComponentId(), List.of());
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        // A select menu's payload is the chosen option's value, not the menu's own id.
        String chosen = event.getValues().isEmpty() ? null : event.getValues().get(0);
        if (chosen == null) {
            event.deferEdit().queue(null, error -> {});
            return;
        }
        handleComponent(event, chosen, event.getValues());
    }

    private void handleComponent(ComponentInteraction event, String payload, List<String> values) {
        // deferEdit acks without changing anything, leaving the bot free to edit the message itself.
        event.deferEdit().queue(null, error -> {});
        ActionRef action = ActionRef.decode(payload);
        if (action == null) return;
        dispatch(new ChatEvent.Interaction(
                userOf(event.getUser()),
                chatOf(event.getChannel().getId()),
                event.getChannelType() == ChannelType.PRIVATE,
                action,
                values));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        String content = event.getMessage().getContentRaw().trim();
        if (content.isEmpty() || content.startsWith("/")) return; // slash commands arrive as interactions
        dispatch(new ChatEvent.FreeText(
                userOf(event.getAuthor()),
                chatOf(event.getChannel().getId()),
                event.isFromType(ChannelType.PRIVATE),
                content));
    }

    private void dispatch(ChatEvent event) {
        try {
            dispatcher.handle(event);
        } catch (Exception e) {
            log.error("Failed to handle Discord event from {}", event.from(), e);
        }
    }

    private UserRef userOf(User user) {
        return new UserRef(Platform.DISCORD, user.getId(), user.getName(), user.getEffectiveName());
    }

    /** Discord threads are channels in their own right, so a lobby in a thread needs no extra id. */
    private ChatRef chatOf(String channelId) {
        return ChatRef.discord(channelId);
    }
}
