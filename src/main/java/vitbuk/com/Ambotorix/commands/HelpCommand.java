package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class HelpCommand implements GeneralCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/help",
            "/help",
            "Show list of commands");
    @Override
    public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendHelp(ctx);
    }
}
