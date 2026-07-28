package vitbuk.com.Ambotorix;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.chat.ChatEvent;
import vitbuk.com.Ambotorix.commands.structure.AdminCommand;
import vitbuk.com.Ambotorix.commands.structure.Command;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.commands.structure.PlayerCommand;
import vitbuk.com.Ambotorix.config.BotConfig;
import vitbuk.com.Ambotorix.services.AmbotorixService;

/**
 * The single entry point for anything a user does, on any platform. Adapters normalize their wire
 * traffic into a {@link ChatEvent} and hand it here.
 *
 * <p>Authorization stays declarative: a command's marker interfaces say what it needs
 * ({@link HostCommand}, {@link PlayerCommand}, {@link AdminCommand}, {@link DynamicCommand}) and this
 * class rejects early. That design was already platform-free, so it moved over unchanged.
 */
@Component
public class BotDispatcher {

    private final AmbotorixService ambotorixService;
    private final CommandFactory commandFactory;
    private final BotConfig botConfig;

    public BotDispatcher(AmbotorixService ambotorixService, CommandFactory commandFactory, BotConfig botConfig) {
        this.ambotorixService = ambotorixService;
        this.commandFactory = commandFactory;
        this.botConfig = botConfig;
    }

    public void handle(ChatEvent event) {
        switch (event) {
            case ChatEvent.Interaction interaction -> ambotorixService.handleInteraction(interaction);
            case ChatEvent.FreeText freeText -> {
                // Free text in a private chat is a draft submission (e.g. Herson ranked picks).
                // Group chatter without a command is ignored, as before.
                if (freeText.direct()) ambotorixService.handleDirectMessage(freeText);
            }
            case ChatEvent.SlashCommand slash -> handleCommand(slash);
        }
    }

    private void handleCommand(ChatEvent.SlashCommand event) {
        CommandContext ctx = new CommandContext(event);
        Command command = commandFactory.getCommand(event.prefix());
        if (command == null) {
            ambotorixService.sendUnknown(ctx);
            return;
        }

        if (command instanceof AdminCommand && !isAdmin(event)) {
            ambotorixService.sendMessage(event, "This command is for bot admin only.");
            return;
        }

        if (command instanceof HostCommand) {
            if (!ambotorixService.hasLobby(ctx)) {
                ambotorixService.sendNoLobby(ctx);
                return;
            }
            if (!ambotorixService.isHost(ctx)) {
                ambotorixService.sendNotAHost(ctx);
                return;
            }
        }

        if (command instanceof PlayerCommand) {
            if (!ambotorixService.hasLobby(ctx)) {
                ambotorixService.sendNoLobby(ctx);
                return;
            }
            if (!ambotorixService.isRegistered(ctx)) {
                ambotorixService.sendNotAPlayer(ctx);
                return;
            }
        }

        if (command instanceof DynamicCommand && !event.hasArgs()) {
            ambotorixService.sendMessage(event, "Usage: " + command.getInfo().name());
            return;
        }

        command.execute(ctx, ambotorixService);
    }

    /** Admin rights are per platform — the configured id only means anything on its own platform. */
    private boolean isAdmin(ChatEvent event) {
        String adminId = botConfig.adminIdFor(event.from().platform());
        return adminId != null && adminId.equals(event.from().id());
    }
}
