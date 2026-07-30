package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.commands.structure.Command;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;

import java.util.List;

/**
 * Publishes the bot's commands to Discord as application (slash) commands.
 *
 * <p>{@code CommandInfo(prefix, name, description)} already carries everything Discord's registration
 * needs, so the command list stays a single source of truth: adding a {@code @Component} command
 * makes it appear on both platforms with no extra registration.
 *
 * <p>A {@link DynamicCommand} becomes a command with one required string option, which means Discord
 * enforces the "argument present" guard in its own UI before the bot is ever called.
 */
// Only needed when the Discord adapter is configured.
@ConditionalOnProperty(name = "discord.token")
@Service
public class DiscordSlashCommandRegistrar {

    public static final String ARGS_OPTION = "args";

    private static final Logger log = LoggerFactory.getLogger(DiscordSlashCommandRegistrar.class);
    /** Discord rejects descriptions longer than this. */
    private static final int MAX_DESCRIPTION = 100;

    private final CommandFactory commandFactory;

    public DiscordSlashCommandRegistrar(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
    }

    /**
     * Publish the command list.
     *
     * <p>With {@code discord.guild-id} set, commands are registered to that one guild and appear
     * <b>immediately</b>. Global registration (no guild id) is correct for a real deployment but
     * Discord can take up to an hour to propagate it — during which typing {@code /lobby} just sends
     * a plain message and the bot looks broken. Point it at your test server while developing.
     */
    public void register(JDA jda, String guildId) {
        List<SlashCommandData> commands = commandFactory.getAll().stream()
                .map(this::toSlashCommand)
                .toList();

        if (guildId != null && !guildId.isBlank()) {
            Guild guild = jda.getGuildById(guildId.trim());
            if (guild == null) {
                log.error("discord.guild-id={} is not a guild this bot is in — no commands registered. "
                        + "Check the id, or drop the property to register globally.", guildId);
                return;
            }
            guild.updateCommands().addCommands(commands).queue(
                    ok -> log.info("Registered {} slash commands in guild {}", commands.size(), guild.getName()),
                    error -> log.error("Failed to register slash commands in guild {}", guild.getName(), error));
            return;
        }

        jda.updateCommands().addCommands(commands).queue(
                ok -> log.info("Registered {} Discord slash commands globally "
                        + "(can take up to an hour to appear; set discord.guild-id for instant "
                        + "registration on one server)", commands.size()),
                error -> log.error("Failed to register Discord slash commands", error));
    }

    private SlashCommandData toSlashCommand(Command command) {
        // Discord command names are lowercase and have no leading slash.
        String name = command.getInfo().prefix().replaceFirst("^/", "").toLowerCase();
        SlashCommandData data = Commands.slash(name, describe(command));
        if (command instanceof DynamicCommand) {
            data.addOption(OptionType.STRING, ARGS_OPTION, argumentHint(command), true);
        }
        return data;
    }

    private String describe(Command command) {
        String description = command.getInfo().description();
        if (description == null || description.isBlank()) description = command.getInfo().name();
        return description.length() <= MAX_DESCRIPTION ? description : description.substring(0, MAX_DESCRIPTION - 1) + "…";
    }

    /** "/ban [shortName]" → "shortName", so the Discord option prompt reads like the Telegram usage. */
    private String argumentHint(Command command) {
        String usage = command.getInfo().name();
        int open = usage.indexOf('[');
        int close = usage.indexOf(']');
        String hint = open >= 0 && close > open ? usage.substring(open + 1, close) : "argument";
        return hint.length() <= MAX_DESCRIPTION ? hint : hint.substring(0, MAX_DESCRIPTION);
    }
}
