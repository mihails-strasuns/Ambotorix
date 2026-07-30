package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.PlayerCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class BanButtonsCommand implements PlayerCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/banButtons",
            "/banButtons",
            "Receive ban buttons in DM for all available leaders");

    @Override
    public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendBanButtons(ctx);
    }
}
