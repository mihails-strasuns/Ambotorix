package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.AdminCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class AdminLobbiesCommand implements AdminCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/adminLobbies", "/adminLobbies", "List all active lobbies (Admin only)");

    @Override public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendAdminLobbies(ctx);
    }
}
