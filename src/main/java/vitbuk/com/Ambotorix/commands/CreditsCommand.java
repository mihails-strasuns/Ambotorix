package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class CreditsCommand implements GeneralCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/credits",
            "/credits",
            "Show who created this bot");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendCredits(ctx);
    }
}
